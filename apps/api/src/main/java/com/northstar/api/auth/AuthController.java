package com.northstar.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.AuthenticationManager;

@NullMarked
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthenticationManager authenticationManager;

    private final SecurityContextRepository securityContextRepository;

    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/me")
    @Operation(operationId = "getAuthSession")
    AuthSession me(@Nullable Authentication authentication, @Parameter(hidden = true) @Nullable CsrfToken csrfToken) {
        return sessionOf(authentication);
    }

    @GetMapping("/csrf")
    @Operation(operationId = "getCsrfToken")
    CsrfTokenResponse csrf(@Parameter(hidden = true) @Nullable CsrfToken csrfToken) {
        if (csrfToken == null) {
            return new CsrfTokenResponse("", "", "");
        }
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    @Operation(operationId = "login")
    AuthSession login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Authentication authenticated;
        try {
            authenticated = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password", e);
        }

        SecurityContext context = new SecurityContextImpl(authenticated);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return sessionOf(authenticated);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "logout")
    void logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    private AuthSession sessionOf(@Nullable Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return new AuthSession(false, null);
        }
        return new AuthSession(true, authentication.getName());
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record AuthSession(boolean authenticated, @Nullable String username) {
    }

    record CsrfTokenResponse(String headerName, String parameterName, String token) {
    }
}
