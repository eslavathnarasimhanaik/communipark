package com.smart.parking.service;

import com.smart.parking.model.Receipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Simulate sending email receipt.
     * In production, integrate with SendGrid / AWS SES / SMTP.
     */
    public Map<String, Object> sendEmailReceipt(String email, Receipt receipt) {
        log.info("📧 EMAIL SENT → To: {}, Receipt: {}, Amount: ₹{}", 
                email, receipt.getReceiptNumber(), String.format("%.0f", receipt.getAmount()));
        
        Map<String, Object> result = new HashMap<>();
        result.put("channel", "EMAIL");
        result.put("to", email);
        result.put("receiptNumber", receipt.getReceiptNumber());
        result.put("status", "DELIVERED");
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("message", "Receipt " + receipt.getReceiptNumber() + " sent to " + email);
        return result;
    }

    /**
     * Simulate sending WhatsApp receipt.
     * In production, integrate with WhatsApp Business API / Twilio.
     */
    public Map<String, Object> sendWhatsAppReceipt(String phone, Receipt receipt) {
        log.info("📱 WHATSAPP SENT → To: {}, Receipt: {}, Amount: ₹{}", 
                phone, receipt.getReceiptNumber(), String.format("%.0f", receipt.getAmount()));
        
        Map<String, Object> result = new HashMap<>();
        result.put("channel", "WHATSAPP");
        result.put("to", phone);
        result.put("receiptNumber", receipt.getReceiptNumber());
        result.put("status", "DELIVERED");
        result.put("timestamp", LocalDateTime.now().toString());
        result.put("message", "Receipt " + receipt.getReceiptNumber() + " sent via WhatsApp to " + phone);
        return result;
    }
}
