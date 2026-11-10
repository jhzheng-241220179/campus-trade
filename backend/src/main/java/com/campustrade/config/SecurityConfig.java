
package com.campustrade.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {
    @Bean
public AuthenticationManager authenticationManager(
        UserDetailsService userDetailsService,
        PasswordEncoder passwordEncoder) {

    DaoAuthenticationProvider provider =
            new DaoAuthenticationProvider(userDetailsService);

    provider.setPasswordEncoder(passwordEncoder);

    return new ProviderManager(provider);
}
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/login"
                ).permitAll()
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/email/send-code",
                    "/api/auth/email/verify"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products")
                    .permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/{id}")
                    .permitAll()
                .anyRequest()
                    .authenticated()
            )
            .csrf(csrf -> csrf.ignoringRequestMatchers(request -> {
                if (!"POST".equalsIgnoreCase(request.getMethod())) {
                    return false;
                }

                String path = request.getRequestURI()
                        .substring(request.getContextPath().length());

                return "/api/auth/email/send-code".equals(path)
                        || "/api/auth/email/verify".equals(path);
            }))
            .httpBasic(withDefaults())
            .formLogin(form -> form.disable());

        return http.build();
    }
}
