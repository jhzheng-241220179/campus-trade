
package com.campustrade.config;

import com.campustrade.controller.AuthController;
import com.campustrade.service.EmailVerificationFacade;
import com.campustrade.service.VerificationCodeService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmailVerificationFacade facade;

    @Test
    void anonymousCanSendCodeWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"student@example.com\"}"))
            .andExpect(status().isOk());

        verify(facade).sendCode("student@example.com");
    }

    @Test
    void anonymousCanVerifyEmailWithoutCsrf() throws Exception {
        when(facade.verifyEmail(
                "student@example.com", "123456"))
            .thenReturn(VerificationCodeService.VerifyResult.SUCCESS);

        mockMvc.perform(post("/api/auth/email/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "student@example.com",
                      "code": "123456"
                    }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    void anonymousCannotAccessMe() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void otherPostRequestsStillRequireCsrf() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}
