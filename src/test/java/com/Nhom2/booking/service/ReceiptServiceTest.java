package com.Nhom2.booking.service;

import com.Nhom2.booking.dto.CashReceiptRequest;
import com.Nhom2.booking.dto.DailyRevenueResponse;
import com.Nhom2.booking.dto.ReceiptResponse;
import com.Nhom2.booking.dto.ReceiptScanResponse;
import com.Nhom2.booking.dto.ReceiptScanResponse.Outcome;
import com.Nhom2.booking.dto.ScannedReceipt;
import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.RevenueReceipt;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.enums.ReceiptSource;
import com.Nhom2.booking.exception.BusinessException;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.RevenueReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
    private static final MockMultipartFile PHOTO =
            new MockMultipartFile("file", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

    private GeminiReceiptReader reader;
    private RevenueReceiptRepository receiptRepository;
    private BookingRepository bookingRepository;
    private BookingService bookingService;
    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        reader = mock(GeminiReceiptReader.class);
        receiptRepository = mock(RevenueReceiptRepository.class);
        bookingRepository = mock(BookingRepository.class);
        bookingService = mock(BookingService.class);
        // 6:30 sáng 23/10 giờ Việt Nam, lúc theo UTC vẫn là 22/10: "hôm nay" phải tính theo giờ Việt Nam
        Clock clock = Clock.fixed(ZonedDateTime.of(2026, 10, 23, 6, 30, 0, 0, ReceiptService.VIETNAM).toInstant(),
                ReceiptService.VIETNAM);
        receiptService = new ReceiptService(reader, receiptRepository, bookingRepository, bookingService,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), clock);

        when(receiptRepository.save(any(RevenueReceipt.class))).thenAnswer(invocation -> {
            RevenueReceipt receipt = invocation.getArgument(0);
            receipt.setId(1L);
            return receipt;
        });
    }

    private static ScannedReceipt scanned(String bookingCode, double amount, String paymentDate) {
        return new ScannedReceipt(true, bookingCode, "HD-0099", amount, paymentDate,
                "Nguyen Van A", "H Hotel", "CASH", "Phòng Deluxe 2 đêm");
    }

    private Booking booking(BookingStatus status, PaymentStatus paymentStatus) {
        User guest = new User();
        guest.setUsername("bich");
        Hotel hotel = new Hotel();
        hotel.setName("H Hotel L Art Hanoi");
        Booking booking = new Booking();
        booking.setId(37L);
        booking.setStatus(status);
        booking.setPaymentStatus(paymentStatus);
        booking.setTotalPrice(3_000_000d);
        booking.setUser(guest);
        booking.setHotel(hotel);
        when(bookingRepository.existsById(37L)).thenReturn(true);
        when(bookingRepository.findById(37L)).thenReturn(Optional.of(booking));
        return booking;
    }

    @Test
    void anInvoiceOfAPaidBookingIsAddedToTodaysReport() {
        booking(BookingStatus.CONFIRMED, PaymentStatus.PAID);
        when(reader.read(any(), any())).thenReturn(scanned("BK000037", 3_000_000, "2026-10-20"));

        ReceiptScanResponse result = receiptService.scan(PHOTO, "admin");

        assertThat(result.outcome()).isEqualTo(Outcome.ADDED_TO_DAILY_REPORT);
        assertThat(result.bookingId()).isEqualTo(37L);
        assertThat(result.receipt().source()).isEqualTo(ReceiptSource.SYSTEM_INVOICE);
        assertThat(result.receipt().revenueDate()).isEqualTo(TODAY);
        assertThat(result.receipt().amount()).isEqualTo(3_000_000d);
        assertThat(result.receipt().hotelName()).isEqualTo("H Hotel L Art Hanoi");
        assertThat(result.receipt().createdBy()).isEqualTo("admin");
        verify(bookingService, never()).markBookingPaid(any());
    }

    @Test
    void cashPaidForAnUnpaidBookingMarksItPaid() {
        Booking booking = booking(BookingStatus.CONFIRMED, PaymentStatus.UNPAID);
        when(bookingService.markBookingPaid(37L)).thenAnswer(invocation -> {
            booking.setPaymentStatus(PaymentStatus.PAID);
            return booking;
        });
        when(reader.read(any(), any())).thenReturn(scanned("DH 37", 3_000_000, null));

        ReceiptScanResponse result = receiptService.scan(PHOTO, "admin");

        assertThat(result.outcome()).isEqualTo(Outcome.BOOKING_MARKED_PAID);
        assertThat(result.receipt().bookingId()).isEqualTo(37L);
        verify(bookingService, never()).approveBooking(any());
    }

    @Test
    void aPendingBookingIsApprovedBeforeItIsMarkedPaid() {
        Booking booking = booking(BookingStatus.PENDING, PaymentStatus.UNPAID);
        when(bookingService.markBookingPaid(37L)).thenReturn(booking);
        when(reader.read(any(), any())).thenReturn(scanned("#37", 3_000_000, null));

        receiptService.scan(PHOTO, "admin");

        InOrder order = inOrder(bookingService);
        order.verify(bookingService).approveBooking(37L);
        order.verify(bookingService).markBookingPaid(37L);
    }

    @Test
    void aBookingAlreadyInTheReportIsNotCountedTwice() {
        booking(BookingStatus.CONFIRMED, PaymentStatus.PAID);
        RevenueReceipt existing = new RevenueReceipt();
        existing.setId(9L);
        existing.setSource(ReceiptSource.SYSTEM_INVOICE);
        existing.setAmount(3_000_000d);
        when(receiptRepository.findFirstByBookingId(37L)).thenReturn(Optional.of(existing));
        when(reader.read(any(), any())).thenReturn(scanned("BK000037", 3_000_000, null));

        ReceiptScanResponse result = receiptService.scan(PHOTO, "admin");

        assertThat(result.outcome()).isEqualTo(Outcome.ALREADY_RECORDED);
        assertThat(result.receipt().id()).isEqualTo(9L);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void aCancelledBookingCannotBePaidByReceipt() {
        booking(BookingStatus.CANCELLED, PaymentStatus.UNPAID);
        when(reader.read(any(), any())).thenReturn(scanned("BK000037", 3_000_000, null));

        assertThatThrownBy(() -> receiptService.scan(PHOTO, "admin"))
                .isInstanceOf(BusinessException.class).hasMessage("error.payment.notPayable");
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void aReceiptNotOnTheSystemIsReturnedForTheAdminToConfirm() {
        when(reader.read(any(), any())).thenReturn(scanned(null, 850_000, "2026-10-20"));

        ReceiptScanResponse result = receiptService.scan(PHOTO, "admin");

        assertThat(result.outcome()).isEqualTo(Outcome.NEW_CASH_RECEIPT);
        assertThat(result.receipt()).isNull();
        assertThat(result.suggestedDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(result.scanned().totalAmount()).isEqualTo(850_000d);
        verify(receiptRepository, never()).save(any());
    }

    @Test
    void aBookingCodeThatDoesNotExistIsTreatedAsCashAndFutureDatesBecomeToday() {
        when(bookingRepository.existsById(999L)).thenReturn(false);
        when(reader.read(any(), any())).thenReturn(scanned("BK000999", 850_000, "2027-01-01"));

        ReceiptScanResponse result = receiptService.scan(PHOTO, "admin");

        assertThat(result.outcome()).isEqualTo(Outcome.NEW_CASH_RECEIPT);
        assertThat(result.suggestedDate()).isEqualTo(TODAY);
    }

    @Test
    void photosThatAreNotReceiptsAreRejected() {
        when(reader.read(any(), any())).thenReturn(new ScannedReceipt(false, null, null, null, null, null, null, null, null));
        assertThatThrownBy(() -> receiptService.scan(PHOTO, "admin"))
                .isInstanceOf(BusinessException.class).hasMessage("error.receipt.notReceipt");

        MockMultipartFile text = new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1});
        assertThatThrownBy(() -> receiptService.scan(text, "admin"))
                .isInstanceOf(BusinessException.class).hasMessage("error.receipt.imageType");
    }

    @Test
    void aConfirmedCashReceiptIsSavedForItsDate() {
        CashReceiptRequest request = new CashReceiptRequest();
        request.setAmount(850_000d);
        request.setRevenueDate(LocalDate.of(2026, 10, 20));
        request.setReceiptNumber(" HD-0099 ");
        request.setCustomerName("Nguyen Van A");

        ReceiptResponse saved = receiptService.createCash(request, "admin");

        ArgumentCaptor<RevenueReceipt> captor = ArgumentCaptor.forClass(RevenueReceipt.class);
        verify(receiptRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(ReceiptSource.CASH);
        assertThat(captor.getValue().getBooking()).isNull();
        assertThat(saved.revenueDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(saved.receiptNumber()).isEqualTo("HD-0099");
    }

    @Test
    void invalidOrDuplicateCashReceiptsAreRejected() {
        CashReceiptRequest zero = new CashReceiptRequest();
        zero.setAmount(0d);
        assertThatThrownBy(() -> receiptService.createCash(zero, "admin"))
                .isInstanceOf(BusinessException.class).hasMessage("error.receipt.amountInvalid");

        CashReceiptRequest future = new CashReceiptRequest();
        future.setAmount(100d);
        future.setRevenueDate(TODAY.plusDays(1));
        assertThatThrownBy(() -> receiptService.createCash(future, "admin"))
                .isInstanceOf(BusinessException.class).hasMessage("error.receipt.futureDate");

        CashReceiptRequest duplicate = new CashReceiptRequest();
        duplicate.setAmount(850_000d);
        duplicate.setReceiptNumber("HD-0099");
        when(receiptRepository.existsByReceiptNumberIgnoreCaseAndAmount("HD-0099", 850_000d)).thenReturn(true);
        assertThatThrownBy(() -> receiptService.createCash(duplicate, "admin"))
                .isInstanceOf(BusinessException.class).hasMessage("error.receipt.duplicate");

        verify(receiptRepository, never()).save(any());
    }

    @Test
    void theDailyReportTotalsCashAndSystemInvoicesSeparately() {
        RevenueReceipt cash = new RevenueReceipt();
        cash.setSource(ReceiptSource.CASH);
        cash.setAmount(850_000d);
        RevenueReceipt invoice = new RevenueReceipt();
        invoice.setSource(ReceiptSource.SYSTEM_INVOICE);
        invoice.setAmount(3_000_000d);
        when(receiptRepository.findByRevenueDateOrderByCreatedAtDesc(TODAY)).thenReturn(List.of(cash, invoice));

        DailyRevenueResponse report = receiptService.daily(null);

        assertThat(report.date()).isEqualTo(TODAY);
        assertThat(report.receipts()).hasSize(2);
        assertThat(report.cashTotal()).isEqualTo(850_000d);
        assertThat(report.invoiceTotal()).isEqualTo(3_000_000d);
        assertThat(report.total()).isEqualTo(3_850_000d);
    }
}
