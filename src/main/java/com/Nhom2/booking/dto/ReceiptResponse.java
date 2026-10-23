package com.Nhom2.booking.dto;

import com.Nhom2.booking.entity.RevenueReceipt;
import com.Nhom2.booking.enums.ReceiptSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một dòng trong báo cáo doanh thu theo ngày. */
public record ReceiptResponse(
        Long id,
        ReceiptSource source,
        Long bookingId,
        Double amount,
        LocalDate revenueDate,
        String receiptNumber,
        String customerName,
        String hotelName,
        String description,
        String createdBy,
        LocalDateTime createdAt
) {

    public static ReceiptResponse from(RevenueReceipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getSource(),
                receipt.getBooking() == null ? null : receipt.getBooking().getId(),
                receipt.getAmount(),
                receipt.getRevenueDate(),
                receipt.getReceiptNumber(),
                receipt.getCustomerName(),
                receipt.getHotelName(),
                receipt.getDescription(),
                receipt.getCreatedBy(),
                receipt.getCreatedAt());
    }
}
