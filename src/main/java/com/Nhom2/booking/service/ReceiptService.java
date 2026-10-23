package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.CashReceiptRequest;
import com.Nhom2.booking.dto.DailyRevenueResponse;
import com.Nhom2.booking.dto.ReceiptResponse;
import com.Nhom2.booking.dto.ReceiptScanResponse;
import com.Nhom2.booking.dto.ReceiptScanResponse.Outcome;
import com.Nhom2.booking.dto.ScannedReceipt;
import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.RevenueReceipt;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.enums.ReceiptSource;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.exception.NotFoundException;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.RevenueReceiptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Quét hóa đơn thanh toán bằng máy ảnh để ghi vào báo cáo doanh thu theo ngày.
 * Hóa đơn của đơn trên hệ thống được ghi ngay vào doanh thu hôm nay; hóa đơn tiền mặt chưa có
 * trên hệ thống được trả về để admin kiểm tra rồi xác nhận qua {@link #createCash}.
 */
@Service
public class ReceiptService {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    private final GeminiReceiptReader reader;
    private final RevenueReceiptRepository receiptRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public ReceiptService(
            GeminiReceiptReader reader,
            RevenueReceiptRepository receiptRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            PlatformTransactionManager transactionManager) {
        this(reader, receiptRepository, bookingRepository, bookingService,
                new TransactionTemplate(transactionManager), Clock.system(VIETNAM));
    }

    ReceiptService(
            GeminiReceiptReader reader,
            RevenueReceiptRepository receiptRepository,
            BookingRepository bookingRepository,
            BookingService bookingService,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.reader = reader;
        this.receiptRepository = receiptRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public ReceiptScanResponse scan(MultipartFile image, String adminUsername) {
        String mimeType = imageType(image);
        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException e) {
            throw new BusinessException("error.receipt.imageRequired");
        }

        // Gọi Gemini ngoài transaction để không giữ kết nối database trong lúc chờ
        ScannedReceipt scanned = reader.read(bytes, mimeType);
        if (!scanned.receipt() || scanned.totalAmount() == null || scanned.totalAmount() <= 0) {
            throw new BusinessException("error.receipt.notReceipt");
        }

        Optional<Long> bookingId = Optional.ofNullable(ReceiptCodes.fromBookingCode(scanned.bookingCode()))
                .or(() -> Optional.ofNullable(ReceiptCodes.find(scanned.receiptNumber())))
                .filter(bookingRepository::existsById);
        if (bookingId.isEmpty()) {
            return new ReceiptScanResponse(Outcome.NEW_CASH_RECEIPT, scanned, null, null, suggestedDate(scanned));
        }
        return transactionTemplate.execute(status -> recordBooking(bookingId.get(), scanned, adminUsername));
    }

    private ReceiptScanResponse recordBooking(Long bookingId, ScannedReceipt scanned, String adminUsername) {
        Optional<RevenueReceipt> existing = receiptRepository.findFirstByBookingId(bookingId);
        if (existing.isPresent()) {
            return new ReceiptScanResponse(
                    Outcome.ALREADY_RECORDED, scanned, ReceiptResponse.from(existing.get()), bookingId, null);
        }

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("error.booking.notFound"));
        Outcome outcome = Outcome.ADDED_TO_DAILY_REPORT;
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            // Khách trả tiền mặt tại khách sạn cho một đơn chưa thanh toán
            if (booking.getStatus() == BookingStatus.PENDING) {
                bookingService.approveBooking(bookingId);
            } else if (booking.getStatus() != BookingStatus.CONFIRMED) {
                throw new BusinessException("error.payment.notPayable");
            }
            booking = bookingService.markBookingPaid(bookingId);
            outcome = Outcome.BOOKING_MARKED_PAID;
        }

        RevenueReceipt receipt = new RevenueReceipt();
        receipt.setSource(ReceiptSource.SYSTEM_INVOICE);
        receipt.setBooking(booking);
        receipt.setAmount(booking.getTotalPrice() != null ? booking.getTotalPrice() : scanned.totalAmount());
        receipt.setRevenueDate(today());
        receipt.setReceiptNumber(limit(scanned.receiptNumber(), 100));
        receipt.setCustomerName(firstText(scanned.customerName(),
                booking.getUser() == null ? null : booking.getUser().getUsername()));
        receipt.setHotelName(booking.getHotel() == null ? limit(scanned.hotelName(), 255) : booking.getHotel().getName());
        receipt.setDescription(limit(scanned.description(), 500));
        receipt.setCreatedBy(adminUsername);
        return new ReceiptScanResponse(
                outcome, scanned, ReceiptResponse.from(receiptRepository.save(receipt)), bookingId, null);
    }

    @Transactional
    public ReceiptResponse createCash(CashReceiptRequest request, String adminUsername) {
        if (request == null || request.getAmount() == null || request.getAmount() <= 0) {
            throw new BusinessException("error.receipt.amountInvalid");
        }
        LocalDate date = request.getRevenueDate() == null ? today() : request.getRevenueDate();
        if (date.isAfter(today())) {
            throw new BusinessException("error.receipt.futureDate");
        }
        String receiptNumber = limit(request.getReceiptNumber(), 100);
        if (receiptNumber != null
                && receiptRepository.existsByReceiptNumberIgnoreCaseAndAmount(receiptNumber, request.getAmount())) {
            throw new BusinessException("error.receipt.duplicate");
        }

        RevenueReceipt receipt = new RevenueReceipt();
        receipt.setSource(ReceiptSource.CASH);
        receipt.setAmount(request.getAmount());
        receipt.setRevenueDate(date);
        receipt.setReceiptNumber(receiptNumber);
        receipt.setCustomerName(limit(request.getCustomerName(), 255));
        receipt.setHotelName(limit(request.getHotelName(), 255));
        receipt.setDescription(limit(request.getDescription(), 500));
        receipt.setCreatedBy(adminUsername);
        return ReceiptResponse.from(receiptRepository.save(receipt));
    }

    @Transactional(readOnly = true)
    public DailyRevenueResponse daily(LocalDate date) {
        LocalDate day = date == null ? today() : date;
        List<ReceiptResponse> receipts = receiptRepository.findByRevenueDateOrderByCreatedAtDesc(day).stream()
                .map(ReceiptResponse::from)
                .toList();
        double cash = total(receipts, ReceiptSource.CASH);
        double invoices = total(receipts, ReceiptSource.SYSTEM_INVOICE);
        return new DailyRevenueResponse(day, receipts, cash, invoices, cash + invoices);
    }

    @Transactional(readOnly = true)
    public List<ReceiptResponse> list(LocalDate from, LocalDate to) {
        List<RevenueReceipt> receipts = from == null && to == null
                ? receiptRepository.findAllByOrderByRevenueDateDescCreatedAtDesc()
                : receiptRepository.findByRevenueDateBetweenOrderByRevenueDateAsc(
                        from == null ? LocalDate.of(2000, 1, 1) : from, to == null ? today() : to);
        return receipts.stream().map(ReceiptResponse::from).toList();
    }

    private static String imageType(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new BusinessException("error.receipt.imageRequired");
        }
        String type = image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(type)) {
            throw new BusinessException("error.receipt.imageType");
        }
        return type;
    }

    /** Ngày in trên hóa đơn nếu hợp lệ và không ở tương lai, ngược lại là hôm nay. */
    private LocalDate suggestedDate(ScannedReceipt scanned) {
        if (scanned.paymentDate() != null) {
            try {
                LocalDate printed = LocalDate.parse(scanned.paymentDate().trim());
                if (!printed.isAfter(today())) {
                    return printed;
                }
            } catch (DateTimeParseException ignored) {
                // Ngày in không đúng định dạng: dùng hôm nay
            }
        }
        return today();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static double total(List<ReceiptResponse> receipts, ReceiptSource source) {
        return receipts.stream()
                .filter(r -> r.source() == source)
                .mapToDouble(r -> r.amount() == null ? 0 : r.amount())
                .sum();
    }

    private static String firstText(String preferred, String fallback) {
        String value = limit(preferred, 255);
        return value != null ? value : limit(fallback, 255);
    }

    private static String limit(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
