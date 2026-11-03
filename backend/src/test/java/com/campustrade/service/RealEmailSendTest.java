
package com.campustrade.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Disabled("仅手动执行真实 SMTP 测试")
@SpringBootTest(properties = "spring.flyway.enabled=false")
@ActiveProfiles("test")
class RealEmailSendTest {

    @Autowired
    private EmailService emailService;

    @Test
    void sendRealEmail() {
        String recipient = System.getenv("SMTP_TEST_RECIPIENT");

        assertNotNull(recipient, "请设置 SMTP_TEST_RECIPIENT");
        assertFalse(recipient.isBlank());

        emailService.sendVerificationCode(recipient, "123456");
    }
}
