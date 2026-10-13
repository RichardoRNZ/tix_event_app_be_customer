package com.tixevents.app.component;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.tixevents.app.dto.EmailDTO;
import com.tixevents.app.event.OtpIssuedEvent;
import com.tixevents.app.service.EmailService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailVerificationListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendEmailOtp(OtpIssuedEvent event) {
        String htmlTemplate = """
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kode OTP TixEvent</title>
    <!-- Import Plus Jakarta Sans dari Google Fonts -->
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
    
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
                <table border="0" cellpadding="0" cellspacing="0" width="100%" style="max-width: 600px; background-color: #ffffff; border-radius: 16px; border: 1px solid #f1f5f9; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden;">
                    
                    <!-- Content Area -->
                    <tr>
                        <td align="left" style="padding: 40px 32px;" class="font-sans">
                            
                            <!-- Header / Logo -->
                            <table border="0" cellpadding="0" cellspacing="0" style="margin-bottom: 32px;">
                                <tr>
                                    <!-- Kotak Logo "T" -->
                                    <td valign="middle">
                                        <div style="background-color: #2563eb; background-image: linear-gradient(to top right, #2563eb, #6366f1); width: 40px; height: 40px; border-radius: 12px; text-align: center; line-height: 40px; color: #ffffff; font-weight: 700; font-size: 20px; box-shadow: 0 4px 10px rgba(37, 99, 235, 0.3);">
                                            T
                                        </div>
                                    </td>
                                    <!-- Teks TixEvent -->
                                    <td valign="middle" style="padding-left: 12px;">
                                        <span style="font-weight: 700; font-size: 24px; color: #1e293b; letter-spacing: -0.5px;">
                                            TixEvent
                                        </span>
                                    </td>
                                </tr>
                            </table>

                            <!-- Greeting & Intro -->
                            <h1 style="margin: 0 0 12px 0; font-size: 20px; font-weight: 700; color: #0f172a;">Kode Verifikasi Anda</h1>
                            <p style="margin: 0 0 24px 0; font-size: 15px; line-height: 1.6; color: #64748b; font-weight: 400;">
                                Halo,<br><br>
                                Anda baru saja meminta kode verifikasi (OTP) untuk masuk atau mendaftar di TixEvent. Masukkan kode 6 digit di bawah ini untuk melanjutkan proses:
                            </p>

                            <!-- OTP Box -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 24px;">
                                <tr>
                                    <td align="center">
                                        <div style="background-color: #ffffff; border: 2px dashed #94a3b8; border-radius: 12px; padding: 20px; text-align: center;">
                                            <span style="font-size: 36px; font-weight: 800; color: #2563eb; letter-spacing: 8px; font-family: 'Plus Jakarta Sans', monospace;">
                                                {{OTP_CODE}}
                                            </span>
                                        </div>
                                    </td>
                                </tr>
                            </table>

                            <!-- Warning / Info Box -->
                            <table border="0" cellpadding="0" cellspacing="0" width="100%" style="margin-bottom: 32px; background-color: #eff6ff; border-radius: 10px; border: 1px solid #bfdbfe;">
                                <tr>
                                    <td style="padding: 16px; font-size: 14px; line-height: 1.5; color: #1e40af; font-weight: 500;">
                                        <span style="display: block; margin-bottom: 4px; font-weight: 700; color: #1d4ed8;">
                                            &#9888; Keamanan Akun
                                        </span>
                                        Kode ini hanya berlaku selama <strong>5 menit</strong>. Jangan pernah membagikan kode OTP ini kepada siapa pun, termasuk pihak yang mengatasnamakan TixEvent.
                                    </td>
                                </tr>
                            </table>

                            <!-- Divider -->
                            <hr style="border: 0; border-top: 1px solid #e2e8f0; margin: 0 0 24px 0;">

                            <!-- Footer Text -->
                            <p style="margin: 0; font-size: 13px; line-height: 1.5; color: #94a3b8; font-weight: 400;">
                                Jika Anda tidak merasa melakukan permintaan ini, Anda dapat mengabaikan email ini dengan aman. Akun Anda tetap terlindungi.<br><br>
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

        String finalHtml = htmlTemplate.replace("{{OTP_CODE}}", event.getOtpCode());

        EmailDTO emailDTO = new EmailDTO();
        emailDTO.setTo(event.getEmail());
        emailDTO.setSubject("Kode Verifikasi TixEvent Anda");
        emailDTO.setText(finalHtml);
        emailDTO.setUseHtml(true);

        emailService.sendEmail(emailDTO);
    }
}
