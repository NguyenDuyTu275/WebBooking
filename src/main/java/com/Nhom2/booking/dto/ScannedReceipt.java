package com.Nhom2.booking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Thông tin Gemini đọc được từ ảnh hóa đơn. Trường nào không in trên hóa đơn thì là null.
 *
 * @param receipt       ảnh có phải hóa đơn / biên lai thanh toán không
 * @param bookingCode   mã đơn in trên hóa đơn (vd "#37", "DH 37", "BK000037")
 * @param totalAmount   tổng số tiền đã trả, đơn vị VND
 * @param paymentDate   ngày thanh toán dạng yyyy-MM-dd
 * @param paymentMethod CASH, TRANSFER, CARD hoặc OTHER
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScannedReceipt(
        @JsonProperty("isReceipt") boolean receipt,
        String bookingCode,
        String receiptNumber,
        Double totalAmount,
        String paymentDate,
        String customerName,
        String hotelName,
        String paymentMethod,
        String description
) {
}
