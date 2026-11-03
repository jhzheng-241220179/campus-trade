
package com.campustrade.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(
                mailSender,
                "test@example.com"
        );
    }

    @Test
    void shouldSendVerificationCodeSuccessfully() {
        emailService.sendVerificationCode(
                "student@example.com",
                "123456"
        );

        var captor = org.mockito.ArgumentCaptor.forClass(
                SimpleMailMessage.class
        );

        verify(mailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertEquals("test@example.com", message.getFrom());

        assertArrayEquals(
                new String[]{"student@example.com"},
                message.getTo()
        );

        assertEquals(
                "CampusTrade 邮箱验证码",
                message.getSubject()
        );

        assertNotNull(message.getText());
        assertTrue(message.getText().contains("123456"));
        assertTrue(message.getText().contains("5 分钟"));
    }

    @Test
    void shouldRejectInvalidVerificationCode() {
        assertThrows(
                IllegalArgumentException.class,
                () -> emailService.sendVerificationCode(
                        "student@example.com",
                        "123"
                )
        );

        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldRejectEmptyRecipient() {
        assertThrows(
                IllegalArgumentException.class,
                () -> emailService.sendVerificationCode(
                        "",
                        "123456"
                )
        );

        verifyNoInteractions(mailSender);
    }

    @Test
    void shouldPropagateMailSendingFailure() {
        doThrow(new org.springframework.mail.MailSendException(
                "模拟 SMTP 发送失败"
        )).when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(
                org.springframework.mail.MailSendException.class,
                () -> emailService.sendVerificationCode(
                        "student@example.com",
                        "123456"
                )
        );
    }
}
