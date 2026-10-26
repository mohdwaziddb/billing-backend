package com.billing.service.invoice;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceLogoEmbedTest {

    @Test
    void largePngLogoIsShrunkAndKeepsTransparency() throws Exception {
        byte[] original = noisyImage(2400, 1200, BufferedImage.TYPE_INT_ARGB, "png");

        String dataUrl = InvoiceTemplateRenderService.toEmbeddableDataUrl(original, "image/png");

        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        BufferedImage embedded = decode(dataUrl);
        assertTrue(embedded.getWidth() <= 600 && embedded.getHeight() <= 400);
        assertEquals(2.0, (double) embedded.getWidth() / embedded.getHeight(), 0.02);
        assertTrue(embedded.getColorModel().hasAlpha());
        assertTrue(base64Bytes(dataUrl) < original.length);
    }

    @Test
    void largeJpegLogoStaysJpeg() throws Exception {
        byte[] original = noisyImage(3000, 2000, BufferedImage.TYPE_INT_RGB, "jpg");

        String dataUrl = InvoiceTemplateRenderService.toEmbeddableDataUrl(original, "image/jpeg");

        assertTrue(dataUrl.startsWith("data:image/jpeg;base64,"));
        BufferedImage embedded = decode(dataUrl);
        assertTrue(embedded.getWidth() <= 600 && embedded.getHeight() <= 400);
        assertTrue(base64Bytes(dataUrl) < original.length);
    }

    @Test
    void smallLogoAndNonRasterAreUnchanged() throws Exception {
        byte[] small = noisyImage(200, 80, BufferedImage.TYPE_INT_ARGB, "png");
        assertEquals("data:image/png;base64," + Base64.getEncoder().encodeToString(small),
                InvoiceTemplateRenderService.toEmbeddableDataUrl(small, "image/png"));

        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' width='4000' height='2000'/>".getBytes();
        assertEquals("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg),
                InvoiceTemplateRenderService.toEmbeddableDataUrl(svg, "image/svg+xml"));
    }

    private static byte[] noisyImage(int width, int height, int type, String format) throws Exception {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        Random random = new Random(7);
        for (int i = 0; i < 4000; i++) {
            graphics.setColor(new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256), type == BufferedImage.TYPE_INT_ARGB ? random.nextInt(256) : 255));
            graphics.fillRect(random.nextInt(width), random.nextInt(height), 30, 30);
        }
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private static BufferedImage decode(String dataUrl) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1))));
    }

    private static int base64Bytes(String dataUrl) {
        return Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1)).length;
    }
}
