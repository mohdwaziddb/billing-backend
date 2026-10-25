package com.billing.service.storage;

import com.billing.core.Registry;
import com.billing.dto.company.CompanyFileInfo;
import com.billing.exception.BadRequestException;
import com.billing.multitenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local-disk mode (S3 off): same key rules as S3. */
class S3StorageServiceTest {

    @TempDir
    Path uploadDir;

    private final S3StorageService storage = new S3StorageService();
    private boolean previousAws;

    @BeforeEach
    void setUp() {
        previousAws = Registry.IS_AWS;
        Registry.IS_AWS = false;
        ReflectionTestUtils.setField(storage, "uploadDir", uploadDir.toString());
        ReflectionTestUtils.setField(storage, "maxBytes", 2L * 1024 * 1024);
        TenantContextHolder.setTenantId("test_write");
    }

    @AfterEach
    void tearDown() {
        Registry.IS_AWS = previousAws;
        TenantContextHolder.clear();
    }

    @Test
    void keepsActualFileNameInTenantFolder() {
        CompanyFileInfo info = storage.store(null, "logo", image("logo.png"));

        assertEquals("test/logo.png", info.getKey());
        assertEquals("logo.png", info.getFileName());
        assertEquals("logo.png", info.getOriginalName());
        assertEquals("logo", info.getKind());
        assertEquals("/uploads/test/logo.png", info.getUrl());
        assertTrue(Files.exists(uploadDir.resolve("test/logo.png")));
    }

    @Test
    void sameNameOfAnotherOwnerGetsSuffixInsteadOfOverwrite() {
        storage.store(null, "logo", image("brand.png"));
        CompanyFileInfo second = storage.store(null, "logo", image("brand.png"));

        assertEquals("test/brand-1.png", second.getKey());
    }

    @Test
    void signatureAndPhotoKeysAreNotGuessable() {
        // Public bucket: "test/signature.png" guess karke kisi ka sign khul jaata tha.
        CompanyFileInfo signature = storage.store(null, "signature", image("signature.png"));
        CompanyFileInfo photo = storage.store(null, "user-photo", image("photo.jpg"));
        CompanyFileInfo photoAgain = storage.store(null, "user-photo", image("photo.jpg"));

        assertTrue(signature.getKey().matches("test/[0-9a-f]{16}-signature\\.png"), signature.getKey());
        assertTrue(photo.getKey().matches("test/[0-9a-f]{16}-photo\\.jpg"), photo.getKey());
        assertTrue(!photo.getKey().equals(photoAgain.getKey()), "every upload gets a fresh key");
        assertEquals("signature.png", signature.getOriginalName());
    }

    @Test
    void sameOwnerReuploadOverwritesOwnKey() {
        CompanyFileInfo first = storage.store(null, "logo", image("logo.png"));
        CompanyFileInfo again = storage.store(null, "logo", image("logo.png"), first.getKey());

        assertEquals("test/logo.png", again.getKey());
    }

    @Test
    void unsafeCharactersAreReplacedButCaseIsKept() {
        CompanyFileInfo info = storage.store(null, "logo", image("My Shop Logo (1).PNG"));

        assertEquals("test/My-Shop-Logo-1.png", info.getKey());
    }

    @Test
    void pathTricksStayInsideTenantFolder() {
        CompanyFileInfo info = storage.store(null, "logo", image("../../etc/passwd.png"));

        assertEquals("test/passwd.png", info.getKey());
    }

    @Test
    void emptyFileIsRejected() {
        assertThrows(BadRequestException.class,
                () -> storage.store(null, "logo", new MockMultipartFile("photo", "a.png", "image/png", new byte[0])));
    }

    private static MockMultipartFile image(String name) {
        String type = name.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        return new MockMultipartFile("photo", name, type, new byte[]{1, 2, 3});
    }
}
