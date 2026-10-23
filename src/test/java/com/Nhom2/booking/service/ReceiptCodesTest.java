package com.Nhom2.booking.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptCodesTest {

    @Test
    void readsEveryCodeFormatTheSystemPrints() {
        assertThat(ReceiptCodes.fromBookingCode("BK000037")).isEqualTo(37L);
        assertThat(ReceiptCodes.fromBookingCode("DH 37")).isEqualTo(37L);
        assertThat(ReceiptCodes.fromBookingCode("dh37")).isEqualTo(37L);
        assertThat(ReceiptCodes.fromBookingCode("#37")).isEqualTo(37L);
        assertThat(ReceiptCodes.fromBookingCode("Mã đơn (Booking): #37")).isEqualTo(37L);
        assertThat(ReceiptCodes.fromBookingCode(" 37 ")).isEqualTo(37L);
    }

    @Test
    void receiptNumbersCountOnlyWithABookingPrefix() {
        assertThat(ReceiptCodes.find("BK000105")).isEqualTo(105L);
        assertThat(ReceiptCodes.find("0001234")).isNull();
        assertThat(ReceiptCodes.find("HD-2026-0099")).isNull();
    }

    @Test
    void missingOrZeroCodesGiveNoBooking() {
        assertThat(ReceiptCodes.fromBookingCode(null)).isNull();
        assertThat(ReceiptCodes.fromBookingCode("")).isNull();
        assertThat(ReceiptCodes.fromBookingCode("BK000000")).isNull();
        assertThat(ReceiptCodes.fromBookingCode("Khách lẻ")).isNull();
    }
}
