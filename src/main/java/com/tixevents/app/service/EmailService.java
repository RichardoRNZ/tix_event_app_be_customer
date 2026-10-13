package com.tixevents.app.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.tixevents.app.dto.EmailDTO;
import com.tixevents.app.entity.MailLog;
import com.tixevents.app.repository.MailLogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender emailSender;
    private final MailLogRepository mailLogRepository;

    @Async
    public void sendEmail(EmailDTO emailDTO) {
        MailLog mailLog = new MailLog();
        mailLog.setRecipientEmail(emailDTO.getTo());
        mailLog.setSubject(emailDTO.getSubject());

        try {
            if (emailDTO.isUseHtml()) {
                MimeMessage message = emailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom("noreply@tixevents.com"); // Sesuaikan dengan email pengirim Anda
                helper.setTo(emailDTO.getTo());
                helper.setSubject(emailDTO.getSubject());
                helper.setText(emailDTO.getText(), true);
                emailSender.send(message);
            } else {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom("noreply@tixevents.com"); // Sesuaikan dengan email pengirim Anda
                message.setTo(emailDTO.getTo());
                message.setSubject(emailDTO.getSubject());
                message.setText(emailDTO.getText());
                emailSender.send(message);
            }
            log.info("Email sent successfully to: {}", emailDTO.getTo());
            mailLog.setStatus("SUCCESS");
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", emailDTO.getTo(), e.getMessage());
            mailLog.setStatus("FAILED");
            mailLog.setErrorMessage(e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        } finally {
            mailLogRepository.save(mailLog);
        }
    }
}
