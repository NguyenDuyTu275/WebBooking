package com.Nhom2.booking.entity;

import com.Nhom2.booking.enums.ReceiptSource;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Một khoản doanh thu admin ghi vào báo cáo theo ngày bằng cách quét hóa đơn. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "revenue_receipts")
public class RevenueReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptSource source;

    // Chỉ có với hóa đơn của đơn đặt phòng trên hệ thống
    @ManyToOne
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(nullable = false)
    private Double amount;

    // Ngày khoản tiền được tính vào báo cáo doanh thu
    @Column(nullable = false)
    private LocalDate revenueDate;

    @Column(length = 100)
    private String receiptNumber;

    private String customerName;

    private String hotelName;

    @Column(length = 500)
    private String description;

    private String createdBy;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
