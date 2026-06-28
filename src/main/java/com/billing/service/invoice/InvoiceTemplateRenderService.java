package com.billing.service.invoice;

import com.billing.dto.invoice.InvoiceTemplatePreviewRequest;
import com.billing.dto.invoice.InvoiceRenderResponse;
import com.billing.dto.invoice.InvoiceTemplateMetadataResponse;
import com.billing.entity.Company;
import com.billing.entity.CompanyInvoiceSetting;
import com.billing.entity.Invoice;
import com.billing.entity.InvoiceItem;
import com.billing.service.AccessControlService;
import com.billing.service.InvoiceService;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceRenderResponse renderInvoice(String email, Long invoiceId, String requestedTemplateId) {
        Company company = accessControlService.getCurrentCompany(email);
        Invoice invoice = invoiceService.getInvoiceOrThrow(company, invoiceId);
        CompanyInvoiceSetting settings = companyInvoiceSettingsService.resolveForCompany(company);
        String templateId = requestedTemplateId == null || requestedTemplateId.isBlank() ? settings.getDefaultTemplateId() : requestedTemplateId.trim();
        InvoiceTemplateDefinition definition = invoiceTemplateRegistryService.getOrThrow(templateId);
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
        LocalDateTime renderedAt = LocalDateTime.now();
        List<Map<String, Object>> itemRows = new ArrayList<>();
        for (InvoiceItem item : invoice.getItems()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("productName", item.getProduct().getName());
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
            itemRows.add(row);
        }

        root.put("invoice", invoiceData(
                invoice.getInvoiceNo(),
                invoice.getInvoiceDate(),
                invoice.getInvoiceDate(),
                renderedAt,
                coalesce(invoice.getCustomer().getState(), "--"),
                invoice.getCustomer().getName(),
                invoice.getCustomer().getMobile(),
                invoice.getCustomer().getEmail(),
                invoice.getCustomer().getAddress(),
                invoice.getCustomer().getState(),
                invoice.getCustomer().getCountry(),
                coalesce(invoice.getCustomer().getGstin(), invoice.getCustomer().getGstNo()),
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
        root.put("shareUrl", frontendUrl + "/invoices/" + invoice.getId());
        return root;
    }

    private Map<String, Object> buildSampleModel(Company company, CompanyInvoiceSetting settings, InvoiceTemplateDefinition definition) {
        Map<String, Object> root = baseModel(company, settings, definition);
        LocalDateTime renderedAt = LocalDateTime.now();
        List<Map<String, Object>> itemRows = List.of(
                item("Demo Office Chair", "9401", 6, "4500.00", "1500.00", "25500.00", "2295.00", "2295.00", "0.00", "30090.00", "CGST 9% + SGST 9%"),
                item("Demo Workstation Desk", "9403", 3, "8200.00", "600.00", "24000.00", "2160.00", "2160.00", "0.00", "28320.00", "CGST 9% + SGST 9%"),
                item("Demo Deployment Service", "9983", 1, "18000.00", "0.00", "18000.00", "0.00", "0.00", "3240.00", "21240.00", "IGST 18%")
        );
        root.put("invoice", invoiceData(
                "INV-SAMPLE-20260629-001",
                LocalDate.now(),
                LocalDate.now().plusDays(7),
                renderedAt,
                coalesce(company.getState(), "Maharashtra"),
                "Demo Retail Private Limited",
                "9876501234",
                "billing@demo-retail.example",
                "Unit 14, Business Square, Baner, Pune",
                "Maharashtra",
                "India",
                "27ABCDE1234F1Z5",
                itemRows,
                "PARTIAL",
                "69000.00",
                "2100.00",
                "67500.00",
                "4455.00",
                "4455.00",
                "3240.00",
                "0.00",
                "79650.00",
                "30000.00",
                "49650.00",
                amountInWords(new BigDecimal("79650.00")),
                previewWatermarkText(settings)
        ));
        root.put("shareUrl", frontendUrl + "/invoices");
        return root;
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
        companyMap.put("invoiceNotes", defaultIfNull(settings.getNoteText(), "Thank you for choosing BizFinity."));
        companyMap.put("invoiceTerms", defaultIfNull(settings.getTermsText(), "Goods once sold will not be taken back."));
        companyMap.put("qrDataUrl", settings.isShowQr() && definition.isSupportsQr() ? qrDataUrl(qrPayload(company)) : null);

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
                "footerCredit", defaultIfNull(settings.getFooterCredit(), "Generated by BizFinity Billing SaaS")
        ));
        model.put("template", Map.of(
                "id", definition.getTemplateId(),
                "name", definition.getTemplateName(),
                "description", definition.getDescription(),
                "version", definition.getVersion(),
                "colors", definition.getDefaultColors()
        ));
        model.put("generatedAt", INDIAN_DATE_TIME_FORMAT.format(LocalDateTime.now()));
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

    private Map<String, Object> item(String productName, String hsnCode, int qty, String price, String discountAmount, String taxableAmount, String cgstAmount, String sgstAmount, String igstAmount, String lineTotal, String gstBreakup) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("productName", productName);
        row.put("hsnCode", hsnCode);
        row.put("qty", qty);
        row.put("price", price);
        row.put("discountAmount", discountAmount);
        row.put("taxRate", new BigDecimal(cgstAmount).compareTo(BigDecimal.ZERO) > 0 ? "18.00" : "18.00");
        row.put("taxName", "GST");
        row.put("taxableAmount", taxableAmount);
        row.put("cgstAmount", cgstAmount);
        row.put("sgstAmount", sgstAmount);
        row.put("igstAmount", igstAmount);
        row.put("lineTotal", lineTotal);
        row.put("gstBreakup", gstBreakup);
        return row;
    }

    private String qrPayload(Company company) {
        if (company.getUpiId() != null && !company.getUpiId().isBlank()) {
            return "upi://pay?pa=" + company.getUpiId().trim() + "&pn=" + company.getName().replace(" ", "%20");
        }
        return "BANK|" + coalesce(company.getBankName(), "") + "|" + coalesce(company.getBankAccountNumber(), "") + "|" + coalesce(company.getBankIfscCode(), "");
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
        if (fileUrl == null || fileUrl.isBlank()) {
            return null;
        }
        try {
            Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath = baseDir.resolve(fileUrl.replaceFirst("^/uploads/?", "")).normalize();
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

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String money(BigDecimal value) {
        return scale(value).toPlainString();
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
