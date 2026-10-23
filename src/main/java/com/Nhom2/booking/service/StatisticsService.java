package com.Nhom2.booking.service;

import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.Nhom2.booking.enums.ReceiptSource;
import com.Nhom2.booking.enums.RequestStatus;
import com.Nhom2.booking.enums.UserRole;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.BookingRequestRepository;
import com.Nhom2.booking.repository.HotelRepository;
import com.Nhom2.booking.repository.RevenueReceiptRepository;
import com.Nhom2.booking.repository.RoomRepository;
import com.Nhom2.booking.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service

//đã thêm

public class StatisticsService {

    private final BookingRepository bookingRepository;
    private final BookingRequestRepository bookingRequestRepository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;
    private final RevenueReceiptRepository receiptRepository;

    public StatisticsService(
            BookingRepository bookingRepository,
            BookingRequestRepository bookingRequestRepository,
            UserRepository userRepository,
            HotelRepository hotelRepository,
            RoomRepository roomRepository,
            RevenueReceiptRepository receiptRepository
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingRequestRepository = bookingRequestRepository;
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
        this.receiptRepository = receiptRepository;
    }

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("totalUsers", userRepository.countByRole(UserRole.USER));
        result.put("totalAdmins", userRepository.countByRole(UserRole.ADMIN));
        result.put("totalHotels", hotelRepository.count());
        result.put("totalRooms", roomRepository.count());
        result.put("totalBookings", bookingRepository.count());
        result.put("pendingBookings", bookingRepository.countByStatus(BookingStatus.PENDING));
        result.put("confirmedBookings", bookingRepository.countByStatus(BookingStatus.CONFIRMED));
        result.put("rejectedBookings", bookingRepository.countByStatus(BookingStatus.REJECTED));
        result.put("cancelledBookings", bookingRepository.countByStatus(BookingStatus.CANCELLED));
        result.put("pendingRequests", bookingRequestRepository.countByStatus(RequestStatus.PENDING));
        // Doanh thu = đơn đã thanh toán trên hệ thống + hóa đơn tiền mặt quét tại khách sạn
        double bookingRevenue = valueOrZero(bookingRepository.sumTotalPriceByPaymentStatus(PaymentStatus.PAID));
        double cashRevenue = valueOrZero(receiptRepository.sumAmountBySource(ReceiptSource.CASH));
        result.put("paidRevenue", bookingRevenue + cashRevenue);
        result.put("cashRevenue", cashRevenue);

        return result;
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0 : value;
    }
}
