package com.Nhom2.booking.enums;

/** Nguồn của một khoản doanh thu được ghi bằng cách quét hóa đơn. */
public enum ReceiptSource {
    /** Hóa đơn của một đơn đặt phòng đã có trên hệ thống. */
    SYSTEM_INVOICE,
    /** Khách trả tiền mặt tại khách sạn, hóa đơn chưa có trên hệ thống. */
    CASH
}
