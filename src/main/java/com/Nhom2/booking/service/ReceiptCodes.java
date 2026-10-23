package com.Nhom2.booking.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tìm mã đơn đặt phòng trong chữ in trên hóa đơn. */
final class ReceiptCodes {

    // Các dạng hệ thống in ra: "BK000037" (app, web), "DH 37" (nội dung chuyển khoản), "#37" (hóa đơn PDF)
    private static final List<Pattern> BOOKING_PATTERNS = List.of(
            Pattern.compile("\\bBK\\s*0*(\\d{1,12})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDH\\s*0*(\\d{1,12})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("#\\s*(\\d{1,12})\\b"),
            Pattern.compile("(?:booking|m[ãa]\\s*đơn|ma\\s*don)\\D{0,6}(\\d{1,12})\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    private static final Pattern DIGITS_ONLY = Pattern.compile("\\s*(\\d{1,12})\\s*");

    private ReceiptCodes() {
    }

    /** Mã đơn trong trường bookingCode; chuỗi chỉ gồm chữ số cũng được coi là mã đơn. */
    static Long fromBookingCode(String code) {
        if (code == null) {
            return null;
        }
        Matcher digits = DIGITS_ONLY.matcher(code);
        return digits.matches() ? parse(digits.group(1)) : find(code);
    }

    /** Mã đơn trong một chuỗi bất kỳ (vd số hóa đơn "BK000037"); chỉ nhận các dạng có tiền tố. */
    static Long find(String text) {
        if (text == null) {
            return null;
        }
        for (Pattern pattern : BOOKING_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return parse(matcher.group(1));
            }
        }
        return null;
    }

    private static Long parse(String digits) {
        long id = Long.parseLong(digits);
        return id > 0 ? id : null;
    }
}
