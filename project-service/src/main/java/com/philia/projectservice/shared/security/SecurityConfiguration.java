package com.philia.projectservice.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            GatewayHeaderAuthenticationFilter gatewayHeaderAuthenticationFilter,
            ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
            ApiAccessDeniedHandler apiAccessDeniedHandler
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler)
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/v1/projects").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/projects/*/publish").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/projects/*/archive").authenticated()
                        .requestMatchers(HttpMethod.POST, "/v1/projects/*/reopen").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/v1/projects/*/visibility").authenticated()
                        .requestMatchers(HttpMethod.GET, "/v1/me/projects").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/v1/projects/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/v1/projects/*").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(gatewayHeaderAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
