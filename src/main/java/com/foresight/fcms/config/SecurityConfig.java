package com.foresight.fcms.config;

import com.foresight.fcms.auth.LegacyAwarePasswordEncoder;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, org.springframework.security.core.session.SessionRegistry sessions)
            throws Exception {
        http.authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .dispatcherTypeMatchers(
                                                DispatcherType.FORWARD, DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                "/actuator/health",
                                                "/actuator/health/liveness",
                                                "/actuator/health/readiness")
                                        .permitAll()
                                        .requestMatchers(
                                                "/fonts/**",
                                                "/css/**",
                                                "/js/**",
                                                "/img/**",
                                                "/login",
                                                "/error",
                                                "/password/forgot",
                                                "/password/reset")
                                        .permitAll()
                                        .requestMatchers("/accounting/**")
                                        .hasAuthority("accounting.read")
                                        .requestMatchers("/admin/users/**")
                                        .hasAuthority("iam.user.read")
                                        .requestMatchers("/account/**")
                                        .hasAuthority("account.write")
                                        .requestMatchers("/sales/prospects/**")
                                        .hasAuthority("sales.prospect.read")
                                        .requestMatchers("/sales/invoices/**")
                                        .hasAuthority("sales.invoice.read")
                                        .requestMatchers("/clients/**")
                                        .hasAuthority("client.read")
                                        .requestMatchers("/tasks/**")
                                        .hasAuthority("task.read")
                                        .requestMatchers("/leaves/**")
                                        .hasAuthority("leave.read")
                                        .requestMatchers("/staff-reports/**")
                                        .hasAuthority("staff-report.read")
                                        .requestMatchers("/finance/expenses/**")
                                        .hasAuthority("expense.read")
                                        .requestMatchers("/finance/**")
                                        .hasAuthority("finance.read")
                                        .requestMatchers("/reports/**")
                                        .hasAuthority("report.read")
                                        .requestMatchers("/backups/**")
                                        .hasAuthority("backup.manage")
                                        .requestMatchers("/lookups/**")
                                        .authenticated()
                                        .requestMatchers("/dashboard", "/", "/locale", "/*.php")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .sessionManagement(session -> session.maximumSessions(5).sessionRegistry(sessions))
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .loginProcessingUrl("/login")
                                        .usernameParameter("email")
                                        .defaultSuccessUrl("/dashboard", true)
                                        .failureUrl("/login?error")
                                        .permitAll())
                .logout(
                        logout ->
                                logout.logoutUrl("/logout")
                                        .logoutSuccessUrl("/login?logout")
                                        .invalidateHttpSession(true)
                                        .deleteCookies("JSESSIONID"));

        return http.build();
    }

    @Bean
    org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }

    @Bean
    org.springframework.security.web.session.HttpSessionEventPublisher sessionEvents() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }

    @Bean
    PasswordEncoder passwordEncoder(org.springframework.core.env.Environment environment) {
        return new LegacyAwarePasswordEncoder(
                environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod")));
    }
}
