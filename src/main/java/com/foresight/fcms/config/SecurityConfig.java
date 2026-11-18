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
        var adminLogin = new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
        adminLogin.setDefaultTargetUrl("/dashboard");
        adminLogin.setAlwaysUseDefaultTargetUrl(true);
        var staffLogin = new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
        staffLogin.setDefaultTargetUrl("/staff/dashboard");
        staffLogin.setAlwaysUseDefaultTargetUrl(true);
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
                                        .requestMatchers("/accounting/books", "/accounting/books/**",
                                                "/accounting/periods", "/accounting/periods/**",
                                                "/accounting/equity-reporting", "/accounting/equity-reporting/**")
                                        .authenticated()
                                        .requestMatchers("/accounting/dashboard")
                                        .hasAuthority("workspace.accounting")
                                        .requestMatchers("/accounting/config/**", "/accounting/sources/**", "/accounting/accounts", "/accounting/accounts/new", "/accounting/journals", "/accounting/journals/new", "/accounting/journals/*/edit", "/accounting/vouchers/**")
                                        .hasAuthority("workspace.accounting")
                                        .requestMatchers("/accounting/**")
                                        .hasAuthority("accounting.read")
                                        .requestMatchers("/admin/dashboard")
                                        .hasAuthority("workspace.admin")
                                        .requestMatchers("/staff/dashboard")
                                        .hasAuthority("workspace.staff")
                                        .requestMatchers("/director/dashboard")
                                        .hasAuthority("workspace.director")
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
                                        .requestMatchers("/finance/expenses/**", "/finance/income/**",
                                                "/finance/cloud-backup/billing/**", "/reports/**")
                                        .authenticated()
                                        .requestMatchers("/finance/**")
                                        .hasAuthority("finance.read")
                                        .requestMatchers("/backups/**")
                                        .hasAuthority("backup.manage")
                                        .requestMatchers("/lookups/**")
                                        .authenticated()
                                        .requestMatchers("/*.php")
                                        .hasAuthority("client.read")
                                        .requestMatchers("/dashboard", "/", "/locale", "/form-session")
                                        .authenticated()
                                        .anyRequest()
                                        .denyAll())
                .sessionManagement(session -> session.maximumSessions(5).sessionRegistry(sessions))
                .exceptionHandling(errors -> errors.accessDeniedHandler(
                        new com.foresight.fcms.auth.UserFormCsrfFailureHandler()))
                .formLogin(
                        form ->
                                form.loginPage("/login")
                                        .loginProcessingUrl("/login")
                                        .usernameParameter("email")
                                        .successHandler((request, response, authentication) -> {
                                            boolean admin = authentication.getAuthorities().stream()
                                                    .anyMatch(a -> java.util.Set.of("workspace.admin","workspace.accounting","workspace.director").contains(a.getAuthority()));
                                            (admin ? adminLogin : staffLogin)
                                                    .onAuthenticationSuccess(request, response, authentication);
                                        })
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
