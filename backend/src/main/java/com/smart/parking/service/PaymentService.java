package com.smart.parking.service;

import com.smart.parking.model.ParkingSpot;
import com.smart.parking.model.Receipt;
import com.smart.parking.model.Reservation;
import com.smart.parking.repository.ParkingSpotRepository;
import com.smart.parking.repository.ReceiptRepository;
import com.smart.parking.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private NotificationService notificationService;

    @Value("${cashfree.app.id:}")
    private String cashfreeAppId;

    @Value("${cashfree.secret.key:}")
    private String cashfreeSecretKey;

    @Value("${cashfree.environment:sandbox}")
    private String cashfreeEnv;

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    private final RestTemplate restTemplate = new RestTemplate();

    // In-memory transaction store (simulated payment gateway)
    private final Map<String, Map<String, Object>> pendingTransactions = new HashMap<>();

    /**
     * Initiate a payment for a reservation.
     * Returns a mock transaction ID that the frontend uses to trigger the webhook.
     */
    public Map<String, Object> initiatePayment(Long reservationId, String paymentMethod) {
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            return Map.of("error", "Reservation not found", "status", "FAILED");
        }

        Reservation res = resOpt.get();

        if ("COMMUNIPAY".equalsIgnoreCase(paymentMethod)) {
            String token = "ctok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            
            Map<String, Object> txn = new HashMap<>();
            txn.put("transactionId", token);
            txn.put("reservationId", reservationId);
            txn.put("amount", res.getRevenue());
            txn.put("paymentMethod", "COMMUNIPAY");
            txn.put("status", "INITIATED");
            txn.put("initiatedAt", LocalDateTime.now().toString());

            pendingTransactions.put(token, txn);

            res.setPaymentMethod("COMMUNIPAY");
            res.setPaymentStatus("PENDING");
            reservationRepository.save(res);

            log.info("💳 COMMUNIPAY TOKEN CREATED → Res: {}, Token: {}", reservationId, token);

            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("reservationId", reservationId);
            result.put("amount", res.getRevenue());
            result.put("status", "INITIATED");
            return result;
        }

        if ("CASHFREE".equalsIgnoreCase(paymentMethod)) {
            // Check if App ID or Secret Key are missing/empty
            if (cashfreeAppId == null || cashfreeAppId.trim().isEmpty() || cashfreeSecretKey == null || cashfreeSecretKey.trim().isEmpty()) {
                return getMockCashfreeSession(reservationId, res);
            } else {
                // Call real Cashfree API to create an order
                try {
                    String orderId = "order_res_" + reservationId + "_" + System.currentTimeMillis();
                    String url = "sandbox".equalsIgnoreCase(cashfreeEnv) 
                            ? "https://sandbox.cashfree.com/pg/orders" 
                            : "https://api.cashfree.com/pg/orders";

                    HttpHeaders headers = new HttpHeaders();
                    headers.set("x-client-id", cashfreeAppId);
                    headers.set("x-client-secret", cashfreeSecretKey);
                    headers.set("x-api-version", "2023-08-01");
                    headers.set("Content-Type", "application/json");

                    Map<String, Object> customerDetails = new HashMap<>();
                    customerDetails.put("customer_id", "cust_" + reservationId);
                    customerDetails.put("customer_phone", res.getPhoneNumber() != null && !res.getPhoneNumber().isEmpty() ? res.getPhoneNumber().replaceAll("[^0-9]", "") : "9999999999");
                    customerDetails.put("customer_name", res.getClientName() != null ? res.getClientName() : "Guest Driver");
                    customerDetails.put("customer_email", res.getClientEmail() != null ? res.getClientEmail() : "guest@example.com");

                    Map<String, Object> orderMeta = new HashMap<>();
                    orderMeta.put("return_url", "http://localhost:5173/payment.html?resId=" + reservationId + "&order_id={order_id}");

                    Map<String, Object> body = new HashMap<>();
                    body.put("order_amount", Math.round(res.getRevenue() * 100.0) / 100.0);
                    body.put("order_currency", "INR");
                    body.put("order_id", orderId);
                    body.put("customer_details", customerDetails);
                    body.put("order_meta", orderMeta);

                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                    ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        Map<String, Object> respBody = response.getBody();
                        String paymentSessionId = (String) respBody.get("payment_session_id");

                        Map<String, Object> txn = new HashMap<>();
                        txn.put("transactionId", orderId);
                        txn.put("reservationId", reservationId);
                        txn.put("amount", res.getRevenue());
                        txn.put("paymentMethod", "CASHFREE");
                        txn.put("status", "INITIATED");
                        txn.put("initiatedAt", LocalDateTime.now().toString());

                        pendingTransactions.put(orderId, txn);

                        res.setPaymentMethod("CASHFREE");
                        res.setPaymentStatus("PENDING");
                        reservationRepository.save(res);

                        log.info("💳 REAL CASHFREE ORDER CREATED → Order ID: {}, Session ID: {}", orderId, paymentSessionId);

                        Map<String, Object> result = new HashMap<>();
                        result.put("paymentSessionId", paymentSessionId);
                        result.put("orderId", orderId);
                        result.put("cfEnv", cashfreeEnv);
                        result.put("isMock", false);
                        result.put("amount", res.getRevenue());
                        return result;
                    } else {
                        log.warn("⚠️ Real Cashfree Order creation failed (status: {}). Falling back to Mock Simulator.", response.getStatusCode());
                        return getMockCashfreeSession(reservationId, res);
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Exception calling real Cashfree API: {}. Falling back to Mock Simulator.", e.getMessage());
                    return getMockCashfreeSession(reservationId, res);
                }
            }
        }

        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        // Generate a 4-digit secret verification code
        String secretCode = String.format("%04d", new Random().nextInt(9000) + 1000);

        Map<String, Object> txn = new HashMap<>();
        txn.put("transactionId", txnId);
        txn.put("reservationId", reservationId);
        txn.put("amount", res.getRevenue());
        txn.put("paymentMethod", paymentMethod);
        txn.put("status", "INITIATED");
        txn.put("secretCode", secretCode);
        txn.put("initiatedAt", LocalDateTime.now().toString());

        pendingTransactions.put(txnId, txn);

        res.setPaymentMethod(paymentMethod);
        res.setPaymentStatus("PENDING");
        reservationRepository.save(res);

        log.info("💳 PAYMENT INITIATED → TXN: {}, Secret: {}, Amount: ₹{}, Method: {}", 
                txnId, secretCode, String.format("%.0f", res.getRevenue()), paymentMethod);

        Map<String, Object> result = new HashMap<>(txn);
        result.put("webhookUrl", "/api/webhook/payment");
        return result;
    }

    public Map<String, Object> processWebhookCallback(String transactionId) {
        return processWebhookCallback(transactionId, null, null);
    }

    /**
     * Process webhook callback with support for custom transaction IDs and dynamic secret codes.
     */
    public Map<String, Object> processWebhookCallback(String transactionId, String mockTxnId, String note) {
        Map<String, Object> txn = null;
        
        // 1. Try to find the transaction by PhonePe webhook note
        if (note != null && !note.isEmpty()) {
            try {
                String[] parts = note.split("_Secret_");
                if (parts.length == 2) {
                    String secretCode = parts[1].trim();
                    String prefix = parts[0];
                    String[] prefixParts = prefix.split("_Vehicle_");
                    if (prefixParts.length == 2) {
                        String resIdStr = prefixParts[0].replace("Booking_", "").trim();
                        Long reservationId = Long.parseLong(resIdStr);
                        
                        String matchingTxnId = null;
                        for (Map.Entry<String, Map<String, Object>> entry : pendingTransactions.entrySet()) {
                            Map<String, Object> t = entry.getValue();
                            Long rId = ((Number) t.get("reservationId")).longValue();
                            if (rId.equals(reservationId)) {
                                String storedSecret = (String) t.get("secretCode");
                                if (storedSecret != null && storedSecret.equals(secretCode)) {
                                    matchingTxnId = entry.getKey();
                                    txn = t;
                                    break;
                                }
                            }
                        }
                        
                        // Swap transaction ID in map if found
                        if (txn != null && matchingTxnId != null && !matchingTxnId.equals(transactionId)) {
                            pendingTransactions.remove(matchingTxnId);
                            txn.put("transactionId", transactionId);
                            pendingTransactions.put(transactionId, txn);
                            log.info("🔄 Webhook Swapped TXN: {} → {}", matchingTxnId, transactionId);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error parsing webhook note: {}", note, e);
            }
        }
        
        // 2. Fallback to mockTxnId swap
        if (txn == null && mockTxnId != null && !mockTxnId.isEmpty()) {
            txn = pendingTransactions.get(mockTxnId);
            if (txn != null && !mockTxnId.equals(transactionId)) {
                pendingTransactions.remove(mockTxnId);
                txn.put("transactionId", transactionId);
                pendingTransactions.put(transactionId, txn);
                log.info("🔄 Webhook Swapped TXN by mockTxnId: {} → {}", mockTxnId, transactionId);
            }
        }
        
        // 3. Direct lookup
        if (txn == null) {
            txn = pendingTransactions.get(transactionId);
        }
        
        if (txn == null) {
            return Map.of("error", "Transaction not found", "status", "FAILED");
        }

        Long reservationId = ((Number) txn.get("reservationId")).longValue();
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            return Map.of("error", "Reservation not found", "status", "FAILED");
        }

        Reservation res = resOpt.get();

        // 1. Mark payment as PAID
        res.setPaymentStatus("PAID");
        res.setPaymentMethod((String) txn.get("paymentMethod"));
        reservationRepository.save(res);

        // 2. Generate all 3 receipts (CUSTOMER, ADMIN, LOCK)
        List<Receipt> receipts = receiptService.generateAllReceipts(res);
        reservationRepository.save(res); // save again to store receipt number

        // 3. Mark all receipts as PAID and webhook verified
        for (Receipt r : receipts) {
            r.setPaymentStatus("PAID");
            r.setWebhookVerified(true);
            receiptRepository.save(r);
        }

        // 4. Send notifications
        Receipt customerReceipt = receipts.stream()
                .filter(r -> "CUSTOMER".equals(r.getReceiptType()))
                .findFirst().orElse(receipts.get(0));

        Map<String, Object> emailResult = notificationService.sendEmailReceipt(
                res.getClientEmail(), customerReceipt);

        Map<String, Object> whatsappResult = null;
        if (res.getPhoneNumber() != null && !res.getPhoneNumber().isEmpty()) {
            whatsappResult = notificationService.sendWhatsAppReceipt(
                    res.getPhoneNumber(), customerReceipt);
        }

        // 5. Update transaction status
        txn.put("status", "COMPLETED");
        txn.put("completedAt", LocalDateTime.now().toString());

        log.info("✅ PAYMENT CONFIRMED → TXN: {}, Reservation: #{}, Receipts: {}", 
                transactionId, reservationId, receipts.size());

        // Build response
        Map<String, Object> result = new HashMap<>();
        result.put("status", "PAID");
        result.put("transactionId", transactionId);
        result.put("reservationId", reservationId);
        result.put("amount", res.getRevenue());
        result.put("receiptNumber", customerReceipt.getReceiptNumber());
        result.put("receiptsGenerated", receipts.size());
        result.put("emailNotification", emailResult);
        result.put("whatsappNotification", whatsappResult);
        result.put("timestamp", LocalDateTime.now().toString());

        return result;
    }

    /**
     * Verify manual verification details: vehicle number & dynamic secret code.
     */
    public Map<String, Object> verifyManual(Long reservationId, String vehicleNumber, String secretCode) {
        // Find reservation first
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            return Map.of("error", "Reservation not found", "status", "FAILED");
        }
        Reservation res = resOpt.get();

        // 1. Verify Vehicle Number (case-insensitive and ignoring hyphens/spaces)
        String storedVehicle = res.getVehicleNumber();
        String cleanStored = storedVehicle == null ? "" : storedVehicle.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        String cleanProvided = vehicleNumber == null ? "" : vehicleNumber.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (cleanStored.isEmpty() || !cleanStored.equals(cleanProvided)) {
            log.warn("❌ Manual Verification Vehicle Mismatch: Stored: {}, Provided: {}", storedVehicle, vehicleNumber);
            return Map.of("error", "Vehicle number mismatch. Mismatch confirmed failed.", "status", "FAILED");
        }

        // 2. Find pending transaction for this reservation matching the secret code
        Map<String, Object> txn = null;
        String storedTxnId = null;
        for (Map.Entry<String, Map<String, Object>> entry : pendingTransactions.entrySet()) {
            Map<String, Object> t = entry.getValue();
            Long resId = ((Number) t.get("reservationId")).longValue();
            if (resId.equals(reservationId)) {
                String storedSecret = (String) t.get("secretCode");
                if (storedSecret != null && storedSecret.equals(secretCode)) {
                    txn = t;
                    storedTxnId = entry.getKey();
                    break;
                }
            }
        }

        if (txn == null) {
            if ("PAID".equals(res.getPaymentStatus())) {
                return Map.of("status", "PAID", "message", "Payment already verified");
            }
            
            // Log active secret codes for this reservation to help debug
            List<String> activeSecrets = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : pendingTransactions.entrySet()) {
                Map<String, Object> t = entry.getValue();
                Long resId = ((Number) t.get("reservationId")).longValue();
                if (resId.equals(reservationId)) {
                    activeSecrets.add((String) t.get("secretCode"));
                }
            }
            log.warn("❌ Manual Verification Secret Mismatch: Provided Secret: {}, Stored Secrets for Res #{}: {}", 
                    secretCode, reservationId, activeSecrets);
            
            return Map.of("error", "Secret verification code mismatch. Mismatch confirmed failed.", "status", "FAILED");
        }

        // If completed already
        String status = (String) txn.get("status");
        if ("COMPLETED".equals(status)) {
            return Map.of("status", "PAID", "transactionId", storedTxnId);
        }

        // Otherwise complete it now
        Map<String, Object> webhookResult = processWebhookCallback(storedTxnId, null, null);
        if (webhookResult.containsKey("error")) {
            return webhookResult;
        }

        return Map.of("status", "PAID", "transactionId", storedTxnId);
    }

    /**
     * Verify details only (vehicle number + secret code) WITHOUT completing payment.
     * Used by the frontend to validate user-entered details before polling for UPI payment.
     */
    public Map<String, Object> verifyDetailsOnly(Long reservationId, String vehicleNumber, String secretCode) {
        // Find reservation
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            return Map.of("verified", false, "error", "Reservation not found");
        }
        Reservation res = resOpt.get();

        // Already paid? Return verified immediately
        if ("PAID".equals(res.getPaymentStatus())) {
            return Map.of("verified", true, "alreadyPaid", true, "message", "Payment already confirmed");
        }

        // 1. Verify Vehicle Number (case-insensitive, ignoring hyphens/spaces)
        String storedVehicle = res.getVehicleNumber();
        String cleanStored = storedVehicle == null ? "" : storedVehicle.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        String cleanProvided = vehicleNumber == null ? "" : vehicleNumber.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (cleanStored.isEmpty() || !cleanStored.equals(cleanProvided)) {
            log.warn("❌ Detail Verify Vehicle Mismatch: Stored: {}, Provided: {}", storedVehicle, vehicleNumber);
            return Map.of("verified", false, "error", "Vehicle number does not match the reservation.");
        }

        // 2. Check secret code matches a pending transaction
        boolean secretMatch = false;
        for (Map.Entry<String, Map<String, Object>> entry : pendingTransactions.entrySet()) {
            Map<String, Object> t = entry.getValue();
            Long resId = ((Number) t.get("reservationId")).longValue();
            if (resId.equals(reservationId)) {
                String storedSecret = (String) t.get("secretCode");
                if (storedSecret != null && storedSecret.equals(secretCode)) {
                    secretMatch = true;
                    break;
                }
            }
        }

        if (!secretMatch) {
            // Log active secrets for debugging
            List<String> activeSecrets = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : pendingTransactions.entrySet()) {
                Map<String, Object> t = entry.getValue();
                Long resId = ((Number) t.get("reservationId")).longValue();
                if (resId.equals(reservationId)) {
                    activeSecrets.add((String) t.get("secretCode"));
                }
            }
            log.warn("❌ Detail Verify Secret Mismatch: Provided: {}, Active for Res #{}: {}", 
                    secretCode, reservationId, activeSecrets);
            return Map.of("verified", false, "error", "Secret code does not match. Please check your UPI transaction note.");
        }

        log.info("✅ DETAILS VERIFIED (no payment yet) → Res #{}, Vehicle: {}", reservationId, vehicleNumber);
        return Map.of("verified", true, "message", "Details verified. Waiting for UPI payment confirmation.");
    }

    /**
     * Direct payment confirmation (skips webhook for simpler flow).
     */
    public Map<String, Object> confirmPaymentDirect(Long reservationId, String paymentMethod) {
        Map<String, Object> initResult = initiatePayment(reservationId, paymentMethod);
        if (initResult.containsKey("error")) return initResult;
        
        String txnId = (String) initResult.get("transactionId");
        return processWebhookCallback(txnId);
    }

    public Map<String, Object> getTransactionStatus(String transactionId) {
        Map<String, Object> txn = pendingTransactions.get(transactionId);
        if (txn == null) {
            return Map.of("error", "Transaction not found", "status", "FAILED");
        }
        return txn;
    }

    /**
     * Verify Cashfree payment status.
     */
    public Map<String, Object> verifyCashfreePayment(String orderId) {
        log.info("🔍 Verifying Cashfree payment for order ID: {}", orderId);
        
        // Extract reservation ID from orderId format "order_res_{resId}_{timestamp}"
        Long reservationId = null;
        try {
            if (orderId.startsWith("order_res_")) {
                String[] parts = orderId.split("_");
                if (parts.length >= 3) {
                    reservationId = Long.parseLong(parts[2]);
                }
            }
        } catch (Exception e) {
            log.error("Error parsing reservation ID from orderId: {}", orderId, e);
        }

        // If not parsed, try looking up in pendingTransactions
        if (reservationId == null) {
            Map<String, Object> txn = pendingTransactions.get(orderId);
            if (txn != null) {
                reservationId = ((Number) txn.get("reservationId")).longValue();
            }
        }

        if (reservationId == null) {
            return Map.of("error", "Reservation not found for this order", "status", "FAILED");
        }

        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            return Map.of("error", "Reservation not found", "status", "FAILED");
        }
        Reservation res = resOpt.get();

        // If already paid
        if ("PAID".equals(res.getPaymentStatus())) {
            return Map.of("status", "PAID", "transactionId", orderId, "message", "Already verified");
        }

        boolean isPaid = false;

        // If it's a mock order id (containing a mock check or no credentials set)
        if (cashfreeAppId == null || cashfreeAppId.trim().isEmpty() || cashfreeSecretKey == null || cashfreeSecretKey.trim().isEmpty() || orderId.startsWith("mock_")) {
            // Simulator or direct API check without keys -> mark paid!
            isPaid = true;
            log.info("⚙️ Mock verify Cashfree: Success");
        } else {
            // Verify via Cashfree API
            try {
                String url = "sandbox".equalsIgnoreCase(cashfreeEnv)
                        ? "https://sandbox.cashfree.com/pg/orders/" + orderId
                        : "https://api.cashfree.com/pg/orders/" + orderId;

                HttpHeaders headers = new HttpHeaders();
                headers.set("x-client-id", cashfreeAppId);
                headers.set("x-client-secret", cashfreeSecretKey);
                headers.set("x-api-version", "2023-08-01");

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> respBody = response.getBody();
                    String orderStatus = (String) respBody.get("order_status");
                    log.info("🔍 Cashfree order API response status: {}", orderStatus);
                    if ("PAID".equalsIgnoreCase(orderStatus)) {
                        isPaid = true;
                    }
                }
            } catch (Exception e) {
                log.error("Exception verifying Cashfree payment", e);
            }
        }

        if (isPaid) {
            // Complete the transaction in pendingTransactions if present
            Map<String, Object> txn = pendingTransactions.get(orderId);
            if (txn == null) {
                txn = new HashMap<>();
                txn.put("transactionId", orderId);
                txn.put("reservationId", reservationId);
                txn.put("amount", res.getRevenue());
                txn.put("paymentMethod", "CASHFREE");
                txn.put("status", "INITIATED");
                pendingTransactions.put(orderId, txn);
            }

            // Confirm payment details
            res.setPaymentStatus("PAID");
            res.setPaymentMethod("CASHFREE");
            reservationRepository.save(res);

            // Generate receipts
            List<Receipt> receipts = receiptService.generateAllReceipts(res);
            for (Receipt r : receipts) {
                r.setPaymentStatus("PAID");
                r.setWebhookVerified(true);
                receiptRepository.save(r);
            }

            Receipt customerReceipt = receipts.stream()
                    .filter(r -> "CUSTOMER".equals(r.getReceiptType()))
                    .findFirst().orElse(receipts.get(0));

            notificationService.sendEmailReceipt(res.getClientEmail(), customerReceipt);
            if (res.getPhoneNumber() != null && !res.getPhoneNumber().isEmpty()) {
                notificationService.sendWhatsAppReceipt(res.getPhoneNumber(), customerReceipt);
            }

            txn.put("status", "COMPLETED");
            txn.put("completedAt", LocalDateTime.now().toString());

            return Map.of(
                "status", "PAID",
                "transactionId", orderId,
                "reservationId", reservationId,
                "amount", res.getRevenue(),
                "receiptNumber", customerReceipt.getReceiptNumber()
            );
        } else {
            return Map.of("status", "PENDING", "message", "Payment has not been completed yet.");
        }
    }

    private Map<String, Object> getMockCashfreeSession(Long reservationId, Reservation res) {
        String mockSessionId = "mock_session_" + UUID.randomUUID().toString().replace("-", "");
        String orderId = "order_res_" + reservationId + "_" + System.currentTimeMillis();

        Map<String, Object> txn = new HashMap<>();
        txn.put("transactionId", orderId);
        txn.put("reservationId", reservationId);
        txn.put("amount", res.getRevenue());
        txn.put("paymentMethod", "CASHFREE");
        txn.put("status", "INITIATED");
        txn.put("initiatedAt", LocalDateTime.now().toString());
        
        pendingTransactions.put(orderId, txn);

        res.setPaymentMethod("CASHFREE");
        res.setPaymentStatus("PENDING");
        reservationRepository.save(res);

        log.info("💳 MOCK CASHFREE ORDER CREATED → Order ID: {}, Session ID: {}", orderId, mockSessionId);

        Map<String, Object> result = new HashMap<>();
        result.put("paymentSessionId", mockSessionId);
        result.put("orderId", orderId);
        result.put("cfEnv", "sandbox");
        result.put("isMock", true);
        result.put("amount", res.getRevenue());
        return result;
    }

    /**
     * Process and complete a custom CommuniPay gateway payment.
     */
    public Map<String, Object> processCommuniPayPayment(String token) {
        log.info("🔍 Processing CommuniPay payment with token: {}", token);
        
        Map<String, Object> txn = pendingTransactions.get(token);
        if (txn == null) {
            return Map.of("error", "Invalid or expired payment token", "status", "FAILED");
        }

        Long reservationId = ((Number) txn.get("reservationId")).longValue();
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            return Map.of("error", "Reservation not found", "status", "FAILED");
        }
        Reservation res = resOpt.get();

        // Already paid?
        if ("PAID".equals(res.getPaymentStatus())) {
            return Map.of("status", "PAID", "transactionId", token, "message", "Payment already completed");
        }

        // Update reservation to PAID
        res.setPaymentStatus("PAID");
        res.setPaymentMethod("COMMUNIPAY");
        reservationRepository.save(res);

        // Generate receipts
        List<Receipt> receipts = receiptService.generateAllReceipts(res);
        for (Receipt r : receipts) {
            r.setPaymentStatus("PAID");
            r.setWebhookVerified(true);
            receiptRepository.save(r);
        }

        Receipt customerReceipt = receipts.stream()
                .filter(r -> "CUSTOMER".equals(r.getReceiptType()))
                .findFirst().orElse(receipts.get(0));

        notificationService.sendEmailReceipt(res.getClientEmail(), customerReceipt);
        if (res.getPhoneNumber() != null && !res.getPhoneNumber().isEmpty()) {
            notificationService.sendWhatsAppReceipt(res.getPhoneNumber(), customerReceipt);
        }

        txn.put("status", "COMPLETED");
        txn.put("completedAt", LocalDateTime.now().toString());

        return Map.of(
            "status", "PAID",
            "transactionId", token,
            "reservationId", reservationId,
            "amount", res.getRevenue(),
            "receiptNumber", customerReceipt.getReceiptNumber()
        );
    }

    /**
     * Initiate a Razorpay payment for a reservation.
     * Returns a real Razorpay Order ID if API keys are configured, otherwise a mock one.
     */
    public Map<String, Object> initiateRazorpayPayment(Long reservationId) {
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            return Map.of("error", "Reservation not found", "status", "FAILED");
        }

        Reservation res = resOpt.get();
        
        // If real keys are configured, attempt real order creation
        if (razorpayKeyId != null && !razorpayKeyId.trim().isEmpty() &&
            razorpayKeySecret != null && !razorpayKeySecret.trim().isEmpty()) {
            
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                
                String auth = razorpayKeyId + ":" + razorpayKeySecret;
                byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                String authHeader = "Basic " + new String(encodedAuth);
                headers.set("Authorization", authHeader);
                
                long amountInPaise = Math.round(res.getRevenue() * 100);
                Map<String, Object> body = new HashMap<>();
                body.put("amount", amountInPaise);
                body.put("currency", "INR");
                body.put("receipt", "receipt_" + reservationId);
                
                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                
                log.info("Creating real Razorpay order for reservation #{} (Amount: {} paise)", reservationId, amountInPaise);
                ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.razorpay.com/v1/orders", 
                    request, 
                    Map.class
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> resBody = response.getBody();
                    String realOrderId = (String) resBody.get("id");
                    
                    Map<String, Object> txn = new HashMap<>();
                    txn.put("transactionId", realOrderId);
                    txn.put("reservationId", reservationId);
                    txn.put("amount", res.getRevenue());
                    txn.put("paymentMethod", "RAZORPAY");
                    txn.put("status", "INITIATED");
                    txn.put("initiatedAt", LocalDateTime.now().toString());
                    
                    pendingTransactions.put(realOrderId, txn);
                    
                    res.setPaymentMethod("RAZORPAY");
                    res.setPaymentStatus("PENDING");
                    reservationRepository.save(res);
                    
                    log.info("💳 REAL RAZORPAY ORDER CREATED → Order ID: {}, Amount: ₹{}", realOrderId, res.getRevenue());
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("orderId", realOrderId);
                    result.put("amount", res.getRevenue());
                    result.put("status", "INITIATED");
                    result.put("key", razorpayKeyId);
                    return result;
                } else {
                    log.error("Failed to create real Razorpay order, status code: {}", response.getStatusCode());
                }
            } catch (Exception e) {
                log.error("Exception occurred while calling Razorpay Orders API: ", e);
            }
        }

        // Mock Fallback
        String orderId = "order_mock_" + reservationId + "_" + System.currentTimeMillis();
        
        Map<String, Object> txn = new HashMap<>();
        txn.put("transactionId", orderId);
        txn.put("reservationId", reservationId);
        txn.put("amount", res.getRevenue());
        txn.put("paymentMethod", "RAZORPAY");
        txn.put("status", "INITIATED");
        txn.put("initiatedAt", LocalDateTime.now().toString());

        pendingTransactions.put(orderId, txn);

        res.setPaymentMethod("RAZORPAY");
        res.setPaymentStatus("PENDING");
        reservationRepository.save(res);

        log.info("💳 RAZORPAY MOCK ORDER CREATED → Order ID: {}, Amount: ₹{}", orderId, res.getRevenue());

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("amount", res.getRevenue());
        result.put("status", "INITIATED");
        result.put("key", "rzp_test_mockkey12345");
        return result;
    }

    /**
     * Verify Razorpay payment, generate one-time exit code, and complete booking.
     */
    public Map<String, Object> verifyRazorpayPayment(String paymentId, String orderId, String signature, Long reservationId) {
        log.info("🔍 Verifying Razorpay payment. Payment ID: {}, Order ID: {}, Signature: {}", paymentId, orderId, signature);
        
        Optional<Reservation> resOpt = reservationRepository.findById(reservationId);
        if (resOpt.isEmpty()) {
            return Map.of("error", "Reservation not found", "status", "FAILED");
        }
        Reservation res = resOpt.get();

        // If already paid
        if ("PAID".equals(res.getPaymentStatus())) {
            return Map.of(
                "status", "PAID",
                "transactionId", paymentId,
                "reservationId", reservationId,
                "amount", res.getRevenue(),
                "receiptNumber", res.getReceiptNumber(),
                "exitCode", res.getExitCode()
            );
        }

        // Verify signature for real orders
        if (razorpayKeySecret != null && !razorpayKeySecret.trim().isEmpty() &&
            signature != null && !signature.trim().isEmpty() && 
            orderId != null && !orderId.startsWith("order_mock")) {
            
            try {
                String generatedSig = calculateHmacSha256(orderId + "|" + paymentId, razorpayKeySecret);
                if (!generatedSig.equals(signature)) {
                    log.error("❌ REAL RAZORPAY SIGNATURE VERIFICATION FAILED. Order ID: {}, Signature: {}, Generated: {}", 
                            orderId, signature, generatedSig);
                    return Map.of("error", "Invalid signature", "status", "FAILED");
                }
                log.info("✅ REAL RAZORPAY SIGNATURE VERIFICATION SUCCESSFUL for Order ID: {}", orderId);
            } catch (Exception e) {
                log.error("Exception during signature verification: ", e);
                return Map.of("error", "Signature verification error: " + e.getMessage(), "status", "FAILED");
            }
        }

        Map<String, Object> txn = pendingTransactions.get(orderId);
        if (txn == null) {
            txn = new HashMap<>();
            txn.put("transactionId", orderId);
            txn.put("reservationId", reservationId);
            txn.put("amount", res.getRevenue());
            txn.put("paymentMethod", "RAZORPAY");
            txn.put("status", "INITIATED");
            pendingTransactions.put(orderId, txn);
        }

        // 1. Mark payment as PAID
        res.setPaymentStatus("PAID");
        res.setPaymentMethod("RAZORPAY");
        
        // Generate a 6-digit one-time exit code
        String exitCode = String.format("%06d", new Random().nextInt(900000) + 100000);
        res.setExitCode(exitCode);
        res.setExited(false);
        reservationRepository.save(res);

        // 2. Generate all 3 receipts (CUSTOMER, ADMIN, LOCK)
        List<Receipt> receipts = receiptService.generateAllReceipts(res);
        for (Receipt r : receipts) {
            r.setPaymentStatus("PAID");
            r.setWebhookVerified(true);
            receiptRepository.save(r);
        }

        Receipt customerReceipt = receipts.stream()
                .filter(r -> "CUSTOMER".equals(r.getReceiptType()))
                .findFirst().orElse(receipts.get(0));

        // 3. Send notifications
        notificationService.sendEmailReceipt(res.getClientEmail(), customerReceipt);
        if (res.getPhoneNumber() != null && !res.getPhoneNumber().isEmpty()) {
            notificationService.sendWhatsAppReceipt(res.getPhoneNumber(), customerReceipt);
        }

        // 4. Update transaction status
        txn.put("status", "COMPLETED");
        txn.put("completedAt", LocalDateTime.now().toString());
        txn.put("razorpayPaymentId", paymentId);
        txn.put("razorpaySignature", signature);

        log.info("✅ RAZORPAY PAYMENT CONFIRMED → Order ID: {}, Res ID: #{}, Exit Code: {}", 
                orderId, reservationId, exitCode);

        return Map.of(
            "status", "PAID",
            "transactionId", paymentId,
            "reservationId", reservationId,
            "amount", res.getRevenue(),
            "receiptNumber", customerReceipt.getReceiptNumber(),
            "exitCode", exitCode
        );
    }

    /**
     * Compute HMAC-SHA256 signature for verification.
     */
    private String calculateHmacSha256(String data, String secret) throws Exception {
        byte[] secretBytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(secretBytes, "HmacSHA256");
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hmacBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Verify one-time exit code and release parking spot.
     */
    public Map<String, Object> verifyExitCode(String exitCode) {
        log.info("🚪 Verifying exit code: {}", exitCode);
        
        if (exitCode == null || exitCode.trim().isEmpty()) {
            return Map.of("error", "Exit code cannot be empty", "success", false);
        }

        // Clean spaces and match case-insensitive (though it's numeric 6 digits)
        String cleanCode = exitCode.trim();

        Optional<Reservation> resOpt = reservationRepository.findByExitCodeAndStatus(cleanCode, "active");
        if (resOpt.isEmpty()) {
            return Map.of("error", "Invalid or inactive exit code", "success", false);
        }

        Reservation res = resOpt.get();
        if (Boolean.TRUE.equals(res.getExited())) {
            return Map.of("error", "Exit code has already been used", "success", false);
        }

        // Mark as exited and complete reservation
        res.setExited(true);
        res.setStatus("completed");
        
        ParkingSpot spot = res.getParkingSpot();
        if (spot != null) {
            spot.setStatus("available");
            spotRepository.save(spot);
        }
        reservationRepository.save(res);

        log.info("🚪 Exit verified and slot #{} released for Reservation #{}", 
                spot != null ? spot.getId() : "N/A", res.getId());

        return Map.of(
            "success", true,
            "message", "Exit code verified. Spot released.",
            "vehicleNumber", res.getVehicleNumber(),
            "vehicleType", res.getVehicleType(),
            "clientName", res.getClientName(),
            "spotId", spot != null ? spot.getId() : "N/A"
        );
    }
}
