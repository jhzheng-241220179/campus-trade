
package com.campustrade.service;

import com.campustrade.exception.VerificationCodeLimitException;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationFacadeTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private VerificationCodeService verificationCodeService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailVerificationFacade facade;

    private User createPendingUser() {
        User user = new User();
        user.setId(1L);
        user.setEmail("student@example.com");
        user.setStatus("PENDING_VERIFICATION");
        user.setEmailVerified(false);
        return user;
    }

    @Test
    void nonexistentEmailShouldNotSendCode() {

        facade.sendCode("unknown@example.com");

        verify(userMapper).findByEmail("unknown@example.com");
        verifyNoInteractions(
                verificationCodeService,
                emailService
        );
    }

    @Test
    void verifiedUserShouldNotSendCode() {

        User user = createPendingUser();
        user.setStatus("ACTIVE");
        user.setEmailVerified(true);

        when(userMapper.findByEmail("student@example.com"))
                .thenReturn(user);

        facade.sendCode("student@example.com");

        verifyNoInteractions(
                verificationCodeService,
                emailService
        );
    }

    @Test
    void rateLimitedUserShouldNotSendEmail() {

        User user = createPendingUser();

        when(userMapper.findByEmail("student@example.com"))
                .thenReturn(user);

        when(verificationCodeService.issueCode(1L))
                .thenThrow(new VerificationCodeLimitException(
                        "验证码申请过于频繁"
                ));

        assertDoesNotThrow(() ->
                facade.sendCode("student@example.com")
        );

        verify(verificationCodeService).issueCode(1L);
        verifyNoInteractions(emailService);
    }

    @Test
    void pendingUserShouldReceiveCode() {

        User user = createPendingUser();

        when(userMapper.findByEmail("student@example.com"))
                .thenReturn(user);

        when(verificationCodeService.issueCode(1L))
                .thenReturn(
                        new VerificationCodeService.IssuedCode(
                                20L,
                                "123456",
                                LocalDateTime.now().plusMinutes(5)
                        )
                );

        facade.sendCode("student@example.com");

        verify(verificationCodeService).issueCode(1L);

        verify(emailService).sendVerificationCode(
                "student@example.com",
                "123456"
        );
    }

    @Test
    void emailShouldBeNormalizedBeforeLookup() {

        facade.sendCode("  STUDENT@EXAMPLE.COM  ");

        verify(userMapper).findByEmail(
                "student@example.com"
        );
    }

@Test
void mailFailureShouldInvalidateIssuedCode() {

    User user = createPendingUser();

    when(userMapper.findByEmail("student@example.com"))
            .thenReturn(user);

    when(verificationCodeService.issueCode(1L))
            .thenReturn(
                    new VerificationCodeService.IssuedCode(
                            20L,
                            "123456",
                            LocalDateTime.now().plusMinutes(5)
                    )
            );

    // 模拟 SMTP 邮件发送失败
    doThrow(new MailSendException("模拟 SMTP 发送失败"))
            .when(emailService)
            .sendVerificationCode(
                    "student@example.com", "123456"
            );

    // 邮件异常必须向上抛出
    assertThrows(
            MailSendException.class,
            () -> facade.sendCode("student@example.com")
    );

    // 必须精确作废本次签发的验证码
    verify(verificationCodeService)
            .invalidateFailedDelivery(1L, 20L);
}

@Test
void cleanupFailureShouldPreserveMailException() {

    User user = createPendingUser();

    when(userMapper.findByEmail("student@example.com"))
            .thenReturn(user);

    when(verificationCodeService.issueCode(1L))
            .thenReturn(
                    new VerificationCodeService.IssuedCode(
                            20L,
                            "123456",
                            LocalDateTime.now().plusMinutes(5)
                    )
            );

    doThrow(new MailSendException("SMTP 发送失败"))
            .when(emailService)
            .sendVerificationCode(
                    "student@example.com", "123456"
            );

    // 模拟补偿操作也发生异常
    doThrow(new IllegalStateException("数据库补偿失败"))
            .when(verificationCodeService)
            .invalidateFailedDelivery(1L, 20L);

    MailSendException exception = assertThrows(
            MailSendException.class,
            () -> facade.sendCode("student@example.com")
    );

    // 主异常仍然是邮件发送异常
    assertEquals("SMTP 发送失败",
            exception.getMessage());

    // 补偿失败作为附加异常保存
    assertEquals(1,
            exception.getSuppressed().length);

    assertEquals("数据库补偿失败",
            exception.getSuppressed()[0].getMessage());
}

}
