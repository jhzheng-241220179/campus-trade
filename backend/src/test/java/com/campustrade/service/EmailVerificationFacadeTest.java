
package com.campustrade.service;

import com.campustrade.exception.VerificationCodeLimitException;
import com.campustrade.mapper.UserMapper;
import com.campustrade.model.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
