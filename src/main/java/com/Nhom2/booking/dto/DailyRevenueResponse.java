package com.Nhom2.booking.dto;

import java.time.LocalDate;
import java.util.List;

/** Báo cáo doanh thu một ngày từ các hóa đơn đã quét. */
public record DailyRevenueResponse(
        LocalDate date,
        List<ReceiptResponse> receipts,
        double cashTotal,
        double invoiceTotal,
        double total
) {
}
