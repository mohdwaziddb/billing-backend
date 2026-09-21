package com.billing.rest;

import com.billing.security.RequiresPermission;
import com.billing.service.dataport.DataPortService;
import com.billing.service.dataport.ProductDataPortDefinition;
import com.billing.util.DataTypeUtility;
import com.billing.util.GeneralResponse;
import com.billing.util.MobileResponseDTOFactory;
import com.billing.util.SanitizeData;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/data-ports/products")
@RequiredArgsConstructor
public class DataPortRestController {

    private final DataPortService dataPortService;
    private final MobileResponseDTOFactory mobileResponseDTOFactory;

    @GetMapping("/sample")
    @RequiresPermission(menu = "PRODUCT_DATAPORT", action = "VIEW")
    public ResponseEntity<?> downloadProductSample(@RequestParam(required = false) Map<String, Object> param, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            DataPortService.SampleFile sampleFile = dataPortService.downloadSample(param, ProductDataPortDefinition.MODULE_KEY);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(sampleFile.fileName()).build().toString())
                    .body(sampleFile.content());
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(menu = "PRODUCT_DATAPORT", action = "ADD")
    public ResponseEntity<?> preview(@RequestParam(required = false) Map<String, Object> param, Authentication authentication, @RequestPart("file") MultipartFile file, HttpServletRequest req) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", dataPortService.preview(param, authentication.getName(), ProductDataPortDefinition.MODULE_KEY, file)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/revalidate")
    @RequiresPermission(menu = "PRODUCT_DATAPORT", action = "ADD")
    public ResponseEntity<?> revalidate(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", dataPortService.previewRows(param, authentication.getName(), ProductDataPortDefinition.MODULE_KEY)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }

    @PostMapping("/import")
    @RequiresPermission(menu = "PRODUCT_DATAPORT", action = "ADD")
    public ResponseEntity<?> importProducts(@RequestBody Map<String, Object> param, Authentication authentication, HttpServletRequest request) {
        try {
            param = SanitizeData.sanitizeMapObj(param);
            return new ResponseEntity<>(new GeneralResponse<>(true, "Successfully", dataPortService.importRows(param, authentication.getName(), ProductDataPortDefinition.MODULE_KEY)), HttpStatus.OK);
        } catch (Exception e) {
            return mobileResponseDTOFactory.reportInternalServerError(e);
        }
    }
}

