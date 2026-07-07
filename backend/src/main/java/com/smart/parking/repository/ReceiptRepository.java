package com.smart.parking.repository;

import com.smart.parking.model.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    List<Receipt> findByReservationId(Long reservationId);
    Optional<Receipt> findByReceiptNumber(String receiptNumber);
    List<Receipt> findByCustomerEmail(String email);
    List<Receipt> findByReceiptType(String receiptType);
}
