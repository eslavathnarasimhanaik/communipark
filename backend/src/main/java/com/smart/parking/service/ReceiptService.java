package com.smart.parking.service;

import com.smart.parking.model.Receipt;
import com.smart.parking.model.Reservation;
import com.smart.parking.repository.ReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ReceiptService {

    @Autowired
    private ReceiptRepository receiptRepository;

    private String generateReceiptNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return "RCP-" + timestamp + "-" + String.format("%03d", new java.util.Random().nextInt(1000));
    }

    /**
     * Generate all 3 receipt types for a reservation:
     * CUSTOMER - for the driver/resident
     * ADMIN - for the parking admin
     * LOCK - finalized, immutable copy
     */
    public List<Receipt> generateAllReceipts(Reservation res) {
        Receipt customer = createReceipt(res, "CUSTOMER");
        Receipt admin = createReceipt(res, "ADMIN");
        Receipt lock = createReceipt(res, "LOCK");
        lock.setLockedAt(LocalDateTime.now()); // Lock immediately

        receiptRepository.save(customer);
        receiptRepository.save(admin);
        receiptRepository.save(lock);

        // Update reservation with receipt number (use customer receipt number)
        res.setReceiptNumber(customer.getReceiptNumber());

        return List.of(customer, admin, lock);
    }

    private Receipt createReceipt(Reservation res, String type) {
        Receipt r = new Receipt();
        r.setReceiptNumber(generateReceiptNumber());
        r.setReservationId(res.getId());
        r.setReceiptType(type);
        r.setCustomerName(res.getClientName());
        r.setCustomerEmail(res.getClientEmail());
        r.setCustomerPhone(res.getPhoneNumber());
        r.setSpotId(res.getParkingSpot() != null ? res.getParkingSpot().getId() : null);
        r.setVehicleNumber(res.getVehicleNumber());
        r.setVehicleType(res.getVehicleType());
        r.setDuration(res.getDuration());
        r.setAmount(res.getRevenue());
        r.setPaymentStatus(res.getPaymentStatus() != null ? res.getPaymentStatus() : "PENDING");
        r.setPaymentMethod(res.getPaymentMethod() != null ? res.getPaymentMethod() : "UPI");
        r.setWebhookVerified(false);
        r.setIssuedAt(LocalDateTime.now());
        r.setLockedAt(null);
        r.setApartmentNumber(res.getApartmentNumber());
        r.setUserType(res.getUserType());
        return r;
    }

    public Receipt lockReceipt(Long receiptId) {
        Receipt r = receiptRepository.findById(receiptId).orElseThrow();
        r.setLockedAt(LocalDateTime.now());
        return receiptRepository.save(r);
    }

    public String generateReceiptHtml(Receipt r) {
        String lockBadge = r.getLockedAt() != null 
            ? "<div style='position:absolute;top:20px;right:20px;background:#f59e0b;color:#000;padding:4px 12px;border-radius:4px;font-weight:700;font-size:0.8rem;'>🔒 LOCKED</div>" 
            : "";
        String paymentBadge = "PAID".equals(r.getPaymentStatus()) 
            ? "<span style='color:#10b981;font-weight:700;'>✅ PAID</span>" 
            : "<span style='color:#f59e0b;font-weight:700;'>⏳ PENDING</span>";
        String webhookBadge = Boolean.TRUE.equals(r.getWebhookVerified()) 
            ? "<span style='color:#10b981;'>✅ Verified</span>" 
            : "<span style='color:#ef4444;'>❌ Not Verified</span>";

        return "<!DOCTYPE html><html><head><title>CommuniPark Receipt</title>"
            + "<style>"
            + "body{font-family:'Segoe UI',sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:2rem;}"
            + ".receipt{max-width:500px;margin:0 auto;background:#1e293b;border:1px solid #334155;border-radius:12px;padding:2rem;position:relative;}"
            + ".header{text-align:center;border-bottom:2px dashed #475569;padding-bottom:1rem;margin-bottom:1rem;}"
            + ".logo{font-size:1.5rem;font-weight:800;background:linear-gradient(135deg,#fff,#818cf8);-webkit-background-clip:text;-webkit-text-fill-color:transparent;}"
            + ".receipt-num{color:#818cf8;font-size:0.85rem;font-weight:600;margin-top:0.5rem;}"
            + ".row{display:flex;justify-content:space-between;padding:0.5rem 0;border-bottom:1px solid #1e293b;}"
            + ".label{color:#94a3b8;font-size:0.85rem;}"
            + ".value{font-weight:600;color:#f1f5f9;}"
            + ".total{font-size:1.25rem;color:#10b981;font-weight:800;text-align:center;padding:1rem;border-top:2px dashed #475569;margin-top:1rem;}"
            + ".type-badge{display:inline-block;background:#818cf8;color:#fff;padding:2px 8px;border-radius:4px;font-size:0.75rem;font-weight:700;}"
            + ".footer{text-align:center;margin-top:1.5rem;color:#64748b;font-size:0.8rem;}"
            + ".print-btn{display:block;margin:1.5rem auto 0;background:linear-gradient(135deg,#6366f1,#818cf8);color:#fff;border:none;padding:12px 2rem;border-radius:8px;cursor:pointer;font-size:0.95rem;font-weight:600;}"
            + "@media print{.print-btn{display:none !important;}body{background:#fff;color:#000;}.receipt{background:#fff;border-color:#ccc;}.label{color:#666;}.value{color:#000;}.total{color:#059669;}.footer{color:#999;}}"
            + "</style></head><body>"
            + "<div class='receipt'>" + lockBadge
            + "<div class='header'>"
            + "<div class='logo'>🅿️ CommuniPark</div>"
            + "<div style='color:#94a3b8;font-size:0.85rem;'>Apartment & Community Parking</div>"
            + "<div class='receipt-num'>" + r.getReceiptNumber() + "</div>"
            + "<span class='type-badge'>" + r.getReceiptType() + " RECEIPT</span>"
            + "</div>"
            + "<div class='row'><span class='label'>Customer</span><span class='value'>" + r.getCustomerName() + "</span></div>"
            + "<div class='row'><span class='label'>Email</span><span class='value'>" + r.getCustomerEmail() + "</span></div>"
            + "<div class='row'><span class='label'>Phone (WhatsApp)</span><span class='value'>" + (r.getCustomerPhone() != null ? r.getCustomerPhone() : "N/A") + "</span></div>"
            + "<div class='row'><span class='label'>Apartment</span><span class='value'>Unit " + (r.getApartmentNumber() != null ? r.getApartmentNumber() : "N/A") + "</span></div>"
            + "<div class='row'><span class='label'>User Type</span><span class='value'>" + (r.getUserType() != null ? r.getUserType() : "Resident") + "</span></div>"
            + "<div class='row'><span class='label'>Parking Spot</span><span class='value'>Slot #" + r.getSpotId() + "</span></div>"
            + "<div class='row'><span class='label'>Vehicle</span><span class='value'>" + r.getVehicleType().toUpperCase() + " (" + r.getVehicleNumber() + ")</span></div>"
            + "<div class='row'><span class='label'>Duration</span><span class='value'>" + r.getDuration() + " Hours</span></div>"
            + "<div class='row'><span class='label'>Payment Method</span><span class='value'>" + (r.getPaymentMethod() != null ? r.getPaymentMethod() : "UPI") + "</span></div>"
            + "<div class='row'><span class='label'>Payment Status</span><span class='value'>" + paymentBadge + "</span></div>"
            + "<div class='row'><span class='label'>Webhook</span><span class='value'>" + webhookBadge + "</span></div>"
            + "<div class='row'><span class='label'>Issued</span><span class='value'>" + r.getIssuedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")) + "</span></div>"
            + "<div class='total'>Total: ₹" + String.format("%.0f", r.getAmount()) + "</div>"
            + "<div class='footer'>Thank you for using CommuniPark!<br/>This is a computer-generated receipt.</div>"
            + "<button class='print-btn' onclick='window.print()'>🖨️ Print Receipt</button>"
            + "</div></body></html>";
    }

    public List<Receipt> getByReservationId(Long resId) {
        return receiptRepository.findByReservationId(resId);
    }

    public Receipt getById(Long id) {
        return receiptRepository.findById(id).orElse(null);
    }
}
