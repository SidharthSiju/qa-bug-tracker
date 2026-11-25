package com.duc.crqa.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SwitchUserFilter switchUserFilter)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/vendor/**", "/favicon.ico").permitAll()
                        .requestMatchers("/login", "/error").permitAll()
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/admin/users/**").hasAnyRole("ADMIN", "QA_LEAD")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasAnyRole("ADMIN", "QA_LEAD")
                        .requestMatchers("/reports/qa-stats", "/reports/qa-stats.csv")
                        .hasAnyRole("ADMIN", "CR_MANAGER", "QA_LEAD", "QA_TESTER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**", "/api/**"))
                // Registered after AuthorizationFilter so /admin/impersonate is enforced as ADMIN-only
                // against the current (admin) authentication before the switch executes.
                .addFilterAfter(switchUserFilter, AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public SwitchUserFilter switchUserFilter(ObjectProvider<UserDetailsService> userDetailsServiceProvider) {
        SwitchUserFilter filter = new SwitchUserFilter();
        // Resolve the UserDetailsService lazily per request. Injecting it eagerly would force the JPA
        // EntityManagerFactory to initialise while the servlet container is starting (Filter beans are created
        // very early), i.e. before Flyway/JPA are ready, which fails on a fresh database.
        // Bean-managed so Spring also injects the ApplicationEventPublisher and MessageSource.
        filter.setUserDetailsService(username -> userDetailsServiceProvider.getObject().loadUserByUsername(username));
        filter.setSwitchUserUrl("/admin/impersonate");
        filter.setExitUserUrl("/impersonate/exit");
        filter.setUsernameParameter("username");
        filter.setTargetUrl("/dashboard");
        filter.setSwitchFailureUrl("/dashboard?impersonateError");
        return filter;
    }

    // The SwitchUserFilter is used only inside the Spring Security chain (addFilterAfter below); disable the
    // duplicate plain-servlet-filter registration Spring Boot would otherwise create for any Filter bean.
    @Bean
    public FilterRegistrationBean<SwitchUserFilter> switchUserFilterRegistration(SwitchUserFilter switchUserFilter) {
        FilterRegistrationBean<SwitchUserFilter> registration = new FilterRegistrationBean<>(switchUserFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
