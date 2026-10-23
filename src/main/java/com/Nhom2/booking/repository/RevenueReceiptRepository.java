package com.Nhom2.booking.repository;

import com.Nhom2.booking.entity.RevenueReceipt;
import com.Nhom2.booking.enums.ReceiptSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RevenueReceiptRepository extends JpaRepository<RevenueReceipt, Long> {

    List<RevenueReceipt> findByRevenueDateOrderByCreatedAtDesc(LocalDate revenueDate);

    List<RevenueReceipt> findByRevenueDateBetweenOrderByRevenueDateAsc(LocalDate from, LocalDate to);

    List<RevenueReceipt> findAllByOrderByRevenueDateDescCreatedAtDesc();

    Optional<RevenueReceipt> findFirstByBookingId(Long bookingId);

    boolean existsByReceiptNumberIgnoreCaseAndAmount(String receiptNumber, Double amount);

    @Query("select coalesce(sum(r.amount), 0) from RevenueReceipt r where r.source = :source")
    Double sumAmountBySource(@Param("source") ReceiptSource source);
}
