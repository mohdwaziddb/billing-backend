package com.billing.controller;

import com.billing.dto.ApiResponse;
import com.billing.dto.dataport.DataPortPreviewResponse;
import com.billing.dto.dataport.ImportResult;
import com.billing.dto.dataport.ProductDataPortImportRequest;
import com.billing.dto.dataport.ProductDataPortRow;
import com.billing.security.RequiresPermission;
import com.billing.service.dataport.DataPortService;
import com.billing.service.dataport.ProductDataPortDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/data-ports/products")
@RequiredArgsConstructor
public class DataPortController {

    private final DataPortService dataPortService;

    @GetMapping("/sample")
    @RequiresPermission(menu = "PRODUCT_DATAPORT", action = "VIEW")
    public ResponseEntity<byte[]> downloadProductSample() {
        DataPortService.SampleFile sampleFile = dataPortService.downloadSample(ProductDataPortDefinition.MODULE_KEY);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(sampleFile.fileName()).build().toString())
                .body(sampleFile.content());
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(menu = "PRODUCT_DATAPORT", action = "ADD")
    public ResponseEntity<ApiResponse<DataPortPreviewResponse<ProductDataPortRow>>> preview(Authentication authentication,
                                                                                            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(
                "Product file preview generated successfully",
                dataPortService.preview(authentication.getName(), ProductDataPortDefinition.MODULE_KEY, file)
        ));
    }

    @PostMapping("/revalidate")
    @RequiresPermission(menu = "PRODUCT_DATAPORT", action = "ADD")
    public ResponseEntity<ApiResponse<DataPortPreviewResponse<ProductDataPortRow>>> revalidate(Authentication authentication,
                                                                                               @RequestBody ProductDataPortImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Product preview revalidated successfully",
                dataPortService.previewRows(authentication.getName(), ProductDataPortDefinition.MODULE_KEY, request.getRows())
        ));
    }

    @PostMapping("/import")
    @RequiresPermission(menu = "PRODUCT_DATAPORT", action = "ADD")
    public ResponseEntity<ApiResponse<ImportResult>> importProducts(Authentication authentication,
                                                                    @RequestBody ProductDataPortImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Products imported successfully",
                dataPortService.importRows(authentication.getName(), ProductDataPortDefinition.MODULE_KEY, request.getRows())
        ));
    }
}
