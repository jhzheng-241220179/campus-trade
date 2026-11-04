
package com.campustrade.controller;

import com.campustrade.exception.GlobalExceptionHandler;
import com.campustrade.service.EmailVerificationFacade;
import com.campustrade.service.VerificationCodeService;
import com.campustrade.exception.VerificationCodeLimitException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private EmailVerificationFacade facade;

    private MockMvc mockMvc;

    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(facade))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void sendCodeShouldReturnSuccess() throws Exception {

        mockMvc.perform(post("/api/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"student@example.com"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(facade).sendCode("student@example.com");
    }

    @Test
    void verifyEmailShouldReturnSuccess() throws Exception {

        when(facade.verifyEmail(
                "student@example.com", "123456"))
            .thenReturn(
                VerificationCodeService.VerifyResult.SUCCESS
            );

        mockMvc.perform(post("/api/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email":"student@example.com",
                      "code":"123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void wrongCodeShouldReturnBadRequest() throws Exception {

        when(facade.verifyEmail(
                "student@example.com", "654321"))
            .thenReturn(
                VerificationCodeService.VerifyResult.WRONG_CODE
            );

        mockMvc.perform(post("/api/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email":"student@example.com",
                      "code":"654321"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void invalidEmailShouldReturnBadRequest() throws Exception {

        mockMvc.perform(post("/api/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"invalid-email"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));

        verifyNoInteractions(facade);
    }

@Test
void sendCodeTooFrequentlyShouldReturn429() throws Exception {

    doThrow(new VerificationCodeLimitException(
            "验证码申请过于频繁，请稍后重试"
    )).when(facade).sendCode("student@example.com");

    mockMvc.perform(post("/api/auth/email/send-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"student@example.com"}
                """))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value(429))
        .andExpect(jsonPath("$.message").value(
                "验证码请求暂时受限，请稍后重试"
        ));

    verify(facade).sendCode("student@example.com");
}

@Test
void unexpectedExceptionShouldReturn500() throws Exception {

    doThrow(new IllegalStateException("数据库内部状态异常"))
            .when(facade).sendCode("student@example.com");

    mockMvc.perform(post("/api/auth/email/send-code")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"student@example.com"}
                """))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value(500))
        .andExpect(jsonPath("$.message").value("服务器内部错误"));
}

}
