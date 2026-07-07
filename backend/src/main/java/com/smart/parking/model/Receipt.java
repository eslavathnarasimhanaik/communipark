package com.smart.parking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "receipts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Receipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String receiptNumber; // e.g. "RCP-20250629-00001"

    private Long reservationId;
    private String receiptType; // "CUSTOMER", "ADMIN", "LOCK"

    private String customerName;
    private String customerEmail;
    private String customerPhone; // WhatsApp number

    private Long spotId;
    private String vehicleNumber;
    private String vehicleType;
    private Integer duration; // hours
    private Double amount; // ₹

    private String paymentStatus; // "PENDING", "PAID", "FAILED"
    private String paymentMethod; // "UPI", "CARD", "CASH"
    private Boolean webhookVerified; // true after webhook confirms

    private LocalDateTime issuedAt;
    private LocalDateTime lockedAt; // null until locked

    private String apartmentNumber;
    private String userType; // Resident / Visitor
}
