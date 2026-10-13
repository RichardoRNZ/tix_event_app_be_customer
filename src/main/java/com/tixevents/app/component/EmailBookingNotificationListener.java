package com.tixevents.app.component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.tixevents.app.dto.EmailDTO;
import com.tixevents.app.event.BookingNotificationEvent;
import com.tixevents.app.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailBookingNotificationListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendEmailNotification(BookingNotificationEvent event) {
        log.info("Received BookingNotificationEvent for email: {}, status: {}", event.getEmail(), event.getStatus());

        String customerName = event.getCustomerName() != null ? event.getCustomerName() : "Pelanggan";
        String eventName = event.getEventName() != null ? event.getEventName() : "Symphony of The Night";
        String bookingNo = event.getBookingNo() != null ? event.getBookingNo() : "-";
        
        String seats = event.getSeats();
        if (seats == null || seats.isBlank()) {
            if (event.getSeatDetailsIds() != null && !event.getSeatDetailsIds().isEmpty()) {
                seats = event.getSeatDetailsIds().stream()
                        .map(id -> "Kursi #" + id)
                        .collect(Collectors.joining(", "));
            } else {
                seats = "-";
            }
        }

        String formattedPrice = formatRupiah(event.getTotalPrice());

        boolean isSuccess = "SUCCESS".equalsIgnoreCase(event.getStatus()) || "CONFIRMED".equalsIgnoreCase(event.getStatus());

        String subject = isSuccess
                ? "Pembayaran Berhasil - " + bookingNo
                : "Pembayaran Gagal / Waktu Habis - " + bookingNo;

        String template = isSuccess ? SUCCESS_TEMPLATE : FAILED_TEMPLATE;

        String finalHtml = template
                .replace("{{CUSTOMER_NAME}}", customerName)
                .replace("{{EVENT_NAME}}", eventName)
                .replace("{{BOOKING_NO}}", bookingNo)
                .replace("{{SEAT_NUMBERS}}", seats)
                .replace("{{TOTAL_PRICE}}", formattedPrice);

        EmailDTO emailDTO = EmailDTO.builder()
                .to(event.getEmail())
                .subject(subject)
                .text(finalHtml)
                .useHtml(true)
                .build();

        emailService.sendEmail(emailDTO);
    }

    private String formatRupiah(BigDecimal amount) {
        if (amount == null) {
            return "Rp 0";
        }
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));
        formatter.setMaximumFractionDigits(0);
        return formatter.format(amount);
    }

    private static final String SUCCESS_TEMPLATE = """
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pembayaran Berhasil - TixEvent</title>
    <!-- Import Plus Jakarta Sans dari Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    
    <style>
        /* Reset Styles untuk Email Client */
        body, table, td, a { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
        table, td { mso-table-lspace: 0pt; mso-table-rspace: 0pt; }
        img { -ms-interpolation-mode: bicubic; border: 0; height: auto; line-height: 100%; outline: none; text-decoration: none; }
        table { border-collapse: collapse !important; }
        body { height: 100% !important; margin: 0 !important; padding: 0 !important; width: 100% !important; }
        
        /* Font styling fallback */
        .font-sans {
            font-family: 'Plus Jakarta Sans', Arial, Helvetica, sans-serif;
        }
    </style>
</head>
<body style="background-color: #f8fafc; margin: 0; padding: 0; width: 100%; -webkit-font-smoothing: antialiased;">

    <!-- Wrapper Keseluruhan -->
    <table border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color: #f8fafc; padding: 40px 20px;">
        <tr>
            <td align="center">
                
                <!-- Main Container Box -->
                <table border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 600px; background-color: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden;">
                    
                    <!-- Content Area -->
                    <tr>
                        <td align="left" style="padding: 40px 32px;" class="font-sans">
                            
                            <!-- Header / Logo -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 32px;">
                                <tr>
                                    <!-- Kotak Logo "T" -->
                                    <td valign="middle" width="40">
                                        <div style="background-color: #2563eb; background-image: linear-gradient(to top right, #2563eb, #6366f1); width: 40px; height: 40px; border-radius: 12px; text-align: center; line-height: 40px; color: #ffffff; font-weight: 700; font-size: 20px; box-shadow: 0 4px 10px rgba(37, 99, 235, 0.3);">
                                            T
                                        </div>
                                    </td>
                                    <!-- Teks TixEvent -->
                                    <td valign="middle" style="padding-left: 12px;">
                                        <span style="font-weight: 800; font-size: 24px; color: #1e293b; letter-spacing: -0.5px;">
                                            TixEvent
                                        </span>
                                    </td>
                                </tr>
                            </table>

                            <!-- Success Banner -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 24px;">
                                <tr>
                                    <td align="center" style="padding: 24px; background-color: #ecfdf5; border-radius: 12px; border: 1px solid #a7f3d0;">
                                        <div style="width: 48px; height: 48px; background-color: #10b981; border-radius: 50%; text-align: center; line-height: 48px; color: #ffffff; font-size: 24px; margin: 0 auto 12px auto; box-shadow: 0 4px 10px rgba(16, 185, 129, 0.3);">
                                            &#10004;
                                        </div>
                                        <h2 style="margin: 0; font-size: 20px; font-weight: 800; color: #065f46;">Pembayaran Berhasil!</h2>
                                        <p style="margin: 8px 0 0 0; font-size: 14px; color: #047857; font-weight: 500;">Tiket Anda telah dikonfirmasi dan siap digunakan.</p>
                                    </td>
                                </tr>
                            </table>

                            <!-- Greeting -->
                            <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #64748b; font-weight: 400;">
                                Halo <strong style="color: #1e293b;">{{CUSTOMER_NAME}}</strong>,<br>
                                Terima kasih telah menggunakan TixEvent. Berikut adalah rincian pesanan tiket Anda untuk event mendatang:
                            </p>

                            <!-- Event Highlight -->
                            <h3 style="margin: 0 0 16px 0; font-size: 22px; font-weight: 800; color: #1e293b; text-align: center;">
                                {{EVENT_NAME}}
                            </h3>

                            <!-- Booking Details Table -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 32px; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;">
                                
                                <!-- No Booking -->
                                <tr>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #f8fafc; font-size: 14px; color: #64748b; font-weight: 500;" width="40%">
                                        No. Pesanan
                                    </td>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #ffffff; font-size: 14px; color: #1e293b; font-weight: 700; font-family: monospace;" align="right">
                                        {{BOOKING_NO}}
                                    </td>
                                </tr>

                                <!-- Nama Customer -->
                                <tr>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #f8fafc; font-size: 14px; color: #64748b; font-weight: 500;">
                                        Nama Pemesan
                                    </td>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #ffffff; font-size: 14px; color: #1e293b; font-weight: 600;" align="right">
                                        {{CUSTOMER_NAME}}
                                    </td>
                                </tr>

                                <!-- No Seat -->
                                <tr>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #f8fafc; font-size: 14px; color: #64748b; font-weight: 500;">
                                        Nomor Kursi
                                    </td>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #ffffff; font-size: 14px; color: #2563eb; font-weight: 700;" align="right">
                                        {{SEAT_NUMBERS}}
                                    </td>
                                </tr>

                                <!-- Total Harga -->
                                <tr>
                                    <td style="padding: 16px; background-color: #f8fafc; font-size: 15px; color: #1e293b; font-weight: 700;">
                                        Total Harga
                                    </td>
                                    <td style="padding: 16px; background-color: #ffffff; font-size: 18px; color: #2563eb; font-weight: 800;" align="right">
                                        {{TOTAL_PRICE}}
                                    </td>
                                </tr>
                            </table>

                            <!-- CTA Button -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 32px;">
                                <tr>
                                    <td align="center">
                                        <a href="#" style="background-color: #2563eb; color: #ffffff; font-size: 16px; font-weight: 600; text-decoration: none; padding: 16px 32px; border-radius: 10px; display: inline-block; box-shadow: 0 4px 6px rgba(37, 99, 235, 0.25);">
                                            Unduh E-Ticket
                                        </a>
                                    </td>
                                </tr>
                            </table>

                            <!-- Divider -->
                            <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 0 0 24px 0;">

                            <!-- Footer Text -->
                            <p style="margin: 0; font-size: 13px; line-height: 1.5; color: #94a3b8; font-weight: 400; text-align: center;">
                                Harap tunjukkan E-Ticket Anda saat berada di lokasi acara.<br>
                                Butuh bantuan? <a href="#" style="color: #2563eb; text-decoration: none; font-weight: 600;">Hubungi Tim Support Kami</a>
                            </p>

                        </td>
                    </tr>
                </table>

                <!-- External Footer -->
                <table border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 600px; margin-top: 20px;">
                    <tr>
                        <td align="center" style="font-size: 12px; color: #94a3b8; line-height: 1.5; font-weight: 500;" class="font-sans">
                            &copy; 2026 TixEvent. Hak Cipta Dilindungi.<br>
                            Sistem Platform Booking Tiket Modern
                        </td>
                    </tr>
                </table>

            </td>
        </tr>
    </table>
</body>
</html>
""";

    private static final String FAILED_TEMPLATE = """
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Pembayaran Gagal - TixEvent</title>
    <!-- Import Plus Jakarta Sans dari Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
    
    <style>
        /* Reset Styles untuk Email Client */
        body, table, td, a { -webkit-text-size-adjust: 100%; -ms-text-size-adjust: 100%; }
        table, td { mso-table-lspace: 0pt; mso-table-rspace: 0pt; }
        img { -ms-interpolation-mode: bicubic; border: 0; height: auto; line-height: 100%; outline: none; text-decoration: none; }
        table { border-collapse: collapse !important; }
        body { height: 100% !important; margin: 0 !important; padding: 0 !important; width: 100% !important; }
        
        /* Font styling fallback */
        .font-sans {
            font-family: 'Plus Jakarta Sans', Arial, Helvetica, sans-serif;
        }
    </style>
</head>
<body style="background-color: #f8fafc; margin: 0; padding: 0; width: 100%; -webkit-font-smoothing: antialiased;">

    <!-- Wrapper Keseluruhan -->
    <table border="0" cellpadding="0" cellspacing="0" width="100%" style="background-color: #f8fafc; padding: 40px 20px;">
        <tr>
            <td align="center">
                
                <!-- Main Container Box -->
                <table border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 600px; background-color: #ffffff; border-radius: 16px; border: 1px solid #e2e8f0; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden;">
                    
                    <!-- Content Area -->
                    <tr>
                        <td align="left" style="padding: 40px 32px;" class="font-sans">
                            
                            <!-- Header / Logo -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 32px;">
                                <tr>
                                    <!-- Kotak Logo "T" -->
                                    <td valign="middle" width="40">
                                        <div style="background-color: #2563eb; background-image: linear-gradient(to top right, #2563eb, #6366f1); width: 40px; height: 40px; border-radius: 12px; text-align: center; line-height: 40px; color: #ffffff; font-weight: 700; font-size: 20px; box-shadow: 0 4px 10px rgba(37, 99, 235, 0.3);">
                                            T
                                        </div>
                                    </td>
                                    <!-- Teks TixEvent -->
                                    <td valign="middle" style="padding-left: 12px;">
                                        <span style="font-weight: 800; font-size: 24px; color: #1e293b; letter-spacing: -0.5px;">
                                            TixEvent
                                        </span>
                                    </td>
                                </tr>
                            </table>

                            <!-- Failed/Expired Banner -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 24px;">
                                <tr>
                                    <td align="center" style="padding: 24px; background-color: #fef2f2; border-radius: 12px; border: 1px solid #fecaca;">
                                        <div style="width: 48px; height: 48px; background-color: #ef4444; border-radius: 50%; text-align: center; line-height: 48px; color: #ffffff; font-size: 24px; font-weight: bold; margin: 0 auto 12px auto; box-shadow: 0 4px 10px rgba(239, 68, 68, 0.3);">
                                            &#10006;
                                        </div>
                                        <h2 style="margin: 0; font-size: 20px; font-weight: 800; color: #991b1b;">Pembayaran Gagal / Waktu Habis</h2>
                                        <p style="margin: 8px 0 0 0; font-size: 14px; color: #b91c1c; font-weight: 500;">Pesanan Anda telah dibatalkan oleh sistem.</p>
                                    </td>
                                </tr>
                            </table>

                            <!-- Greeting & Explanation -->
                            <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #64748b; font-weight: 400;">
                                Halo <strong style="color: #1e293b;">{{CUSTOMER_NAME}}</strong>,<br>
                                Mohon maaf, waktu batas pembayaran tiket Anda telah habis atau terjadi kendala saat verifikasi pembayaran. Saat ini kursi yang Anda pilih telah kembali tersedia untuk umum.
                            </p>

                            <!-- Event Highlight -->
                            <h3 style="margin: 0 0 16px 0; font-size: 20px; font-weight: 800; text-align: center; text-decoration: line-through; color: #94a3b8;">
                                {{EVENT_NAME}}
                            </h3>

                            <!-- Booking Details Table -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 32px; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden; opacity: 0.85;">
                                
                                <!-- No Booking -->
                                <tr>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #f8fafc; font-size: 14px; color: #64748b; font-weight: 500;" width="40%">
                                        No. Pesanan
                                    </td>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #ffffff; font-size: 14px; color: #1e293b; font-weight: 700; font-family: monospace;" align="right">
                                        {{BOOKING_NO}}
                                    </td>
                                </tr>

                                <!-- Nama Event -->
                                <tr>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #f8fafc; font-size: 14px; color: #64748b; font-weight: 500;">
                                        Event
                                    </td>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #ffffff; font-size: 14px; color: #1e293b; font-weight: 600;" align="right">
                                        {{EVENT_NAME}}
                                    </td>
                                </tr>

                                <!-- No Seat -->
                                <tr>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #f8fafc; font-size: 14px; color: #64748b; font-weight: 500;">
                                        Nomor Kursi
                                    </td>
                                    <td style="padding: 16px; border-bottom: 1px solid #e2e8f0; background-color: #ffffff; font-size: 14px; color: #ef4444; font-weight: 700; text-decoration: line-through;" align="right">
                                        {{SEAT_NUMBERS}}
                                    </td>
                                </tr>

                                <!-- Total Harga -->
                                <tr>
                                    <td style="padding: 16px; background-color: #f8fafc; font-size: 15px; color: #1e293b; font-weight: 700;">
                                        Total Harga
                                    </td>
                                    <td style="padding: 16px; background-color: #ffffff; font-size: 18px; color: #ef4444; font-weight: 800;" align="right">
                                        {{TOTAL_PRICE}}
                                    </td>
                                </tr>
                            </table>

                            <!-- CTA Button -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 32px;">
                                <tr>
                                    <td align="center">
                                        <p style="margin: 0 0 16px 0; font-size: 14px; color: #64748b;">Ingin mencoba memesan ulang?</p>
                                        <a href="#" style="background-color: #ef4444; color: #ffffff; font-size: 16px; font-weight: 700; text-decoration: none; padding: 16px 32px; border-radius: 10px; display: inline-block; box-shadow: 0 4px 6px rgba(239, 68, 68, 0.25);">
                                            Pesan Kembali
                                        </a>
                                    </td>
                                </tr>
                            </table>

                            <!-- Divider -->
                            <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 0 0 24px 0;">

                            <!-- Footer Text -->
                            <p style="margin: 0; font-size: 13px; line-height: 1.5; color: #94a3b8; font-weight: 400; text-align: center;">
                                Jika dana Anda sudah terpotong namun pesanan gagal, mohon lampirkan bukti pembayaran dan <br><a href="#" style="color: #2563eb; text-decoration: none; font-weight: 600;">Hubungi Tim Support Kami</a>
                            </p>

                        </td>
                    </tr>
                </table>

                <!-- External Footer -->
                <table border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 600px; margin-top: 20px;">
                    <tr>
                        <td align="center" style="font-size: 12px; color: #94a3b8; line-height: 1.5; font-weight: 500;" class="font-sans">
                            &copy; 2026 TixEvent. Hak Cipta Dilindungi.<br>
                            Sistem Platform Booking Tiket Modern
                        </td>
                    </tr>
                </table>

            </td>
        </tr>
    </table>
</body>
</html>
""";
}
