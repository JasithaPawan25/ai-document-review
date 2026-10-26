package com.jasitha.docreview.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * Role-based access:
 *  UPLOADER - upload documents and see their own
 *  REVIEWER - see all documents, approve / reject / retry
 *  ADMIN    - everything
 *
 * Uses HTTP Basic with demo users to keep the starter simple. Next step: JWT (see README).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless API with no cookies, so CSRF protection isn't needed. Re-enable it if you add session login.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/documents").hasAnyRole("UPLOADER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/documents/*/approve", "/api/documents/*/reject",
                                "/api/documents/*/retry").hasAnyRole("REVIEWER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/documents/*/audit").hasAnyRole("REVIEWER", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    InMemoryUserDetailsManager users(AppProperties properties, PasswordEncoder encoder) {
        List<UserDetails> users = properties.users().stream()
                .map(u -> User.withUsername(u.username())
                        .password(encoder.encode(u.password()))
                        .roles(u.roles().toArray(String[]::new))
                        .build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }
}
