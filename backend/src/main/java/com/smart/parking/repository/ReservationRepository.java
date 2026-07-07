package com.smart.parking.repository;

import com.smart.parking.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByStatus(String status);
    List<Reservation> findByParkingSpotIdAndStatus(Long parkingSpotId, String status);
    java.util.Optional<Reservation> findByExitCodeAndStatus(String exitCode, String status);
}
