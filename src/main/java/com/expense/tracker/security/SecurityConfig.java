package com.expense.tracker.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Simple, same-origin app with no external clients calling the
            // API, so we skip CSRF tokens to keep the login/logout buttons
            // plain links/forms instead of needing token plumbing. If you
            // ever expose this API beyond your own frontend, turn this back on.
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login.html", "/login.css","/style.css",
                    "/oauth2/**", "/login/oauth2/**",
                    "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )

            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login.html")
                .defaultSuccessUrl("/index.html", true)
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
            )

            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html")
                .permitAll()
            );

        return http.build();
    }
}
