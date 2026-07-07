package com.smart.parking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "parking_spot_id")
    private ParkingSpot parkingSpot;

    private String vehicleNumber;
    private String vehicleType; // "car", "motorcycle", "truck"
    private String clientName;
    private String clientEmail;
    private String phoneNumber; // WhatsApp number
    private String apartmentNumber; // "402B", "101A", etc.
    private String userType; // "Resident" or "Visitor"
    
    private LocalDateTime startTime;
    private LocalDate bookingDate; // must be >= tomorrow
    private Integer duration; // in hours
    private Double revenue; // in ₹
    private String status; // "active", "completed", "cancelled"

    private String paymentStatus; // "PENDING", "PAID", "FAILED"
    private String paymentMethod; // "UPI", "CARD", "CASH"
    private String receiptNumber; // links to Receipt entity

    private String exitCode;
    private Boolean exited = false;
}
