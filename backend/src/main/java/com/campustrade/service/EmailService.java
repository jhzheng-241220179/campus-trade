
package com.campustrade.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendVerificationCode(String to, String code) {
        if (to == null || to.isBlank()
                || code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("邮件参数不正确");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("CampusTrade 邮箱验证码");
        message.setText(
                "您的 CampusTrade 邮箱验证码为：" + code
                + "\n\n验证码有效期为 5 分钟。"
                + "\n如果不是您本人操作，请忽略此邮件。"
        );

        mailSender.send(message);
    }
}
