package com.Nhom2.booking.dto;

import java.time.LocalDate;

/**
 * Kết quả quét một hóa đơn.
 *
 * @param receipt       khoản đã ghi vào báo cáo; null khi {@link Outcome#NEW_CASH_RECEIPT}
 * @param suggestedDate ngày đề xuất ghi doanh thu cho hóa đơn tiền mặt (ngày trên hóa đơn hoặc hôm nay)
 */
public record ReceiptScanResponse(
        Outcome outcome,
        ScannedReceipt scanned,
        ReceiptResponse receipt,
        Long bookingId,
        LocalDate suggestedDate
) {

    public enum Outcome {
        /** Đơn trên hệ thống chưa thanh toán: đã đánh dấu đã thanh toán và ghi vào doanh thu hôm nay. */
        BOOKING_MARKED_PAID,
        /** Đơn trên hệ thống đã thanh toán: đã thêm vào báo cáo doanh thu hôm nay. */
        ADDED_TO_DAILY_REPORT,
        /** Hóa đơn của đơn này đã có trong báo cáo, không ghi thêm. */
        ALREADY_RECORDED,
        /** Hóa đơn chưa có trên hệ thống (tiền mặt): admin kiểm tra thông tin rồi xác nhận để ghi. */
        NEW_CASH_RECEIPT
    }
}
