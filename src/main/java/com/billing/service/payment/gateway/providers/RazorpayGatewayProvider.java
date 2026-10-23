package com.billing.service.payment.gateway.providers;

import com.billing.dto.payment.gateway.PaymentGatewayFieldResponse;
import com.billing.dto.payment.gateway.PaymentGatewayMetadataResponse;
import com.billing.entity.PaymentTransaction;
import com.billing.entity.enums.PaymentGatewayType;
import com.billing.entity.enums.PaymentTransactionStatus;
import com.billing.exception.BadRequestException;
import com.billing.service.payment.gateway.PaymentGatewayProvider;
import com.billing.service.payment.gateway.PaymentGatewayResolvedSettings;
import com.billing.service.payment.gateway.dto.GatewayHealthResult;
import com.billing.service.payment.gateway.dto.GatewayLinkRequest;
import com.billing.service.payment.gateway.dto.GatewayLinkResult;
import com.billing.service.payment.gateway.dto.GatewayOrderRequest;
import com.billing.service.payment.gateway.dto.GatewayOrderResult;
import com.billing.service.payment.gateway.dto.GatewayPaymentResult;
import com.billing.service.payment.gateway.dto.GatewayWebhookEvent;
import com.billing.util.DataTypeUtility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Razorpay adapter: Orders + Checkout, Payment Links, webhooks.
 * Docs: https://razorpay.com/docs/api/ (amount hamesha paise me).
 */
@Component
public class RazorpayGatewayProvider implements PaymentGatewayProvider {

    private static final Logger log = LoggerFactory.getLogger(RazorpayGatewayProvider.class);

    static final String API_BASE_URL = "https://api.razorpay.com/v1";
    static final String KEY_ID = "keyId";
    static final String KEY_SECRET = "keySecret";
    static final String WEBHOOK_SECRET = "webhookSecret";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public RazorpayGatewayProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentGatewayType gatewayType() {
        return PaymentGatewayType.RAZORPAY;
    }

    @Override
    public PaymentGatewayMetadataResponse metadata() {
        return PaymentGatewayMetadataResponse.builder()
                .gatewayType(PaymentGatewayType.RAZORPAY.name())
                .gatewayName("Razorpay")
                .description("UPI, Cards, Netbanking and Wallets via Razorpay Checkout and Payment Links.")
                .supportsCheckout(true)
                .supportsPaymentLink(true)
                .dashboardUrl("https://dashboard.razorpay.com/app/keys")
                .webhookEvents(List.of("payment.captured", "payment.failed", "order.paid", "payment_link.paid", "payment_link.expired", "payment_link.cancelled"))
                .fields(List.of(
                        PaymentGatewayFieldResponse.builder()
                                .key(KEY_ID).label("Key ID").type("text").required(true).sensitive(false)
                                .placeholder("rzp_test_xxxxxxxxxxxx")
                                .helpText("Razorpay Dashboard > Account & Settings > API Keys. Test keys start with rzp_test_.")
                                .build(),
                        PaymentGatewayFieldResponse.builder()
                                .key(KEY_SECRET).label("Key Secret").type("password").required(true).sensitive(true)
                                .placeholder("Key secret")
                                .helpText("Stored encrypted. Never shared with the browser.")
                                .build(),
                        PaymentGatewayFieldResponse.builder()
                                .key(WEBHOOK_SECRET).label("Webhook Secret").type("password").required(false).sensitive(true)
                                .placeholder("Webhook secret")
                                .helpText("Same secret you set while adding the webhook URL in Razorpay Dashboard > Webhooks. Needed for payment links and auto-confirmation.")
                                .build()))
                .build();
    }

    @Override
    public void validateConfiguration(PaymentGatewayResolvedSettings settings) {
        String keyId = settings.value(KEY_ID);
        if (keyId == null) {
            throw new BadRequestException("Razorpay Key ID is required");
        }
        if (!keyId.startsWith("rzp_")) {
            throw new BadRequestException("Razorpay Key ID should start with rzp_");
        }
        if (settings.testMode() && !keyId.startsWith("rzp_test_")) {
            throw new BadRequestException("Test mode is on but the Key ID is a live key. Use an rzp_test_ key or turn off test mode.");
        }
        if (!settings.testMode() && keyId.startsWith("rzp_test_")) {
            throw new BadRequestException("Live mode needs a live Key ID (rzp_live_). Turn on test mode for rzp_test_ keys.");
        }
        if (settings.value(KEY_SECRET) == null) {
            throw new BadRequestException("Razorpay Key Secret is required");
        }
    }

    @Override
    public GatewayHealthResult testConnection(PaymentGatewayResolvedSettings settings) {
        validateConfiguration(settings);
        try {
            RazorpayResponse response = call(settings, "GET", "/orders?count=1", null);
            if (response.status() == 200) {
                return new GatewayHealthResult(true, "Connected to Razorpay successfully");
            }
            if (response.status() == 401) {
                return new GatewayHealthResult(false, "Razorpay rejected the Key ID / Key Secret");
            }
            return new GatewayHealthResult(false, "Razorpay error: " + errorDescription(response.body()));
        } catch (BadRequestException exception) {
            return new GatewayHealthResult(false, exception.getMessage());
        }
    }

    @Override
    public GatewayOrderResult createOrder(PaymentGatewayResolvedSettings settings, GatewayOrderRequest request) {
        validateConfiguration(settings);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", toPaise(request.amount()));
        body.put("currency", request.currency());
        body.put("receipt", truncate(request.receipt(), 40));
        body.set("notes", notesNode(request.notes(), request.txnRef()));
        RazorpayResponse response = call(settings, "POST", "/orders", body);
        JsonNode json = requireSuccess(response, "create order");
        String orderId = DataTypeUtility.stringValue(json.path("id").asText(null));
        if (orderId.isEmpty()) {
            throw new BadRequestException("Razorpay did not return an order id");
        }
        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("key", settings.value(KEY_ID));
        checkout.put("order_id", orderId);
        checkout.put("amount", toPaise(request.amount()));
        checkout.put("currency", request.currency());
        return new GatewayOrderResult(orderId, checkout, response.body());
    }

    @Override
    public GatewayLinkResult createPaymentLink(PaymentGatewayResolvedSettings settings, GatewayLinkRequest request) {
        validateConfiguration(settings);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("amount", toPaise(request.amount()));
        body.put("currency", request.currency());
        body.put("accept_partial", false);
        body.put("reference_id", truncate(request.txnRef(), 40));
        body.put("description", truncate(request.description(), 2048));
        ObjectNode customer = body.putObject("customer");
        if (request.customerName() != null) {
            customer.put("name", request.customerName());
        }
        if (request.customerMobile() != null) {
            customer.put("contact", request.customerMobile());
        }
        if (request.customerEmail() != null) {
            customer.put("email", request.customerEmail());
        }
        // Link hum khud WhatsApp/SMS se bhejte hain; Razorpay ke notification band.
        ObjectNode notify = body.putObject("notify");
        notify.put("sms", false);
        notify.put("email", false);
        body.put("reminder_enable", false);
        if (request.expiresAt() != null) {
            body.put("expire_by", request.expiresAt().atZone(ZoneId.systemDefault()).toEpochSecond());
        }
        body.set("notes", notesNode(request.notes(), request.txnRef()));
        RazorpayResponse response = call(settings, "POST", "/payment_links", body);
        JsonNode json = requireSuccess(response, "create payment link");
        String linkId = json.path("id").asText(null);
        String shortUrl = json.path("short_url").asText(null);
        if (linkId == null || shortUrl == null) {
            throw new BadRequestException("Razorpay did not return a payment link");
        }
        return new GatewayLinkResult(linkId, shortUrl, response.body());
    }

    @Override
    public GatewayPaymentResult verifyCheckout(PaymentGatewayResolvedSettings settings, PaymentTransaction transaction, Map<String, Object> callbackPayload) {
        String paymentId = DataTypeUtility.stringValue(callbackPayload.get("razorpay_payment_id"));
        String orderId = DataTypeUtility.stringValue(callbackPayload.get("razorpay_order_id"));
        String signature = DataTypeUtility.stringValue(callbackPayload.get("razorpay_signature"));
        if (paymentId.isEmpty() || orderId.isEmpty() || signature.isEmpty()) {
            throw new BadRequestException("Incomplete Razorpay payment response");
        }
        if (!orderId.equals(transaction.getGatewayOrderId())) {
            throw new BadRequestException("Razorpay order does not match this transaction");
        }
        if (!isValidCheckoutSignature(orderId, paymentId, signature, settings.value(KEY_SECRET))) {
            throw new BadRequestException("Razorpay payment signature verification failed");
        }
        // Signature sahi = payment authorize hua; actual status/amount gateway se lo (capture bhi yahi).
        return fetchPayment(settings, paymentId);
    }

    @Override
    public GatewayWebhookEvent parseWebhook(PaymentGatewayResolvedSettings settings, String rawBody, Map<String, String> headers) {
        String webhookSecret = settings.value(WEBHOOK_SECRET);
        if (webhookSecret == null) {
            throw new BadRequestException("Razorpay webhook secret is not configured");
        }
        String signature = header(headers, "x-razorpay-signature");
        if (signature == null || !isValidWebhookSignature(rawBody, signature, webhookSecret)) {
            throw new BadRequestException("Invalid Razorpay webhook signature");
        }
        JsonNode root = readJson(rawBody);
        String event = root.path("event").asText("");
        JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
        JsonNode orderEntity = root.path("payload").path("order").path("entity");
        JsonNode linkEntity = root.path("payload").path("payment_link").path("entity");

        String orderId = firstNonBlank(paymentEntity.path("order_id").asText(null), orderEntity.path("id").asText(null), linkEntity.path("order_id").asText(null));
        String linkId = linkEntity.path("id").asText(null);
        String txnRef = firstNonBlank(linkEntity.path("reference_id").asText(null),
                paymentEntity.path("notes").path("txn_ref").asText(null),
                orderEntity.path("notes").path("txn_ref").asText(null));

        GatewayPaymentResult result = switch (event) {
            case "payment.captured", "order.paid", "payment_link.paid" -> paymentEntity.isMissingNode()
                    ? null
                    : toPaymentResult(paymentEntity, rawBody);
            case "payment.failed" -> new GatewayPaymentResult(PaymentTransactionStatus.FAILED,
                    paymentEntity.path("id").asText(null),
                    paymentEntity.path("method").asText(null),
                    fromPaise(paymentEntity.path("amount")),
                    firstNonBlank(paymentEntity.path("error_description").asText(null), "Payment failed"),
                    rawBody);
            case "payment_link.expired" -> new GatewayPaymentResult(PaymentTransactionStatus.EXPIRED, null, null, null, "Payment link expired", rawBody);
            case "payment_link.cancelled" -> new GatewayPaymentResult(PaymentTransactionStatus.CANCELLED, null, null, null, "Payment link cancelled", rawBody);
            default -> null;
        };
        return new GatewayWebhookEvent(event, orderId, linkId, txnRef, result);
    }

    @Override
    public GatewayPaymentResult fetchStatus(PaymentGatewayResolvedSettings settings, PaymentTransaction transaction) {
        if (transaction.getGatewayPaymentId() != null) {
            return fetchPayment(settings, transaction.getGatewayPaymentId());
        }
        if (transaction.getGatewayLinkId() != null) {
            RazorpayResponse response = call(settings, "GET", "/payment_links/" + transaction.getGatewayLinkId(), null);
            JsonNode json = requireSuccess(response, "fetch payment link");
            String linkStatus = json.path("status").asText("");
            for (JsonNode payment : json.path("payments")) {
                String paymentStatus = payment.path("status").asText("");
                if ("captured".equals(paymentStatus) || "authorized".equals(paymentStatus)) {
                    return fetchPayment(settings, payment.path("payment_id").asText());
                }
            }
            return switch (linkStatus) {
                case "expired" -> new GatewayPaymentResult(PaymentTransactionStatus.EXPIRED, null, null, null, "Payment link expired", response.body());
                case "cancelled" -> new GatewayPaymentResult(PaymentTransactionStatus.CANCELLED, null, null, null, "Payment link cancelled", response.body());
                default -> GatewayPaymentResult.pending(response.body());
            };
        }
        if (transaction.getGatewayOrderId() != null) {
            RazorpayResponse response = call(settings, "GET", "/orders/" + transaction.getGatewayOrderId() + "/payments", null);
            JsonNode json = requireSuccess(response, "fetch order payments");
            GatewayPaymentResult failed = null;
            for (JsonNode payment : json.path("items")) {
                String paymentStatus = payment.path("status").asText("");
                if ("captured".equals(paymentStatus) || "authorized".equals(paymentStatus)) {
                    return fetchPayment(settings, payment.path("id").asText());
                }
                if ("failed".equals(paymentStatus) && failed == null) {
                    failed = new GatewayPaymentResult(PaymentTransactionStatus.FAILED, payment.path("id").asText(null),
                            payment.path("method").asText(null), fromPaise(payment.path("amount")),
                            firstNonBlank(payment.path("error_description").asText(null), "Payment failed"), response.body());
                }
            }
            // Failed attempt ke baad customer dobara try kar sakta hai; isliye failed sirf tab jab koi aur na ho.
            return failed != null ? failed : GatewayPaymentResult.pending(response.body());
        }
        return GatewayPaymentResult.pending(null);
    }

    @Override
    public void cancelPaymentLink(PaymentGatewayResolvedSettings settings, PaymentTransaction transaction) {
        if (transaction.getGatewayLinkId() == null) {
            return;
        }
        try {
            call(settings, "POST", "/payment_links/" + transaction.getGatewayLinkId() + "/cancel", objectMapper.createObjectNode());
        } catch (Exception exception) {
            log.warn("Razorpay link cancel failed for {}: {}", transaction.getTxnRef(), exception.getMessage());
        }
    }

    /** HMAC_SHA256(order_id + "|" + payment_id, key_secret) == razorpay_signature */
    public static boolean isValidCheckoutSignature(String orderId, String paymentId, String signature, String keySecret) {
        if (keySecret == null || signature == null) {
            return false;
        }
        return constantTimeEquals(hmacSha256Hex(orderId + "|" + paymentId, keySecret), signature);
    }

    /** HMAC_SHA256(raw request body, webhook_secret) == X-Razorpay-Signature */
    public static boolean isValidWebhookSignature(String rawBody, String signature, String webhookSecret) {
        if (rawBody == null || webhookSecret == null || signature == null) {
            return false;
        }
        return constantTimeEquals(hmacSha256Hex(rawBody, webhookSecret), signature);
    }

    static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute HMAC", exception);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.trim().toLowerCase(Locale.ENGLISH).getBytes(StandardCharsets.UTF_8));
    }

    private GatewayPaymentResult fetchPayment(PaymentGatewayResolvedSettings settings, String paymentId) {
        RazorpayResponse response = call(settings, "GET", "/payments/" + paymentId, null);
        JsonNode payment = requireSuccess(response, "fetch payment");
        if ("authorized".equals(payment.path("status").asText(""))) {
            // Account par auto-capture off ho to authorized payment kuch din me refund ho jaata hai — yahin capture.
            ObjectNode body = objectMapper.createObjectNode();
            body.put("amount", payment.path("amount").asLong());
            body.put("currency", payment.path("currency").asText("INR"));
            RazorpayResponse captureResponse = call(settings, "POST", "/payments/" + paymentId + "/capture", body);
            if (captureResponse.status() >= 200 && captureResponse.status() < 300) {
                return toPaymentResult(readJson(captureResponse.body()), captureResponse.body());
            }
            log.warn("Razorpay capture failed for {}: {}", paymentId, errorDescription(captureResponse.body()));
        }
        return toPaymentResult(payment, response.body());
    }

    private GatewayPaymentResult toPaymentResult(JsonNode payment, String rawBody) {
        String status = payment.path("status").asText("");
        PaymentTransactionStatus mapped = switch (status) {
            case "captured" -> PaymentTransactionStatus.SUCCESS;
            case "failed" -> PaymentTransactionStatus.FAILED;
            default -> PaymentTransactionStatus.PENDING;
        };
        return new GatewayPaymentResult(mapped,
                payment.path("id").asText(null),
                payment.path("method").asText(null),
                fromPaise(payment.path("amount")),
                mapped == PaymentTransactionStatus.FAILED ? firstNonBlank(payment.path("error_description").asText(null), "Payment failed") : null,
                rawBody);
    }

    private RazorpayResponse call(PaymentGatewayResolvedSettings settings, String method, String path, JsonNode body) {
        String keyId = settings.value(KEY_ID);
        String keySecret = settings.value(KEY_SECRET);
        if (keyId == null || keySecret == null) {
            throw new BadRequestException("Razorpay keys are not configured");
        }
        String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(API_BASE_URL + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + auth)
                .header("Accept", "application/json");
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body.toString()));
        } else {
            builder.GET();
        }
        try {
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new RazorpayResponse(response.statusCode(), response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Razorpay request interrupted");
        } catch (Exception exception) {
            log.warn("Razorpay {} {} failed: {}", method, path, exception.getMessage());
            throw new BadRequestException("Unable to reach Razorpay. Please try again.");
        }
    }

    private JsonNode requireSuccess(RazorpayResponse response, String action) {
        if (response.status() == 401) {
            throw new BadRequestException("Razorpay rejected the API keys. Check Payment Gateway settings.");
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new BadRequestException("Razorpay could not " + action + ": " + errorDescription(response.body()));
        }
        return readJson(response.body());
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception exception) {
            throw new BadRequestException("Invalid response from Razorpay");
        }
    }

    private String errorDescription(String body) {
        try {
            String description = objectMapper.readTree(body).path("error").path("description").asText(null);
            return description == null ? "unexpected response" : description;
        } catch (Exception exception) {
            return "unexpected response";
        }
    }

    private ObjectNode notesNode(Map<String, String> notes, String txnRef) {
        ObjectNode node = objectMapper.createObjectNode();
        if (notes != null) {
            notes.forEach((key, value) -> {
                if (value != null) {
                    node.put(key, truncate(value, 256));
                }
            });
        }
        node.put("txn_ref", txnRef);
        return node;
    }

    static long toPaise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private static BigDecimal fromPaise(JsonNode amountNode) {
        if (amountNode == null || amountNode.isMissingNode() || amountNode.isNull()) {
            return null;
        }
        return BigDecimal.valueOf(amountNode.asLong()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }

    private static String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record RazorpayResponse(int status, String body) {
    }
}
