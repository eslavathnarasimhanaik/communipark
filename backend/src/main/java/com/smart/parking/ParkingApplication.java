package com.smart.parking;

import com.smart.parking.model.ParkingSpot;
import com.smart.parking.model.Reservation;
import com.smart.parking.repository.ParkingSpotRepository;
import com.smart.parking.repository.ReservationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

@SpringBootApplication
public class ParkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDatabase(
            ParkingSpotRepository spotRepository,
            ReservationRepository reservationRepository) {
        return args -> {
            if (spotRepository.count() == 0) {
                System.out.println("Seeding database with parking spots and sample reservations...");

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
                // Spot 3: 3 hours, Car (₹50/hr -> ₹150)
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
                r3.setRevenue(150.0);
                r3.setStatus("active");
                reservationRepository.save(r3);

                // Spot 6: 2 hours, Motorcycle (₹20/hr -> ₹40)
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
                r6.setRevenue(40.0); // ₹20/hr for motorcycle
                r6.setStatus("active");
                reservationRepository.save(r6);

                // Spot 9: VIP, 4 hours, Truck (₹150/hr -> ₹600)
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
                r9.setRevenue(600.0); // ₹150/hr for VIP
                r9.setStatus("active");
                reservationRepository.save(r9);

                // Let's also add some completed historical reservations so charts look nice!
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
                rc1.setRevenue(100.0); // ₹50/hr
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
                rc2.setRevenue(450.0); // VIP spot (₹150/hr)
                rc2.setStatus("completed");
                reservationRepository.save(rc2);

                System.out.println("Seeding completed successfully.");
            }
        };
    }
}
