package com.Nhom2.booking.dto;

import lombok.Data;

import java.time.LocalDate;

/** Hóa đơn tiền mặt admin đã kiểm tra sau khi quét, gửi lên để ghi vào doanh thu. */
@Data
public class CashReceiptRequest {
    private Double amount;
    private LocalDate revenueDate;
    private String receiptNumber;
    private String customerName;
    private String hotelName;
    private String description;
}
