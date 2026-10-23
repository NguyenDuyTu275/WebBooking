package com.Nhom2.booking.controller;

import com.Nhom2.booking.dto.CashReceiptRequest;
import com.Nhom2.booking.dto.DailyRevenueResponse;
import com.Nhom2.booking.dto.ReceiptResponse;
import com.Nhom2.booking.dto.ReceiptScanResponse;
import com.Nhom2.booking.service.ReceiptService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/** Quét hóa đơn bằng máy ảnh (Gemini) và báo cáo doanh thu theo ngày. Chỉ ADMIN (/admin/**). */
@RestController
@RequestMapping("/admin/receipts")
public class AdminReceiptController {

    private final ReceiptService receiptService;

    public AdminReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptScanResponse scan(@RequestPart("file") MultipartFile file, Authentication authentication) {
        return receiptService.scan(file, authentication.getName());
    }

    @PostMapping
    public ReceiptResponse createCash(@RequestBody CashReceiptRequest request, Authentication authentication) {
        return receiptService.createCash(request, authentication.getName());
    }

    @GetMapping("/daily")
    public DailyRevenueResponse daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return receiptService.daily(date);
    }

    @GetMapping
    public List<ReceiptResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return receiptService.list(from, to);
    }
}
