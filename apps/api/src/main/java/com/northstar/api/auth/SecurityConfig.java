package com.northstar.api.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@NullMarked
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties({AuthProperties.class, MobileAuthProperties.class, CorsProperties.class})
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties auth,
            MobileAuthProperties mobileAuth, CorsProperties corsProperties,
            SecurityContextRepository securityContextRepository,
            ObjectProvider<JwtDecoder> jwtDecoderProvider) throws Exception {
        if (mobileAuth.enabled() && !auth.enabled()) {
            throw new IllegalStateException("Mobile auth requires northstar.auth.enabled=true");
        }
        List<String> allowedOrigins = corsProperties.origins();
        if (!auth.enabled()) {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }

        if (!allowedOrigins.isEmpty()) {
            CorsConfiguration corsConfiguration = new CorsConfiguration();
            corsConfiguration.setAllowedOrigins(allowedOrigins);
            corsConfiguration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            corsConfiguration.setAllowedHeaders(
                    List.of("Authorization", "Content-Type", "Accept", "X-Timezone"));
            corsConfiguration.setAllowCredentials(false);
            corsConfiguration.setMaxAge(3600L);
            UrlBasedCorsConfigurationSource corsSource = new UrlBasedCorsConfigurationSource();
            corsSource.registerCorsConfiguration("/api/**", corsConfiguration);
            http.cors(cors -> cors.configurationSource(corsSource));
        }

        http
                .csrf(csrf -> csrf
                        .spa()
                        .ignoringRequestMatchers("/api/auth/mobile/**"))
                .securityContext(securityContext -> securityContext.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/auth/me", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/mobile/login", "/api/auth/mobile/refresh",
                                "/api/auth/mobile/logout").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/v3/api-docs", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/", "/index.html", "/assets/**", "/logo.png", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, HttpStatus.FORBIDDEN, "Access denied")));

        if (mobileAuth.enabled()) {
            JwtDecoder decoder = jwtDecoderProvider.getIfAvailable();
            if (decoder == null) {
                throw new IllegalStateException("Mobile auth is enabled but no JwtDecoder is configured");
            }
            http.oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(decoder)));
        }

        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    UserDetailsService userDetailsService(AuthProperties auth) {
        if (!auth.enabled()) {
            UserDetails disabled = User.withUsername("disabled")
                    .password("{noop}disabled")
                    .roles("USER")
                    .build();
            return new InMemoryUserDetailsManager(disabled);
        }

        auth.requireConfigured();
        UserDetails user = User.withUsername(auth.username())
                .password(auth.passwordHash())
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}
                """.formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
