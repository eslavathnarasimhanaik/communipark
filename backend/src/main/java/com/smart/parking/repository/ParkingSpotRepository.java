package com.smart.parking.repository;

import com.smart.parking.model.ParkingSpot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {
    List<ParkingSpot> findByStatus(String status);
    List<ParkingSpot> findByType(String type);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ParkingSpot p where p.id = :id")
    Optional<ParkingSpot> findByIdForUpdate(Long id);
}
