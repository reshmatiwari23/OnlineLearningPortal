package com.olp.course.config;
 
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
 
/**
 * Security config for course-service.
 *
 * JWT validation is done at API Gateway — not here.
 * All requests arriving at this service are already authenticated.
 * Role checking (instructor vs learner) is done in CourseService
 * using the x-user-role header injected by API Gateway.
 *
 * Locally: Vite proxy forwards requests without JWT — we permit all.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
 
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                // All course API requests permitted — role checking done in service layer
                .anyRequest().permitAll()
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable()) // needed for H2 console
            );
 
        return http.build();
    }
}
 
 