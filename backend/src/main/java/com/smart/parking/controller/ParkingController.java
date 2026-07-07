package com.smart.parking.controller;

import com.smart.parking.model.ParkingSpot;
import com.smart.parking.model.Receipt;
import com.smart.parking.model.Reservation;
import com.smart.parking.repository.ParkingSpotRepository;
import com.smart.parking.repository.ReceiptRepository;
import com.smart.parking.repository.ReservationRepository;
import com.smart.parking.service.PaymentService;
import com.smart.parking.service.ReceiptService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ParkingController {

    @Autowired
    private ParkingSpotRepository spotRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private PaymentService paymentService;

    private static final Object bookingLock = new Object();

    // 1. Get all parking spots
    @GetMapping("/parking-spots")
    public List<ParkingSpot> getAllSpots() {
        return spotRepository.findAll();
    }

    // DTO for reservation request
    @Data
    public static class ReservationRequest {
        private String vehicleNumber;
        private Integer duration;
        private String clientName;
        private String clientEmail;
        private String phoneNumber; // WhatsApp number
        private String vehicleType; // "car", "motorcycle", "truck"
        private String apartmentNumber;
        private String userType; // "Resident" or "Visitor"
        private String bookingDate; // yyyy-MM-dd, must be >= tomorrow
        private String paymentMethod; // "UPI", "CARD", "CASH"
    }

    // 2. Reserve a specific spot
    @PostMapping("/reservations/spot/{slotId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> reserveSpecificSpot(
            @PathVariable Long slotId,
            @RequestBody ReservationRequest request) {
        
        synchronized (bookingLock) {
            Optional<ParkingSpot> spotOpt = spotRepository.findByIdForUpdate(slotId);
            if (spotOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Parking spot not found.");
            }

            ParkingSpot spot = spotOpt.get();
            if ("occupied".equalsIgnoreCase(spot.getStatus())) {
                return ResponseEntity.badRequest().body("Parking spot is already occupied.");
            }

            // Validate booking date (must be >= tomorrow)
            LocalDate bookingDate = null;
            if (request.getBookingDate() != null && !request.getBookingDate().isEmpty()) {
                bookingDate = LocalDate.parse(request.getBookingDate());
                if (bookingDate.isBefore(LocalDate.now().plusDays(1))) {
                    return ResponseEntity.badRequest().body("Booking must be at least 1 day in advance. Earliest: " + LocalDate.now().plusDays(1));
                }
            } else {
                bookingDate = LocalDate.now().plusDays(1); // default to tomorrow
            }

            // Create reservation
            Reservation reservation = new Reservation();
            reservation.setParkingSpot(spot);
            reservation.setVehicleNumber(request.getVehicleNumber() != null ? request.getVehicleNumber() : "N/A");
            reservation.setVehicleType(request.getVehicleType() != null ? request.getVehicleType().toLowerCase() : "car");
            reservation.setClientName(request.getClientName() != null ? request.getClientName() : "Guest Driver");
            reservation.setClientEmail(request.getClientEmail() != null ? request.getClientEmail() : "guest@example.com");
            reservation.setPhoneNumber(request.getPhoneNumber());
            reservation.setApartmentNumber(request.getApartmentNumber() != null ? request.getApartmentNumber() : "N/A");
            reservation.setUserType(request.getUserType() != null ? request.getUserType() : "Resident");
            reservation.setStartTime(LocalDateTime.now());
            reservation.setBookingDate(bookingDate);
            
            int duration = request.getDuration() != null ? request.getDuration() : 1;
            reservation.setDuration(duration);
            
            // Calculate Revenue in Rupees (₹)
            double rate = "vip".equalsIgnoreCase(spot.getType()) ? 150.0 : 50.0;
            if ("motorcycle".equalsIgnoreCase(reservation.getVehicleType()) && !"vip".equalsIgnoreCase(spot.getType())) {
                rate = 20.0; // cheaper rate for motorcycles in regular spots
            }
            reservation.setRevenue(rate * duration);
            reservation.setStatus("active");
            reservation.setPaymentStatus("PENDING");
            reservation.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "UPI");

            // Save Reservation & Update Spot Status
            spot.setStatus("occupied");
            spotRepository.save(spot);
            Reservation savedReservation = reservationRepository.save(reservation);

            return ResponseEntity.ok(savedReservation);
        }
    }

    // 3. Create a general reservation (finds first available spot)
    @PostMapping("/reservations")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> createGeneralReservation(@RequestBody ReservationRequest request) {
        synchronized (bookingLock) {
            List<ParkingSpot> availableSpots = spotRepository.findByStatus("available");
            
            if (availableSpots.isEmpty()) {
                return ResponseEntity.badRequest().body("No parking spots are currently available.");
            }

            // Preference logic:
            // Try to match regular vehicle to regular spot, vip vehicle (or if only vip left) to vip spot
            ParkingSpot selectedSpot = null;
            String vType = request.getVehicleType() != null ? request.getVehicleType().toLowerCase() : "car";
            
            if ("motorcycle".equals(vType) || "car".equals(vType)) {
                // Prefer regular spots
                selectedSpot = availableSpots.stream()
                        .filter(s -> "regular".equalsIgnoreCase(s.getType()))
                        .findFirst()
                        .orElse(availableSpots.get(0)); // fallback to whatever is available
            } else {
                // For trucks or generic requests, take any available spot
                selectedSpot = availableSpots.get(0);
            }

            // Validate booking date
            LocalDate bookingDate = null;
            if (request.getBookingDate() != null && !request.getBookingDate().isEmpty()) {
                bookingDate = LocalDate.parse(request.getBookingDate());
                if (bookingDate.isBefore(LocalDate.now().plusDays(1))) {
                    return ResponseEntity.badRequest().body("Booking must be at least 1 day in advance. Earliest: " + LocalDate.now().plusDays(1));
                }
            } else {
                bookingDate = LocalDate.now().plusDays(1);
            }

            // Create reservation
            Reservation reservation = new Reservation();
            reservation.setParkingSpot(selectedSpot);
            reservation.setVehicleNumber(request.getVehicleNumber() != null ? request.getVehicleNumber() : "N/A");
            reservation.setVehicleType(vType);
            reservation.setClientName(request.getClientName() != null ? request.getClientName() : "Guest Driver");
            reservation.setClientEmail(request.getClientEmail() != null ? request.getClientEmail() : "guest@example.com");
            reservation.setPhoneNumber(request.getPhoneNumber());
            reservation.setApartmentNumber(request.getApartmentNumber() != null ? request.getApartmentNumber() : "N/A");
            reservation.setUserType(request.getUserType() != null ? request.getUserType() : "Resident");
            reservation.setStartTime(LocalDateTime.now());
            reservation.setBookingDate(bookingDate);
            
            int duration = request.getDuration() != null ? request.getDuration() : 1;
            reservation.setDuration(duration);
            
            // Calculate Revenue in Rupees (₹)
            double rate = "vip".equalsIgnoreCase(selectedSpot.getType()) ? 150.0 : 50.0;
            if ("motorcycle".equalsIgnoreCase(vType) && !"vip".equalsIgnoreCase(selectedSpot.getType())) {
                rate = 20.0;
            }
            reservation.setRevenue(rate * duration);
            reservation.setStatus("active");
            reservation.setPaymentStatus("PENDING");
            reservation.setPaymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "UPI");

            // Save
            selectedSpot.setStatus("occupied");
            spotRepository.save(selectedSpot);
            Reservation savedReservation = reservationRepository.save(reservation);

            return ResponseEntity.ok(savedReservation);
        }
    }

    // 4. Get all reservations
    @GetMapping("/reservations")
    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    // 4b. Update reservation phone number
    @PutMapping("/reservations/{id}/phone")
    public ResponseEntity<?> updateReservationPhone(@PathVariable Long id, @RequestParam String phone) {
        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (resOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Reservation res = resOpt.get();
        res.setPhoneNumber(phone);
        reservationRepository.save(res);
        return ResponseEntity.ok(Map.of("message", "Phone number updated successfully"));
    }

    // 5. Complete / cancel a reservation (release parking spot)
    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<?> completeReservation(@PathVariable Long id) {
        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (resOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Reservation reservation = resOpt.get();
        if ("active".equalsIgnoreCase(reservation.getStatus())) {
            reservation.setStatus("completed");
            
            ParkingSpot spot = reservation.getParkingSpot();
            if (spot != null) {
                // Free the spot
                spot.setStatus("available");
                spotRepository.save(spot);
            }
            reservationRepository.save(reservation);
            return ResponseEntity.ok().body("Reservation completed and spot released.");
        } else {
            return ResponseEntity.badRequest().body("Reservation is already completed or cancelled.");
        }
    }

    // 5b. Completely delete a reservation record (active or completed)
    @DeleteMapping("/reservations/{id}/delete")
    public ResponseEntity<?> deleteReservation(@PathVariable Long id) {
        Optional<Reservation> resOpt = reservationRepository.findById(id);
        if (resOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Reservation reservation = resOpt.get();
        if ("active".equalsIgnoreCase(reservation.getStatus())) {
            ParkingSpot spot = reservation.getParkingSpot();
            if (spot != null) {
                spot.setStatus("available");
                spotRepository.save(spot);
            }
        }
        reservationRepository.delete(reservation);
        return ResponseEntity.ok().body("Reservation deleted successfully.");
    }

    // 5c. Admin reset/re-seed database
    @PostMapping("/admin/reset")
    public ResponseEntity<?> resetDatabase() {
        receiptRepository.deleteAll();
        reservationRepository.deleteAll();
        spotRepository.deleteAll();

        // Create spots 1 to 10
        ParkingSpot s1 = new ParkingSpot(1L, "regular", "available");
        ParkingSpot s2 = new ParkingSpot(2L, "vip", "available");
        ParkingSpot s3 = new ParkingSpot(3L, "regular", "occupied");
        ParkingSpot s4 = new ParkingSpot(4L, "regular", "available");
        ParkingSpot s5 = new ParkingSpot(5L, "vip", "available");
        ParkingSpot s6 = new ParkingSpot(6L, "regular", "occupied");
        ParkingSpot s7 = new ParkingSpot(7L, "regular", "available");
        ParkingSpot s8 = new ParkingSpot(8L, "regular", "available");
        ParkingSpot s9 = new ParkingSpot(9L, "vip", "occupied");
        ParkingSpot s10 = new ParkingSpot(10L, "regular", "available");

        spotRepository.save(s1);
        spotRepository.save(s2);
        spotRepository.save(s3);
        spotRepository.save(s4);
        spotRepository.save(s5);
        spotRepository.save(s6);
        spotRepository.save(s7);
        spotRepository.save(s8);
        spotRepository.save(s9);
        spotRepository.save(s10);

        // Seed reservations for occupied slots
        Reservation r3 = new Reservation();
        r3.setParkingSpot(s3);
        r3.setVehicleNumber("AP-28-AB-1234");
        r3.setVehicleType("car");
        r3.setClientName("Eslavath Narasimha");
        r3.setClientEmail("narasimha@example.com");
        r3.setApartmentNumber("402B");
        r3.setUserType("Resident");
        r3.setStartTime(LocalDateTime.now().minusHours(2));
        r3.setDuration(3);
        r3.setRevenue(150.0); // 3 hours @ ₹50/hr
        r3.setStatus("active");
        reservationRepository.save(r3);

        Reservation r6 = new Reservation();
        r6.setParkingSpot(s6);
        r6.setVehicleNumber("AP-09-CD-5678");
        r6.setVehicleType("motorcycle");
        r6.setClientName("Suresh Kumar");
        r6.setClientEmail("suresh@example.com");
        r6.setApartmentNumber("101A");
        r6.setUserType("Visitor");
        r6.setStartTime(LocalDateTime.now().minusHours(1));
        r6.setDuration(2);
        r6.setRevenue(40.0); // 2 hours @ ₹20/hr
        r6.setStatus("active");
        reservationRepository.save(r6);

        Reservation r9 = new Reservation();
        r9.setParkingSpot(s9);
        r9.setVehicleNumber("AP-11-EF-9012");
        r9.setVehicleType("truck");
        r9.setClientName("Ramesh Naik");
        r9.setClientEmail("ramesh@example.com");
        r9.setApartmentNumber("305C");
        r9.setUserType("Resident");
        r9.setStartTime(LocalDateTime.now().minusHours(3));
        r9.setDuration(4);
        r9.setRevenue(600.0); // 4 hours @ ₹150/hr (VIP)
        r9.setStatus("active");
        reservationRepository.save(r9);

        // Seed completed historical reservations
        Reservation rc1 = new Reservation();
        rc1.setParkingSpot(s1);
        rc1.setVehicleNumber("AP-15-GH-1111");
        rc1.setVehicleType("car");
        rc1.setClientName("Anjali Sen");
        rc1.setClientEmail("anjali@example.com");
        rc1.setApartmentNumber("204F");
        rc1.setUserType("Visitor");
        rc1.setStartTime(LocalDateTime.now().minusHours(8));
        rc1.setDuration(2);
        rc1.setRevenue(100.0); // 2 hours @ ₹50/hr
        rc1.setStatus("completed");
        reservationRepository.save(rc1);

        Reservation rc2 = new Reservation();
        rc2.setParkingSpot(s2);
        rc2.setVehicleNumber("AP-22-IJ-2222");
        rc2.setVehicleType("car");
        rc2.setClientName("David Miller");
        rc2.setClientEmail("david@example.com");
        rc2.setApartmentNumber("501D");
        rc2.setUserType("Resident");
        rc2.setStartTime(LocalDateTime.now().minusHours(6));
        rc2.setDuration(3);
        rc2.setRevenue(450.0); // 3 hours @ ₹150/hr (VIP)
        rc2.setStatus("completed");
        reservationRepository.save(rc2);

        return ResponseEntity.ok().body("Database reset successfully.");
    }

    // DTO for Dashboard Stats
    @Data
    public static class DashboardStats {
        private String occupancyRate;
        private String peakHour;
        private String averageDuration;
        private String dailyRevenue;
        private List<Integer> hourlyTrend;
        private List<Long> vehicleTypeDistribution; // [cars, bikes, trucks]
        private List<Map<String, Object>> parkingDetails;
        
        // SUM Control Panel Additions
        private String totalRevenue;
        private String totalHours;
        private Long totalBookings;
        private List<Long> residentVsVisitor; // [Residents, Visitors]
    }

    // 6. Get dashboard stats
    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStats> getDashboardStats() {
        List<ParkingSpot> spots = spotRepository.findAll();
        List<Reservation> reservations = reservationRepository.findAll();

        DashboardStats stats = new DashboardStats();

        // 1. Occupancy Rate
        long totalSpots = spots.size();
        long occupiedSpots = spots.stream().filter(s -> "occupied".equalsIgnoreCase(s.getStatus())).count();
        double occupancyPct = totalSpots > 0 ? ((double) occupiedSpots / totalSpots) * 100 : 0.0;
        stats.setOccupancyRate(String.format("%.0f%%", occupancyPct));

        // 2. Peak Hour calculation
        Map<Integer, Long> hourCounts = reservations.stream()
                .filter(r -> r.getStartTime() != null)
                .collect(Collectors.groupingBy(r -> r.getStartTime().getHour(), Collectors.counting()));
        
        int peakHourVal = -1;
        long maxCount = 0;
        for (Map.Entry<Integer, Long> entry : hourCounts.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                peakHourVal = entry.getKey();
            }
        }
        if (peakHourVal != -1) {
            String peakHourStr;
            if (peakHourVal == 0) peakHourStr = "12 AM";
            else if (peakHourVal == 12) peakHourStr = "12 PM";
            else if (peakHourVal > 12) peakHourStr = (peakHourVal - 12) + " PM";
            else peakHourStr = peakHourVal + " AM";
            stats.setPeakHour(peakHourStr);
        } else {
            stats.setPeakHour("N/A");
        }

        // 3. Average Stay Duration
        double avgDur = reservations.stream()
                .mapToInt(Reservation::getDuration)
                .average()
                .orElse(0.0);
        stats.setAverageDuration(String.format("%.1fh", avgDur));

        // 4. Daily Revenue
        double todayRevenue = reservations.stream()
                .filter(r -> r.getStartTime() != null && r.getStartTime().toLocalDate().equals(LocalDateTime.now().toLocalDate()))
                .mapToDouble(Reservation::getRevenue)
                .sum();
        stats.setDailyRevenue(String.format("₹%.0f", todayRevenue));

        // 5. Hourly Trend
        List<Integer> trend = Arrays.asList(15, 25, 30, 45, 50, 35, 40, 65, 80, (int) occupancyPct);
        stats.setHourlyTrend(trend);

        // 6. Vehicle Type Distribution [Cars, Bikes, Trucks]
        long cars = reservations.stream().filter(r -> "active".equalsIgnoreCase(r.getStatus()) && "car".equalsIgnoreCase(r.getVehicleType())).count();
        long bikes = reservations.stream().filter(r -> "active".equalsIgnoreCase(r.getStatus()) && "motorcycle".equalsIgnoreCase(r.getVehicleType())).count();
        long trucks = reservations.stream().filter(r -> "active".equalsIgnoreCase(r.getStatus()) && "truck".equalsIgnoreCase(r.getVehicleType())).count();
        
        if (cars == 0 && bikes == 0 && trucks == 0) {
            cars = reservations.stream().filter(r -> "car".equalsIgnoreCase(r.getVehicleType())).count();
            bikes = reservations.stream().filter(r -> "motorcycle".equalsIgnoreCase(r.getVehicleType())).count();
            trucks = reservations.stream().filter(r -> "truck".equalsIgnoreCase(r.getVehicleType())).count();
        }
        stats.setVehicleTypeDistribution(Arrays.asList(cars, bikes, trucks));

        // 7. SUM Control Panel calculations
        double allTimeRevenue = reservations.stream().mapToDouble(Reservation::getRevenue).sum();
        stats.setTotalRevenue(String.format("₹%.0f", allTimeRevenue));

        double totalHrs = reservations.stream().mapToDouble(Reservation::getDuration).sum();
        stats.setTotalHours(String.format("%.0fh", totalHrs));

        stats.setTotalBookings((long) reservations.size());

        long residents = reservations.stream().filter(r -> "resident".equalsIgnoreCase(r.getUserType())).count();
        long visitors = reservations.stream().filter(r -> "visitor".equalsIgnoreCase(r.getUserType())).count();
        if (residents == 0 && visitors == 0) {
            residents = 3;
            visitors = 2;
        }
        stats.setResidentVsVisitor(Arrays.asList(residents, visitors));

        // 8. Parking Spot Details List
        List<Map<String, Object>> details = new ArrayList<>();
        List<ParkingSpot> sortedSpots = spots.stream()
                .sorted(Comparator.comparing(ParkingSpot::getId))
                .collect(Collectors.toList());

        for (ParkingSpot s : sortedSpots) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", s.getId());
            detail.put("type", s.getType().substring(0, 1).toUpperCase() + s.getType().substring(1));
            detail.put("status", s.getStatus().substring(0, 1).toUpperCase() + s.getStatus().substring(1));
            
            List<Reservation> activeResList = reservationRepository.findByParkingSpotIdAndStatus(s.getId(), "active");
            if (!activeResList.isEmpty()) {
                Reservation activeRes = activeResList.get(0);
                detail.put("duration", activeRes.getDuration() + "h");
                String vt = activeRes.getVehicleType();
                if (vt != null) {
                    vt = vt.substring(0, 1).toUpperCase() + vt.substring(1);
                } else {
                    vt = "Car";
                }
                detail.put("vehicle", vt + " (" + activeRes.getVehicleNumber() + ")");
                detail.put("revenue", String.format("₹%.0f", activeRes.getRevenue()));
                detail.put("reservationId", activeRes.getId());
                detail.put("apartmentNumber", activeRes.getApartmentNumber() != null ? activeRes.getApartmentNumber() : "N/A");
                detail.put("userType", activeRes.getUserType() != null ? activeRes.getUserType() : "Resident");
                detail.put("clientName", activeRes.getClientName() != null ? activeRes.getClientName() : "N/A");
                detail.put("clientEmail", activeRes.getClientEmail() != null ? activeRes.getClientEmail() : "N/A");
            } else {
                detail.put("duration", "0h");
                detail.put("vehicle", "N/A");
                detail.put("revenue", "₹0");
                detail.put("reservationId", null);
                detail.put("apartmentNumber", "N/A");
                detail.put("userType", "N/A");
                detail.put("clientName", "N/A");
                detail.put("clientEmail", "N/A");
            }
            details.add(detail);
        }
        stats.setParkingDetails(details);

        return ResponseEntity.ok(stats);
    }

    // ====== PAYMENT & RECEIPT ENDPOINTS ======

    public static class PaymentRequest {
        private Long reservationId;
        private String paymentMethod; // "UPI", "CARD", "CASH"

        public Long getReservationId() {
            return this.reservationId;
        }
        public void setReservationId(Long reservationId) {
            this.reservationId = reservationId;
        }
        public String getPaymentMethod() {
            return this.paymentMethod;
        }
        public void setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
        }
    }

    // 9. Initiate payment
    @PostMapping("/payment/initiate")
    public ResponseEntity<?> initiatePayment(@RequestBody PaymentRequest request) {
        Map<String, Object> result = paymentService.initiatePayment(
                request.getReservationId(), request.getPaymentMethod());
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // 10. Webhook callback (payment gateway calls this)
    @PostMapping("/webhook/payment")
    public ResponseEntity<?> webhookPayment(@RequestBody Map<String, String> payload) {
        String txnId = payload.get("transactionId");
        if (txnId == null || txnId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing transactionId"));
        }
        String mockTxnId = payload.get("mockTxnId");
        String note = payload.get("note");
        if (note == null) {
            note = payload.get("message");
        }
        Map<String, Object> result = paymentService.processWebhookCallback(txnId, mockTxnId, note);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/payment/verify-cashfree")
    public ResponseEntity<?> verifyCashfree(@RequestBody Map<String, String> payload) {
        String orderId = payload.get("orderId");
        if (orderId == null || orderId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing orderId"));
        }
        Map<String, Object> result = paymentService.verifyCashfreePayment(orderId);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/payment/communipay/process")
    public ResponseEntity<?> processCommuniPay(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        if (token == null || token.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing token"));
        }
        Map<String, Object> result = paymentService.processCommuniPayPayment(token);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/payment/status/{transactionId}")
    public ResponseEntity<?> getPaymentStatus(@PathVariable String transactionId) {
        Map<String, Object> result = paymentService.getTransactionStatus(transactionId);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    public static class VerifyManualRequest {
        private Long reservationId;
        private String vehicleNumber;
        private String secretCode;

        public Long getReservationId() {
            return this.reservationId;
        }
        public void setReservationId(Long reservationId) {
            this.reservationId = reservationId;
        }
        public String getVehicleNumber() {
            return this.vehicleNumber;
        }
        public void setVehicleNumber(String vehicleNumber) {
            this.vehicleNumber = vehicleNumber;
        }
        public String getSecretCode() {
            return this.secretCode;
        }
        public void setSecretCode(String secretCode) {
            this.secretCode = secretCode;
        }
    }

    // 11a. Verify details only (vehicle + secret code match, no payment completion)
    @PostMapping("/payment/verify-details")
    public ResponseEntity<?> verifyDetailsOnly(@RequestBody VerifyManualRequest request) {
        Map<String, Object> result = paymentService.verifyDetailsOnly(
                request.getReservationId(), request.getVehicleNumber(), request.getSecretCode());
        boolean verified = (boolean) result.getOrDefault("verified", false);
        if (!verified) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // 11b. Manual validation endpoint (verifies + completes payment)
    @PostMapping("/payment/verify-manual")
    public ResponseEntity<?> verifyManual(@RequestBody VerifyManualRequest request) {
        Map<String, Object> result = paymentService.verifyManual(
                request.getReservationId(), request.getVehicleNumber(), request.getSecretCode());
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // 12. Direct payment (initiates + confirms in one call for simplicity)
    @PostMapping("/payment/confirm")
    public ResponseEntity<?> confirmPayment(@RequestBody PaymentRequest request) {
        Map<String, Object> result = paymentService.confirmPaymentDirect(
                request.getReservationId(), request.getPaymentMethod());
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // 12. Get receipt HTML (printable)
    @GetMapping(value = "/receipts/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> getReceiptHtml(@PathVariable Long id) {
        Receipt receipt = receiptService.getById(id);
        if (receipt == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(receiptService.generateReceiptHtml(receipt));
    }

    // 13. Get receipt JSON
    @GetMapping("/receipts/{id}")
    public ResponseEntity<?> getReceipt(@PathVariable Long id) {
        Receipt receipt = receiptService.getById(id);
        if (receipt == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(receipt);
    }

    // 14. Get all receipts for a reservation
    @GetMapping("/receipts/reservation/{resId}")
    public ResponseEntity<?> getReceiptsByReservation(@PathVariable Long resId) {
        List<Receipt> receipts = receiptService.getByReservationId(resId);
        return ResponseEntity.ok(receipts);
    }

    // 15. Get all receipts
    @GetMapping("/receipts")
    public List<Receipt> getAllReceipts() {
        return receiptRepository.findAll();
    }

    // 16. Initiate Razorpay payment
    @PostMapping("/payment/razorpay/initiate")
    public ResponseEntity<?> initiateRazorpayPayment(@RequestBody PaymentRequest request) {
        Map<String, Object> result = paymentService.initiateRazorpayPayment(request.getReservationId());
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // 17. Verify Razorpay payment
    @PostMapping("/payment/razorpay/verify")
    public ResponseEntity<?> verifyRazorpayPayment(@RequestBody Map<String, String> payload) {
        String paymentId = payload.get("razorpay_payment_id");
        String orderId = payload.get("razorpay_order_id");
        String signature = payload.get("razorpay_signature");
        String resIdStr = payload.get("reservationId");
        
        if (paymentId == null || orderId == null || resIdStr == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required parameters"));
        }

        Long reservationId = Long.parseLong(resIdStr);
        Map<String, Object> result = paymentService.verifyRazorpayPayment(paymentId, orderId, signature, reservationId);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // 18. Verify Exit Code at the gate
    @PostMapping("/exit/verify")
    public ResponseEntity<?> verifyExitCode(@RequestBody Map<String, String> payload) {
        String exitCode = payload.get("exitCode");
        if (exitCode == null || exitCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Exit code is required"));
        }
        Map<String, Object> result = paymentService.verifyExitCode(exitCode);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
