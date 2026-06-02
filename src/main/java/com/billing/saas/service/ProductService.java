package com.billing.saas.service;

import com.billing.saas.dto.product.ProductRequest;
import com.billing.saas.dto.product.ProductResponse;
import com.billing.saas.entity.Company;
import com.billing.saas.entity.Product;
import com.billing.saas.exception.BadRequestException;
import com.billing.saas.exception.ResourceNotFoundException;
import com.billing.saas.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final AccessControlService accessControlService;

    @Transactional
    public ProductResponse create(String email, ProductRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        validateProduct(company, request, null);

        Product product = Product.builder()
                .company(company)
                .name(request.getName())
                .category(request.getCategory())
                .brand(request.getBrand())
                .sku(request.getSku())
                .hsnCode(request.getHsnCode())
                .purchasePrice(scale(request.getPurchasePrice()))
                .sellingPrice(scale(request.getSellingPrice()))
                .stockQty(normalizeCount(request.getStockQty()))
                .minStockQty(normalizeCount(request.getMinStockQty()))
                .taxPercent(scalePercent(request.getTaxPercent()))
                .active(Boolean.TRUE.equals(request.getActive()))
                .build();

        return toResponse(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(String email, String search, Boolean active) {
        Company company = accessControlService.getCurrentCompany(email);
        return productRepository.findAllByCompanyWithFilters(company, active, normalizeSearch(search)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(String email, Long productId) {
        Company company = accessControlService.getCurrentCompany(email);
        return toResponse(getProductOrThrow(company, productId));
    }

    @Transactional
    public ProductResponse update(String email, Long productId, ProductRequest request) {
        Company company = accessControlService.getCurrentCompany(email);
        validateProduct(company, request, productId);
        Product product = getProductOrThrow(company, productId);

        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        product.setSku(request.getSku());
        product.setHsnCode(request.getHsnCode());
        product.setPurchasePrice(scale(request.getPurchasePrice()));
        product.setSellingPrice(scale(request.getSellingPrice()));
        product.setStockQty(normalizeCount(request.getStockQty()));
        product.setMinStockQty(normalizeCount(request.getMinStockQty()));
        product.setTaxPercent(scalePercent(request.getTaxPercent()));
        product.setActive(Boolean.TRUE.equals(request.getActive()));

        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(String email, Long productId) {
        Company company = accessControlService.getCurrentCompany(email);
        Product product = getProductOrThrow(company, productId);
        product.setActive(false);
        productRepository.save(product);
    }

    public Product getProductOrThrow(Company company, Long productId) {
        return productRepository.findByIdAndCompany(productId, company)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void validateProduct(Company company, ProductRequest request, Long productId) {
        if (scale(request.getSellingPrice()).compareTo(scale(request.getPurchasePrice())) < 0) {
            throw new BadRequestException("Selling price cannot be less than purchase price");
        }
        if (productId == null) {
            if (productRepository.existsByCompanyAndSkuIgnoreCase(company, request.getSku())) {
                throw new BadRequestException("SKU already exists in your company");
            }
        } else if (productRepository.existsByCompanyAndSkuIgnoreCaseAndIdNot(company, request.getSku(), productId)) {
            throw new BadRequestException("SKU already exists in your company");
        }
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .category(product.getCategory())
                .brand(product.getBrand())
                .sku(product.getSku())
                .hsnCode(product.getHsnCode())
                .purchasePrice(scale(product.getPurchasePrice()))
                .sellingPrice(scale(product.getSellingPrice()))
                .stockQty(product.getStockQty())
                .minStockQty(product.getMinStockQty())
                .taxPercent(scalePercent(product.getTaxPercent()))
                .active(product.isActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .createdBy(product.getCreatedBy())
                .updatedBy(product.getUpdatedBy())
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePercent(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer normalizeCount(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
