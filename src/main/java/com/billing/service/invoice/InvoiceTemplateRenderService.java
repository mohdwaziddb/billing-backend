package com.billing.service.invoice;

import com.billing.dto.invoice.InvoiceTemplatePreviewRequest;
import com.billing.dto.invoice.InvoiceRenderResponse;
import com.billing.dto.invoice.InvoiceTemplateMetadataResponse;
import com.billing.entity.Company;
import com.billing.entity.CompanyInvoiceSetting;
import com.billing.entity.Customer;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.service.AccessControlService;
import com.billing.service.InvoiceService;
import com.billing.util.CompanyFileJson;
import com.billing.util.DataTypeUtility;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.billing.util.OutboundUrlGuard;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceTemplateRenderService {

    private static final DateTimeFormatter INDIAN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter INDIAN_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final long MAX_REMOTE_IMAGE_BYTES = 5L * 1024L * 1024L;

    private final AccessControlService accessControlService;
    private final InvoiceService invoiceService;
    private final InvoiceTemplateRegistryService invoiceTemplateRegistryService;
    private final CompanyInvoiceSettingsService companyInvoiceSettingsService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<InvoiceTemplateMetadataResponse> list(String email) {
        Company company = accessControlService.getCurrentCompany(email);
        String defaultTemplateId = companyInvoiceSettingsService.resolveForCompany(company).getDefaultTemplateId();
        return invoiceTemplateRegistryService.templates().stream()
                .map(definition -> InvoiceTemplateMetadataResponse.builder()
                        .templateId(definition.getTemplateId())
                        .templateName(definition.getTemplateName())
                        .version(definition.getVersion())
                        .description(definition.getDescription())
                        .author(definition.getAuthor())
                        .previewImage(definition.getPreviewImage())
                        .supportsWatermark(definition.isSupportsWatermark())
                        .supportsQr(definition.isSupportsQr())
                        .supportsSignature(definition.isSupportsSignature())
                        .supportsBankDetails(definition.isSupportsBankDetails())
                        .supportsTerms(definition.isSupportsTerms())
                        .supportsNotes(definition.isSupportsNotes())
                        .supportsGst(definition.isSupportsGst())
                        .supportsMultiPage(definition.isSupportsMultiPage())
                        .supportedPaperSizes(definition.getSupportedPaperSizes())
                        .defaultColors(definition.getDefaultColors())
                        .defaultTemplate(definition.getTemplateId().equals(defaultTemplateId))
                        .sortOrder(definition.getSortOrder())
                        .category(definition.getCategory())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceRenderResponse renderInvoice(Map<String, Object> param, String email, Long invoiceId) {
        String requestedTemplateId = DataTypeUtility.stringValue(param.get("templateId"));
        if (requestedTemplateId.length() == 0) {
            requestedTemplateId = DataTypeUtility.stringValue(param.get("templateIdForRender"));
        }
        if (requestedTemplateId.length() == 0) {
            requestedTemplateId = null;
        }
        return renderInvoice(email, invoiceId, requestedTemplateId);
    }

    @Transactional(readOnly = true)
    public InvoiceRenderResponse renderInvoice(String email, Long invoiceId, String requestedTemplateId) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = invoiceService.getInvoiceOrThrow(company, invoiceId);
        CompanyInvoiceSetting settings = companyInvoiceSettingsService.resolveForCompany(company);
        // Saved default ka folder hat gaya ho to bhi print/PDF chale (fallback template);
        // explicitly maanga gaya galat id abhi bhi 400 deta hai.
        InvoiceTemplateDefinition definition = requestedTemplateId == null || requestedTemplateId.isBlank()
                ? invoiceTemplateRegistryService.getOrDefault(settings.getDefaultTemplateId())
                : invoiceTemplateRegistryService.getOrThrow(requestedTemplateId.trim());
        String html = render(definition, buildInvoiceModel(company, invoice, settings, definition));
        return InvoiceRenderResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNo(invoice.getInvoiceNo())
                .templateId(definition.getTemplateId())
                .templateName(definition.getTemplateName())
                .html(html)
                .build();
    }

    @Transactional(readOnly = true)
    public InvoiceRenderResponse previewTemplate(Map<String, Object> param, String email, String templateId) {
        Long invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoiceId"));
        if (invoiceId == null) {
            invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoice_id"));
        }
        return previewTemplate(email, templateId, invoiceId);
    }

    @Transactional(readOnly = true)
    public InvoiceRenderResponse previewWithOverrides(Map<String, Object> param, String email, String templateId) {
        InvoiceTemplatePreviewRequest invoiceTemplatePreviewRequest = mapToPreviewRequest(param);
        return previewTemplate(email, templateId, invoiceTemplatePreviewRequest);
    }

    @Transactional(readOnly = true)
    public InvoiceRenderResponse previewTemplate(String email, String templateId, Long invoiceId) {
        Company company = accessControlService.getCurrentCompany(email);
        CompanyInvoiceSetting settings = companyInvoiceSettingsService.resolveForCompany(company);
        InvoiceTemplateDefinition definition = invoiceTemplateRegistryService.getOrThrow(templateId);
        Map<String, Object> model = invoiceId == null
                ? buildSampleModel(company, settings, definition)
                : buildInvoiceModel(company, invoiceService.getInvoiceOrThrow(company, invoiceId), settings, definition);
        String html = render(definition, model);
        return InvoiceRenderResponse.builder()
                .invoiceId(invoiceId)
                .invoiceNo(invoiceId == null ? "SAMPLE-PREVIEW" : "INVOICE")
                .templateId(definition.getTemplateId())
                .templateName(definition.getTemplateName())
                .html(html)
                .build();
    }

    @Transactional(readOnly = true)
    public InvoiceRenderResponse previewTemplate(String email, String templateId, InvoiceTemplatePreviewRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        CompanyInvoiceSetting settings = mergePreviewSettings(companyInvoiceSettingsService.resolveForCompany(company), request);
        InvoiceTemplateDefinition definition = invoiceTemplateRegistryService.getOrThrow(templateId);
        Long invoiceId = request == null ? null : request.getInvoiceId();
        Map<String, Object> model = invoiceId == null
                ? buildSampleModel(company, settings, definition)
                : buildInvoiceModel(company, invoiceService.getInvoiceOrThrow(company, invoiceId), settings, definition);
        String html = render(definition, model);
        return InvoiceRenderResponse.builder()
                .invoiceId(invoiceId)
                .invoiceNo(invoiceId == null ? "SAMPLE-PREVIEW" : "INVOICE")
                .templateId(definition.getTemplateId())
                .templateName(definition.getTemplateName())
                .html(html)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Map<String, Object> param, String email, Long invoiceId) {
        String requestedTemplateId = DataTypeUtility.stringValue(param.get("templateId"));
        if (requestedTemplateId.length() == 0) {
            requestedTemplateId = null;
        }
        return pdf(email, invoiceId, requestedTemplateId);
    }

    @Transactional(readOnly = true)
    public byte[] pdf(String email, Long invoiceId, String requestedTemplateId) {
        InvoiceRenderResponse response = renderInvoice(email, invoiceId, requestedTemplateId);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(response.getHtml(), frontendUrl);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate invoice PDF", ex);
        }
    }

    private String render(InvoiceTemplateDefinition definition, Map<String, Object> model) {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);

        String htmlTemplate = definition.getHtmlTemplate()
                .replace("</head>", "<style>\n" + definition.getCssTemplate() + "\n</style>\n</head>");

        Context context = new Context(Locale.ENGLISH);
        context.setVariables(model);
        return engine.process(htmlTemplate, context);
    }

    private Map<String, Object> buildInvoiceModel(Company company, Invoice invoice, CompanyInvoiceSetting settings, InvoiceTemplateDefinition definition) {
        Map<String, Object> root = baseModel(company, settings, definition);
        // Print-time shown on the PDF: IST wall-time, not server UTC.
        LocalDateTime renderedAt = com.billing.core.AppDateTime.istNow();
        List<Map<String, Object>> itemRows = new ArrayList<>();
        List<TaxLine> taxLines = new ArrayList<>();
        for (InvoiceItem item : invoice.getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productName", item.getProduct().getName());
            row.put("sku", item.getProduct().getSku());
            row.put("hsnCode", coalesce(item.getHsnCode(), "--"));
            row.put("qty", item.getQty());
            row.put("price", money(item.getPrice()));
            row.put("discountAmount", money(item.getDiscountAmount()));
            row.put("taxRate", item.getTaxRate() == null ? "0.00" : item.getTaxRate().setScale(2, RoundingMode.HALF_UP).toPlainString());
            row.put("taxName", coalesce(item.getTaxName(), item.getTaxRate() + "%"));
            row.put("taxableAmount", money(item.getTaxableAmount()));
            row.put("cgstAmount", money(item.getCgstAmount()));
            row.put("sgstAmount", money(item.getSgstAmount()));
            row.put("igstAmount", money(item.getIgstAmount()));
            row.put("lineTotal", money(item.getLineTotal()));
            row.put("gstBreakup", item.getIgstAmount() != null && item.getIgstAmount().compareTo(BigDecimal.ZERO) > 0
                    ? "IGST " + item.getIgstRate().setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
                    : "CGST " + scale(item.getCgstRate()).toPlainString() + "% + SGST " + scale(item.getSgstRate()).toPlainString() + "%");
            row.put("cgstRate", rate(item.getCgstRate()));
            row.put("sgstRate", rate(item.getSgstRate()));
            row.put("igstRate", rate(item.getIgstRate()));
            row.put("taxRateLabel", rate(item.getTaxRate()));
            row.put("taxAmount", money(scale(item.getCgstAmount()).add(scale(item.getSgstAmount())).add(scale(item.getIgstAmount()))));
            itemRows.add(row);
            taxLines.add(new TaxLine(coalesce(item.getHsnCode(), "--"), scale(item.getTaxRate()), scale(item.getTaxableAmount()),
                    scale(item.getCgstRate()), scale(item.getCgstAmount()), scale(item.getSgstRate()), scale(item.getSgstAmount()),
                    scale(item.getIgstRate()), scale(item.getIgstAmount()), item.getQty() == null ? 0 : item.getQty()));
        }
        Customer customer = invoice.getCustomer();
        // Invoice ke waqt ka snapshot (place of supply / GSTIN) pehle; purane invoices par customer ki current value.
        String placeOfSupply = coalesce(invoice.getPlaceOfSupplyState(), customer.getState(), "--");
        String customerGstin = coalesce(invoice.getCustomerGstin(), customer.getGstin(), customer.getGstNo());

        root.put("invoice", invoiceData(
                invoice.getInvoiceNo(),
                invoice.getInvoiceDate(),
                invoice.getInvoiceDate(),
                renderedAt,
                placeOfSupply,
                customer.getName(),
                customer.getMobile(),
                customer.getEmail(),
                customer.getAddress(),
                customer.getState(),
                customer.getCountry(),
                customerGstin,
                itemRows,
                invoice.getPaymentStatus().name(),
                money(invoice.getSubtotal()),
                money(invoice.getDiscountAmount()),
                money(invoice.getTaxableAmount()),
                money(invoice.getCgstTotal()),
                money(invoice.getSgstTotal()),
                money(invoice.getIgstTotal()),
                money(invoice.getRoundOff()),
                money(invoice.getGrandTotal()),
                money(invoice.getPaidAmount()),
                money(invoice.getBalanceAmount()),
                amountInWords(invoice.getGrandTotal()),
                watermarkText(invoice, settings)
        ));
        String customerStateCode = customer.getStateMaster() == null ? null : customer.getStateMaster().getStateCode();
        enrichInvoice(root, company, settings, definition, invoice.getInvoiceNo(), invoice.getInvoiceDate(), taxLines,
                scale(invoice.getDiscountAmount()), scale(invoice.getRoundOff()), scale(invoice.getGrandTotal()),
                scale(invoice.getPaidAmount()), scale(invoice.getBalanceAmount()),
                joinNonBlank(customer.getAddress(), customer.getCity(), customer.getState(), customer.getPincode()),
                stateCodeFor(placeOfSupply, customerGstin, customer.getState(), customerStateCode, company));
        root.put("shareUrl", frontendUrl + "/invoices/" + invoice.getId());
        return root;
    }

    private Map<String, Object> buildSampleModel(Company company, CompanyInvoiceSetting settings, InvoiceTemplateDefinition definition) {
        Map<String, Object> root = baseModel(company, settings, definition);
        LocalDateTime renderedAt = com.billing.core.AppDateTime.istNow();
        LocalDate sampleDate = com.billing.core.AppDateTime.istToday();
        String sampleState = coalesce(company.getState(), "Maharashtra");
        String sampleStateCode = coalesce(companyStateCode(company), "27");
        String sampleCustomerGstin = sampleStateCode + "AAPFB4321K1Z7";
        // Realistic intra-state B2B sample. Amounts code se calculate hote hain (CGST + SGST),
        // taaki line totals, HSN summary, grand total aur amount-in-words hamesha match karein.
        List<TaxLine> taxLines = new ArrayList<>();
        List<Map<String, Object>> itemRows = new ArrayList<>();
        BigDecimal[] grossAndDiscount = {BigDecimal.ZERO, BigDecimal.ZERO};
        addSampleLine(itemRows, taxLines, grossAndDiscount, "HP LaserJet Pro M126nw Multifunction Printer", "PRN-M126NW", "8443", 2, "14500.00", "500.00", "18");
        addSampleLine(itemRows, taxLines, grossAndDiscount, "Logitech MK270 Wireless Keyboard & Mouse Combo", "LOG-MK270", "8471", 10, "1395.00", "0.00", "18");
        addSampleLine(itemRows, taxLines, grossAndDiscount, "JK Copier A4 Paper 75 GSM (500 Sheets)", "JK-A4-75", "4802", 25, "245.00", "0.00", "12");
        addSampleLine(itemRows, taxLines, grossAndDiscount, "Ergonomic Mesh Office Chair with Headrest", "CHR-MESH-HR", "9401", 4, "6800.00", "1200.00", "18");
        addSampleLine(itemRows, taxLines, grossAndDiscount, "Camlin Whiteboard Marker - Pack of 10", "CAM-WBM-10", "9608", 12, "180.00", "0.00", "18");
        addSampleLine(itemRows, taxLines, grossAndDiscount, "Annual IT Support & Maintenance Contract (12 months)", "AMC-IT-12M", "998713", 1, "12000.00", "0.00", "18");
        BigDecimal taxableTotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        for (TaxLine line : taxLines) {
            taxableTotal = taxableTotal.add(line.taxableAmount());
            cgstTotal = cgstTotal.add(line.cgstAmount());
            sgstTotal = sgstTotal.add(line.sgstAmount());
        }
        BigDecimal unroundedTotal = taxableTotal.add(cgstTotal).add(sgstTotal);
        BigDecimal grandTotal = unroundedTotal.setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        BigDecimal roundOff = grandTotal.subtract(unroundedTotal);
        BigDecimal paidAmount = new BigDecimal("50000.00");
        BigDecimal balanceAmount = grandTotal.subtract(paidAmount);
        int financialYearStart = sampleDate.getMonthValue() >= 4 ? sampleDate.getYear() : sampleDate.getYear() - 1;
        root.put("invoice", invoiceData(
                "INV/" + financialYearStart + "-" + String.format("%02d", (financialYearStart + 1) % 100) + "/0042",
                sampleDate,
                sampleDate.plusDays(15),
                renderedAt,
                sampleState,
                "Shree Balaji Enterprises",
                "9876543210",
                "accounts@shreebalaji.in",
                "Shop No. 12, Laxmi Market, MG Road",
                sampleState,
                "India",
                sampleCustomerGstin,
                itemRows,
                "PARTIAL",
                money(grossAndDiscount[0]),
                money(grossAndDiscount[1]),
                money(taxableTotal),
                money(cgstTotal),
                money(sgstTotal),
                money(BigDecimal.ZERO),
                money(roundOff),
                money(grandTotal),
                money(paidAmount),
                money(balanceAmount),
                amountInWords(grandTotal),
                previewWatermarkText(settings)
        ));
        enrichInvoice(root, company, settings, definition, "SAMPLE", sampleDate, taxLines, grossAndDiscount[1], roundOff, grandTotal,
                paidAmount, balanceAmount, joinNonBlank("Shop No. 12, Laxmi Market, MG Road", "Pune", sampleState, "411001"), sampleStateCode);
        root.put("shareUrl", frontendUrl + "/invoices");
        return root;
    }

    /** Sample line (intra-state CGST + SGST). grossAndDiscount[0] += qty x price, [1] += discount. */
    private void addSampleLine(List<Map<String, Object>> itemRows, List<TaxLine> taxLines, BigDecimal[] grossAndDiscount,
                               String productName, String sku, String hsnCode, int qty, String price, String discount, String taxRate) {
        BigDecimal unitPrice = new BigDecimal(price);
        BigDecimal discountAmount = new BigDecimal(discount);
        BigDecimal rateValue = new BigDecimal(taxRate);
        BigDecimal gross = unitPrice.multiply(BigDecimal.valueOf(qty));
        BigDecimal taxableAmount = scale(gross.subtract(discountAmount));
        BigDecimal halfRate = rateValue.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        BigDecimal halfTax = taxableAmount.multiply(halfRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal lineTotal = taxableAmount.add(halfTax).add(halfTax);
        grossAndDiscount[0] = grossAndDiscount[0].add(gross);
        grossAndDiscount[1] = grossAndDiscount[1].add(discountAmount);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productName", productName);
        row.put("sku", sku);
        row.put("hsnCode", hsnCode);
        row.put("qty", qty);
        row.put("price", money(unitPrice));
        row.put("discountAmount", money(discountAmount));
        row.put("taxRate", scale(rateValue).toPlainString());
        row.put("taxName", "GST " + rate(rateValue) + "%");
        row.put("taxableAmount", money(taxableAmount));
        row.put("cgstAmount", money(halfTax));
        row.put("sgstAmount", money(halfTax));
        row.put("igstAmount", money(BigDecimal.ZERO));
        row.put("lineTotal", money(lineTotal));
        row.put("gstBreakup", "CGST " + scale(halfRate).toPlainString() + "% + SGST " + scale(halfRate).toPlainString() + "%");
        row.put("cgstRate", rate(halfRate));
        row.put("sgstRate", rate(halfRate));
        row.put("igstRate", "0");
        row.put("taxRateLabel", rate(rateValue));
        row.put("taxAmount", money(halfTax.add(halfTax)));
        itemRows.add(row);
        taxLines.add(new TaxLine(hsnCode, scale(rateValue), taxableAmount, halfRate, halfTax, halfRate, halfTax, BigDecimal.ZERO, BigDecimal.ZERO, qty));
    }

    private CompanyInvoiceSetting mergePreviewSettings(CompanyInvoiceSetting base, InvoiceTemplatePreviewRequest request) {
        if (request == null) {
            return base;
        }
        CompanyInvoiceSetting previewSettings = CompanyInvoiceSetting.builder()
                .id(base.getId())
                .company(base.getCompany())
                .defaultTemplateId(base.getDefaultTemplateId())
                .showWatermark(base.isShowWatermark())
                .watermarkText(base.getWatermarkText())
                .showSignature(base.isShowSignature())
                .signatureLabel(base.getSignatureLabel())
                .signatureHeading(base.getSignatureHeading())
                .showQr(base.isShowQr())
                .showBankDetails(base.isShowBankDetails())
                .showTerms(base.isShowTerms())
                .showNotes(base.isShowNotes())
                .noteText(base.getNoteText())
                .termsText(base.getTermsText())
                .footerCredit(base.getFooterCredit())
                .build();
        if (request.getShowWatermark() != null) {
            previewSettings.setShowWatermark(request.getShowWatermark());
        }
        if (request.getWatermarkText() != null) {
            previewSettings.setWatermarkText(blankToNull(request.getWatermarkText()));
        }
        if (request.getShowSignature() != null) {
            previewSettings.setShowSignature(request.getShowSignature());
        }
        if (request.getSignatureLabel() != null) {
            previewSettings.setSignatureLabel(request.getSignatureLabel().trim());
        }
        if (request.getSignatureHeading() != null) {
            previewSettings.setSignatureHeading(request.getSignatureHeading().trim());
        }
        if (request.getShowQr() != null) {
            previewSettings.setShowQr(request.getShowQr());
        }
        if (request.getShowBankDetails() != null) {
            previewSettings.setShowBankDetails(request.getShowBankDetails());
        }
        if (request.getShowTerms() != null) {
            previewSettings.setShowTerms(request.getShowTerms());
        }
        if (request.getShowNotes() != null) {
            previewSettings.setShowNotes(request.getShowNotes());
        }
        if (request.getNoteText() != null) {
            previewSettings.setNoteText(blankToNull(request.getNoteText()));
        }
        if (request.getTermsText() != null) {
            previewSettings.setTermsText(blankToNull(request.getTermsText()));
        }
        if (request.getFooterCredit() != null) {
            previewSettings.setFooterCredit(blankToNull(request.getFooterCredit()));
        }
        return previewSettings;
    }

    private Map<String, Object> baseModel(Company company, CompanyInvoiceSetting settings, InvoiceTemplateDefinition definition) {
        Map<String, Object> model = new LinkedHashMap<>();
        Map<String, Object> companyMap = new LinkedHashMap<>();
        companyMap.put("name", company.getName());
        companyMap.put("legalName", coalesce(company.getLegalName(), company.getName()));
        companyMap.put("gstin", coalesce(company.getGstin(), company.getTaxId(), "--"));
        companyMap.put("address", joinNonBlank(company.getAddressLine1(), company.getAddressLine2(), company.getCity(), company.getState(), company.getCountry(), company.getPincode()));
        companyMap.put("phone", coalesce(company.getPhone(), "--"));
        companyMap.put("email", coalesce(company.getEmail(), "--"));
        companyMap.put("website", coalesce(company.getWebsiteUrl(), "--"));
        companyMap.put("logoDataUrl", fileUrlToDataUrl(company.getLogoUrl()));
        companyMap.put("signatureDataUrl", settings.isShowSignature() ? fileUrlToDataUrl(company.getSignatureUrl()) : null);
        companyMap.put("bankName", coalesce(company.getBankName(), "--"));
        companyMap.put("bankAccountName", coalesce(company.getBankAccountName(), "--"));
        companyMap.put("bankAccountNumber", coalesce(company.getBankAccountNumber(), "--"));
        companyMap.put("bankIfscCode", coalesce(company.getBankIfscCode(), "--"));
        companyMap.put("bankBranch", coalesce(company.getBankBranch(), "--"));
        companyMap.put("upiId", coalesce(company.getUpiId(), "--"));
        companyMap.put("invoiceNotes", defaultIfNull(settings.getNoteText(), "Thank you for choosing Bizio."));
        companyMap.put("invoiceTerms", defaultIfNull(settings.getTermsText(), "Goods once sold will not be taken back."));
        companyMap.put("qrDataUrl", settings.isShowQr() && definition.isSupportsQr() && hasText(company.getUpiId()) ? qrDataUrl(upiPayload(company, null, null)) : null);
        companyMap.put("qrAmount", null);
        String companyAddress = DataTypeUtility.stringValue(companyMap.get("address"));
        companyMap.put("stateName", coalesce(company.getState(), company.getStateMaster() == null ? null : company.getStateMaster().getStateName()));
        companyMap.put("stateCode", companyStateCode(company));
        companyMap.put("pan", coalesce(company.getPanNumber()));
        companyMap.put("cin", coalesce(company.getCinNumber()));
        companyMap.put("hasGstin", hasText(company.getGstin()) || hasText(company.getTaxId()));
        companyMap.put("hasPan", hasText(company.getPanNumber()));
        companyMap.put("hasCin", hasText(company.getCinNumber()));
        companyMap.put("hasPhone", hasText(company.getPhone()));
        companyMap.put("hasEmail", hasText(company.getEmail()));
        companyMap.put("hasWebsite", hasText(company.getWebsiteUrl()));
        companyMap.put("hasAddress", hasText(companyAddress));
        companyMap.put("hasBankDetails", hasText(company.getBankAccountNumber()));
        companyMap.put("hasUpi", hasText(company.getUpiId()));
        companyMap.put("compositionScheme", company.isCompositionScheme());

        model.put("company", companyMap);
        model.put("settings", Map.of(
                "showWatermark", settings.isShowWatermark() && definition.isSupportsWatermark(),
                "showSignature", settings.isShowSignature() && definition.isSupportsSignature(),
                "showQr", settings.isShowQr() && definition.isSupportsQr(),
                "showBankDetails", settings.isShowBankDetails() && definition.isSupportsBankDetails(),
                "showTerms", settings.isShowTerms() && definition.isSupportsTerms(),
                "showNotes", settings.isShowNotes() && definition.isSupportsNotes(),
                "signatureLabel", defaultIfNull(settings.getSignatureLabel(), "Authorized Signature"),
                "signatureHeading", defaultIfNull(settings.getSignatureHeading(), "For Company"),
                "footerCredit", defaultIfNull(settings.getFooterCredit(), "Generated by Bizio Billing SaaS")
        ));
        model.put("template", Map.of(
                "id", definition.getTemplateId(),
                "name", definition.getTemplateName(),
                "description", definition.getDescription(),
                "version", definition.getVersion(),
                "colors", definition.getDefaultColors()
        ));
        model.put("generatedAt", INDIAN_DATE_TIME_FORMAT.format(com.billing.core.AppDateTime.istNow()));
        return model;
    }

    private Map<String, Object> invoiceData(String invoiceNo,
                                            LocalDate invoiceDate,
                                            LocalDate dueDate,
                                            LocalDateTime renderedAt,
                                            String placeOfSupply,
                                            String customerName,
                                            String customerPhone,
                                            String customerEmail,
                                            String customerAddress,
                                            String customerState,
                                            String customerCountry,
                                            String customerGstin,
                                            List<Map<String, Object>> items,
                                            String paymentStatus,
                                            String subtotal,
                                            String discount,
                                            String taxableAmount,
                                            String cgst,
                                            String sgst,
                                            String igst,
                                            String roundOff,
                                            String grandTotal,
                                            String paidAmount,
                                            String balanceAmount,
                                            String amountInWords,
                                            String watermarkText) {
        Map<String, Object> invoiceMap = new LinkedHashMap<>();
        invoiceMap.put("invoiceNo", invoiceNo);
        invoiceMap.put("invoiceDate", invoiceDate.toString());
        invoiceMap.put("invoiceDateLabel", formatDateTime(invoiceDate, renderedAt));
        invoiceMap.put("dueDate", dueDate.toString());
        invoiceMap.put("dueDateLabel", formatDate(dueDate));
        invoiceMap.put("placeOfSupply", placeOfSupply);
        invoiceMap.put("customerName", customerName);
        invoiceMap.put("customerPhone", customerPhone);
        invoiceMap.put("customerEmail", customerEmail);
        invoiceMap.put("customerAddress", customerAddress);
        invoiceMap.put("customerState", customerState);
        invoiceMap.put("customerCountry", customerCountry);
        invoiceMap.put("customerGstin", coalesce(customerGstin, "--"));
        invoiceMap.put("paymentStatus", paymentStatus);
        invoiceMap.put("items", items);
        invoiceMap.put("subtotal", subtotal);
        invoiceMap.put("discount", discount);
        invoiceMap.put("taxableAmount", taxableAmount);
        invoiceMap.put("cgst", cgst);
        invoiceMap.put("sgst", sgst);
        invoiceMap.put("igst", igst);
        invoiceMap.put("roundOff", roundOff);
        invoiceMap.put("grandTotal", grandTotal);
        invoiceMap.put("paidAmount", paidAmount);
        invoiceMap.put("balanceAmount", balanceAmount);
        invoiceMap.put("amountInWords", amountInWords);
        invoiceMap.put("watermarkText", watermarkText);
        invoiceMap.put("renderedAt", INDIAN_DATE_TIME_FORMAT.format(renderedAt));
        return invoiceMap;
    }

    /** Ek invoice line ka GST hissa — HSN summary aur totals isi se bante hain. */
    private record TaxLine(String hsnCode, BigDecimal taxRate, BigDecimal taxableAmount,
                           BigDecimal cgstRate, BigDecimal cgstAmount, BigDecimal sgstRate, BigDecimal sgstAmount,
                           BigDecimal igstRate, BigDecimal igstAmount, int qty) {
        BigDecimal totalTax() {
            return cgstAmount.add(sgstAmount).add(igstAmount);
        }
    }

    /**
     * Templates ke liye extra (sab optional) keys: HSN summary, inter-state flag, "has*" flags
     * taaki missing GSTIN/bank/phone par "--" chhapne ki jagah poora row chhupa sakein,
     * aur invoice-specific UPI QR (amount + invoice no ke saath).
     */
    @SuppressWarnings("unchecked")
    private void enrichInvoice(Map<String, Object> root, Company company, CompanyInvoiceSetting settings, InvoiceTemplateDefinition definition,
                               String invoiceNo, LocalDate invoiceDate, List<TaxLine> taxLines,
                               BigDecimal discountAmount, BigDecimal roundOff, BigDecimal grandTotal, BigDecimal paidAmount, BigDecimal balanceAmount,
                               String customerFullAddress, String placeOfSupplyCode) {
        Map<String, Object> invoiceMap = (Map<String, Object>) root.get("invoice");
        Map<String, Object> companyMap = (Map<String, Object>) root.get("company");
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;
        int totalQty = 0;
        Map<String, Map<String, Object>> hsnGroups = new LinkedHashMap<>();
        Map<String, BigDecimal[]> hsnSums = new LinkedHashMap<>();
        for (TaxLine line : taxLines) {
            cgstTotal = cgstTotal.add(line.cgstAmount());
            sgstTotal = sgstTotal.add(line.sgstAmount());
            igstTotal = igstTotal.add(line.igstAmount());
            totalQty += line.qty();
            String groupKey = line.hsnCode() + "|" + line.taxRate().toPlainString();
            BigDecimal[] sums = hsnSums.computeIfAbsent(groupKey, key -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            sums[0] = sums[0].add(line.taxableAmount());
            sums[1] = sums[1].add(line.cgstAmount());
            sums[2] = sums[2].add(line.sgstAmount());
            sums[3] = sums[3].add(line.igstAmount());
            Map<String, Object> group = hsnGroups.computeIfAbsent(groupKey, key -> new LinkedHashMap<>());
            group.put("hsnCode", line.hsnCode());
            group.put("taxRate", rate(line.taxRate()));
            group.put("cgstRate", rate(line.cgstRate()));
            group.put("sgstRate", rate(line.sgstRate()));
            group.put("igstRate", rate(line.igstRate()));
        }
        List<Map<String, Object>> hsnSummary = new ArrayList<>();
        hsnGroups.forEach((groupKey, group) -> {
            BigDecimal[] sums = hsnSums.get(groupKey);
            group.put("taxableAmount", money(sums[0]));
            group.put("cgstAmount", money(sums[1]));
            group.put("sgstAmount", money(sums[2]));
            group.put("igstAmount", money(sums[3]));
            group.put("totalTax", money(sums[1].add(sums[2]).add(sums[3])));
            hsnSummary.add(group);
        });
        BigDecimal totalTax = cgstTotal.add(sgstTotal).add(igstTotal);
        boolean interState = igstTotal.compareTo(BigDecimal.ZERO) > 0;
        String placeOfSupply = DataTypeUtility.stringValue(invoiceMap.get("placeOfSupply"));
        String customerGstin = DataTypeUtility.stringValue(invoiceMap.get("customerGstin"));

        invoiceMap.put("invoiceDateShort", formatDate(invoiceDate));
        invoiceMap.put("interState", interState);
        invoiceMap.put("totalTax", money(totalTax));
        invoiceMap.put("totalTaxInWords", amountInWords(totalTax));
        invoiceMap.put("totalQty", totalQty);
        invoiceMap.put("itemCount", taxLines.size());
        invoiceMap.put("hsnSummary", hsnSummary);
        invoiceMap.put("hasDiscount", discountAmount.compareTo(BigDecimal.ZERO) > 0);
        invoiceMap.put("hasRoundOff", roundOff.compareTo(BigDecimal.ZERO) != 0);
        invoiceMap.put("hasPaid", paidAmount.compareTo(BigDecimal.ZERO) > 0);
        invoiceMap.put("hasBalance", balanceAmount.compareTo(BigDecimal.ZERO) > 0);
        invoiceMap.put("isPaid", balanceAmount.compareTo(BigDecimal.ZERO) <= 0);
        invoiceMap.put("hasCustomerGstin", hasText(customerGstin) && !"--".equals(customerGstin));
        invoiceMap.put("hasCustomerPhone", hasText(DataTypeUtility.stringValue(invoiceMap.get("customerPhone"))));
        invoiceMap.put("hasCustomerEmail", hasText(DataTypeUtility.stringValue(invoiceMap.get("customerEmail"))));
        invoiceMap.put("customerFullAddress", customerFullAddress);
        invoiceMap.put("hasCustomerAddress", hasText(customerFullAddress));
        invoiceMap.put("placeOfSupplyCode", placeOfSupplyCode);
        invoiceMap.put("placeOfSupplyLabel", placeOfSupplyCode == null || "--".equals(placeOfSupply) ? placeOfSupply : placeOfSupply + " (" + placeOfSupplyCode + ")");
        invoiceMap.put("reverseCharge", "No");
        // Composition dealer GST "Tax Invoice" issue nahi kar sakta — Bill of Supply banta hai.
        invoiceMap.put("documentTitle", company.isCompositionScheme() ? "Bill of Supply"
                : (hasText(company.getGstin()) || hasText(company.getTaxId()) ? "Tax Invoice" : "Invoice"));

        if (settings.isShowQr() && definition.isSupportsQr() && hasText(company.getUpiId())) {
            BigDecimal qrAmount = balanceAmount.compareTo(BigDecimal.ZERO) > 0 ? balanceAmount : null;
            companyMap.put("qrDataUrl", qrDataUrl(upiPayload(company, qrAmount, invoiceNo)));
            companyMap.put("qrAmount", qrAmount == null ? null : money(qrAmount));
        }
    }

    /** UPI deep link (NPCI format). Scan karte hi payee + amount + invoice note bhar jaata hai. */
    private String upiPayload(Company company, BigDecimal amount, String invoiceNo) {
        StringBuilder payload = new StringBuilder("upi://pay?pa=").append(urlEncode(company.getUpiId().trim()))
                .append("&pn=").append(urlEncode(coalesce(company.getBankAccountName(), company.getLegalName(), company.getName(), "Merchant")))
                .append("&cu=INR");
        if (amount != null) {
            payload.append("&am=").append(scale(amount).toPlainString());
        }
        if (hasText(invoiceNo)) {
            payload.append("&tn=").append(urlEncode("Invoice " + invoiceNo));
        }
        return payload.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String companyStateCode(Company company) {
        if (company.getStateMaster() != null && hasText(company.getStateMaster().getStateCode())) {
            return company.getStateMaster().getStateCode().trim();
        }
        return gstinStateCode(coalesce(company.getGstin(), company.getTaxId()));
    }

    /** Place of supply ka GST state code: customer GSTIN ke pehle 2 digit, warna state master. */
    private String stateCodeFor(String placeOfSupply, String customerGstin, String customerState, String customerStateCode, Company company) {
        String fromGstin = gstinStateCode(customerGstin);
        if (fromGstin != null) {
            return fromGstin;
        }
        if (hasText(customerStateCode) && placeOfSupply != null && placeOfSupply.equalsIgnoreCase(coalesce(customerState, ""))) {
            return customerStateCode.trim();
        }
        if (placeOfSupply != null && placeOfSupply.equalsIgnoreCase(coalesce(company.getState(), ""))) {
            return companyStateCode(company);
        }
        return null;
    }

    private String gstinStateCode(String gstin) {
        if (gstin == null) {
            return null;
        }
        String trimmed = gstin.trim();
        return trimmed.length() >= 2 && Character.isDigit(trimmed.charAt(0)) && Character.isDigit(trimmed.charAt(1)) ? trimmed.substring(0, 2) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 18.00 -> "18", 2.50 -> "2.5" (GST rate label). */
    private String rate(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String watermarkText(Invoice invoice, CompanyInvoiceSetting settings) {
        if (!settings.isShowWatermark()) {
            return "";
        }
        if (settings.getWatermarkText() != null && !settings.getWatermarkText().isBlank()) {
            return settings.getWatermarkText().trim();
        }
        if (invoice.isDeleted()) {
            return "CANCELLED";
        }
        if ("PAID".equalsIgnoreCase(invoice.getPaymentStatus().name())) {
            return "PAID";
        }
        return "INVOICE";
    }

    private String previewWatermarkText(CompanyInvoiceSetting settings) {
        if (!settings.isShowWatermark()) {
            return "";
        }
        if (settings.getWatermarkText() != null && !settings.getWatermarkText().isBlank()) {
            return settings.getWatermarkText().trim();
        }
        return "INVOICE";
    }

    private String fileUrlToDataUrl(String fileUrl) {
        // DB columns now hold full file JSON; legacy plain URLs/paths still work.
        String resolved = DataTypeUtility.stringValue(CompanyFileJson.extractUrl(fileUrl));
        if (resolved.length() == 0) {
            return null;
        }
        String lower = resolved.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return remoteUrlToDataUrl(resolved);
        }
        try {
            Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(resolved.replaceFirst("^/uploads/?", "")).normalize();
            if (!filePath.startsWith(baseDir) || !Files.exists(filePath)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(filePath);
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null) {
                mimeType = "image/png";
            }
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (IOException ex) {
            return null;
        }
    }

    /** Fetch an S3 (or any https) image URL for invoice/PDF embedding. Never throws. */
    private String remoteUrlToDataUrl(String url) {
        try {
            // Logo URL tenant set kar sakta hai: internal address par fetch nahi (SSRF).
            HttpRequest request = HttpRequest.newBuilder(OutboundUrlGuard.requirePublicUrl(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_REMOTE_IMAGE_BYTES) {
                return null;
            }
            String mimeType = response.headers().firstValue("content-type")
                    .map(value -> value.split(";")[0].trim().toLowerCase(Locale.ROOT))
                    .filter(value -> value.startsWith("image/"))
                    .orElse("image/png");
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception ex) {
            return null;
        }
    }

    private String qrDataUrl(String payload) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 220, 220);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception ex) {
            return null;
        }
    }

    private InvoiceTemplatePreviewRequest mapToPreviewRequest(Map<String, Object> param) {
        InvoiceTemplatePreviewRequest invoiceTemplatePreviewRequest = new InvoiceTemplatePreviewRequest();
        Long invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoiceId"));
        if (invoiceId == null) {
            invoiceId = DataTypeUtility.getForeignKeyValue(param.get("invoice_id"));
        }
        invoiceTemplatePreviewRequest.setInvoiceId(invoiceId);
        Object showWatermarkObj = param.get("showWatermark");
        if (showWatermarkObj != null) {
            String showWatermarkStr = DataTypeUtility.stringValue(showWatermarkObj);
            if (showWatermarkStr.length() > 0) {
                invoiceTemplatePreviewRequest.setShowWatermark(DataTypeUtility.booleanValue(showWatermarkObj));
            }
        }
        String watermarkText = DataTypeUtility.stringValue(param.get("watermarkText"));
        if (watermarkText.length() == 0) {
            watermarkText = null;
        }
        invoiceTemplatePreviewRequest.setWatermarkText(watermarkText);
        Object showSignatureObj = param.get("showSignature");
        if (showSignatureObj != null) {
            String showSignatureStr = DataTypeUtility.stringValue(showSignatureObj);
            if (showSignatureStr.length() > 0) {
                invoiceTemplatePreviewRequest.setShowSignature(DataTypeUtility.booleanValue(showSignatureObj));
            }
        }
        String signatureLabel = DataTypeUtility.stringValue(param.get("signatureLabel"));
        if (signatureLabel.length() == 0) {
            signatureLabel = null;
        }
        invoiceTemplatePreviewRequest.setSignatureLabel(signatureLabel);
        String signatureHeading = DataTypeUtility.stringValue(param.get("signatureHeading"));
        if (signatureHeading.length() == 0) {
            signatureHeading = null;
        }
        invoiceTemplatePreviewRequest.setSignatureHeading(signatureHeading);
        Object showQrObj = param.get("showQr");
        if (showQrObj != null) {
            String showQrStr = DataTypeUtility.stringValue(showQrObj);
            if (showQrStr.length() > 0) {
                invoiceTemplatePreviewRequest.setShowQr(DataTypeUtility.booleanValue(showQrObj));
            }
        }
        Object showBankDetailsObj = param.get("showBankDetails");
        if (showBankDetailsObj != null) {
            String showBankDetailsStr = DataTypeUtility.stringValue(showBankDetailsObj);
            if (showBankDetailsStr.length() > 0) {
                invoiceTemplatePreviewRequest.setShowBankDetails(DataTypeUtility.booleanValue(showBankDetailsObj));
            }
        }
        Object showTermsObj = param.get("showTerms");
        if (showTermsObj != null) {
            String showTermsStr = DataTypeUtility.stringValue(showTermsObj);
            if (showTermsStr.length() > 0) {
                invoiceTemplatePreviewRequest.setShowTerms(DataTypeUtility.booleanValue(showTermsObj));
            }
        }
        Object showNotesObj = param.get("showNotes");
        if (showNotesObj != null) {
            String showNotesStr = DataTypeUtility.stringValue(showNotesObj);
            if (showNotesStr.length() > 0) {
                invoiceTemplatePreviewRequest.setShowNotes(DataTypeUtility.booleanValue(showNotesObj));
            }
        }
        String noteText = DataTypeUtility.stringValue(param.get("noteText"));
        if (noteText.length() == 0) {
            noteText = null;
        }
        invoiceTemplatePreviewRequest.setNoteText(noteText);
        String termsText = DataTypeUtility.stringValue(param.get("termsText"));
        if (termsText.length() == 0) {
            termsText = null;
        }
        invoiceTemplatePreviewRequest.setTermsText(termsText);
        String footerCredit = DataTypeUtility.stringValue(param.get("footerCredit"));
        if (footerCredit.length() == 0) {
            footerCredit = null;
        }
        invoiceTemplatePreviewRequest.setFooterCredit(footerCredit);
        return invoiceTemplatePreviewRequest;
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Invoice par Indian grouping: 1,23,456.00 (pehle 123456.00 chhapta tha). */
    private String money(BigDecimal value) {
        BigDecimal scaled = scale(value);
        String plain = scaled.abs().toPlainString();
        int dotIndex = plain.indexOf('.');
        String integerPart = dotIndex < 0 ? plain : plain.substring(0, dotIndex);
        String decimalPart = dotIndex < 0 ? "" : plain.substring(dotIndex);
        StringBuilder grouped = new StringBuilder();
        if (integerPart.length() <= 3) {
            grouped.append(integerPart);
        } else {
            String leading = integerPart.substring(0, integerPart.length() - 3);
            String lastThree = integerPart.substring(integerPart.length() - 3);
            int firstGroup = leading.length() % 2;
            if (firstGroup > 0) {
                grouped.append(leading, 0, firstGroup).append(',');
            }
            for (int index = firstGroup; index < leading.length(); index += 2) {
                grouped.append(leading, index, index + 2).append(',');
            }
            grouped.append(lastThree);
        }
        return (scaled.signum() < 0 ? "-" : "") + grouped + decimalPart;
    }

    private String coalesce(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultIfNull(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : INDIAN_DATE_FORMAT.format(date);
    }

    private String formatDateTime(LocalDate date, LocalDateTime renderedAt) {
        if (date == null) {
            return "";
        }
        LocalDateTime value = renderedAt == null ? date.atStartOfDay() : date.atTime(renderedAt.toLocalTime().withSecond(0).withNano(0));
        return INDIAN_DATE_TIME_FORMAT.format(value);
    }

    private String joinNonBlank(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.trim());
            }
        }
        return String.join(", ", parts);
    }

    private String amountInWords(BigDecimal amount) {
        long rupees = amount.setScale(0, RoundingMode.DOWN).longValue();
        int paise = amount.subtract(BigDecimal.valueOf(rupees)).movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
        StringBuilder builder = new StringBuilder();
        builder.append(toWords(rupees)).append(" Rupees");
        if (paise > 0) {
            builder.append(" and ").append(toWords(paise)).append(" Paise");
        }
        builder.append(" Only");
        return builder.toString();
    }

    private String toWords(long number) {
        if (number == 0) {
            return "Zero";
        }
        String[] units = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
                "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
        String[] tens = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};
        return convert(number, units, tens).trim();
    }

    private String convert(long number, String[] units, String[] tens) {
        if (number < 20) {
            return units[(int) number];
        }
        if (number < 100) {
            return tens[(int) number / 10] + (number % 10 != 0 ? " " + convert(number % 10, units, tens) : "");
        }
        if (number < 1000) {
            return convert(number / 100, units, tens) + " Hundred" + (number % 100 != 0 ? " " + convert(number % 100, units, tens) : "");
        }
        if (number < 100000) {
            return convert(number / 1000, units, tens) + " Thousand" + (number % 1000 != 0 ? " " + convert(number % 1000, units, tens) : "");
        }
        if (number < 10000000) {
            return convert(number / 100000, units, tens) + " Lakh" + (number % 100000 != 0 ? " " + convert(number % 100000, units, tens) : "");
        }
        return convert(number / 10000000, units, tens) + " Crore" + (number % 10000000 != 0 ? " " + convert(number % 10000000, units, tens) : "");
    }
}
