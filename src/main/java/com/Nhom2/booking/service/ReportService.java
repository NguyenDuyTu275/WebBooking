package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.RevenueReceipt;
import com.Nhom2.booking.repository.BookingRepository;
import com.Nhom2.booking.repository.RevenueReceiptRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ReportService {

    private final BookingRepository bookingRepository;
    private final RevenueReceiptRepository receiptRepository;

    public ReportService(BookingRepository bookingRepository, RevenueReceiptRepository receiptRepository) {
        this.bookingRepository = bookingRepository;
        this.receiptRepository = receiptRepository;
    }

    public byte[] exportBookingsExcel() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Bookings");
            Row header = sheet.createRow(0);
            String[] columns = {
                    "ID",
                    "User",
                    "Hotel",
                    "Check in",
                    "Check out",
                    "Status",
                    "Payment",
                    "Total price",
                    "Created at"
            };

            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            List<Booking> bookings = bookingRepository.findAllByOrderByCreatedAtDesc();

            for (int i = 0; i < bookings.size(); i++) {
                Booking booking = bookings.get(i);
                Row row = sheet.createRow(i + 1);

                row.createCell(0).setCellValue(booking.getId());
                row.createCell(1).setCellValue(booking.getUser().getUsername());
                row.createCell(2).setCellValue(booking.getHotel().getName());
                row.createCell(3).setCellValue(String.valueOf(booking.getCheckInDate()));
                row.createCell(4).setCellValue(String.valueOf(booking.getCheckOutDate()));
                row.createCell(5).setCellValue(String.valueOf(booking.getStatus()));
                row.createCell(6).setCellValue(String.valueOf(booking.getPaymentStatus()));
                row.createCell(7).setCellValue(
                        booking.getTotalPrice() == null ? 0 : booking.getTotalPrice()
                );
                row.createCell(8).setCellValue(String.valueOf(booking.getCreatedAt()));
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            writeReceiptsSheet(workbook);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Cannot export booking report", ex);
        }
    }

    /** Sheet thứ hai: các khoản doanh thu ghi bằng cách quét hóa đơn (tiền mặt và hóa đơn hệ thống). */
    private void writeReceiptsSheet(Workbook workbook) {
        Sheet sheet = workbook.createSheet("Receipts");
        String[] columns = {
                "ID",
                "Revenue date",
                "Source",
                "Booking ID",
                "Amount",
                "Receipt number",
                "Customer",
                "Hotel",
                "Description",
                "Recorded by"
        };

        Row header = sheet.createRow(0);
        for (int i = 0; i < columns.length; i++) {
            header.createCell(i).setCellValue(columns[i]);
        }

        List<RevenueReceipt> receipts = receiptRepository.findAllByOrderByRevenueDateDescCreatedAtDesc();
        for (int i = 0; i < receipts.size(); i++) {
            RevenueReceipt receipt = receipts.get(i);
            Row row = sheet.createRow(i + 1);

            row.createCell(0).setCellValue(receipt.getId());
            row.createCell(1).setCellValue(String.valueOf(receipt.getRevenueDate()));
            row.createCell(2).setCellValue(String.valueOf(receipt.getSource()));
            row.createCell(3).setCellValue(receipt.getBooking() == null ? "" : String.valueOf(receipt.getBooking().getId()));
            row.createCell(4).setCellValue(receipt.getAmount() == null ? 0 : receipt.getAmount());
            row.createCell(5).setCellValue(nullToEmpty(receipt.getReceiptNumber()));
            row.createCell(6).setCellValue(nullToEmpty(receipt.getCustomerName()));
            row.createCell(7).setCellValue(nullToEmpty(receipt.getHotelName()));
            row.createCell(8).setCellValue(nullToEmpty(receipt.getDescription()));
            row.createCell(9).setCellValue(nullToEmpty(receipt.getCreatedBy()));
        }

        for (int i = 0; i < columns.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
