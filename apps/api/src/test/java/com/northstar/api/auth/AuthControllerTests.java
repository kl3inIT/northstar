package com.northstar.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.context.SecurityContextRepository;

class AuthControllerTests {

    @Test
    void csrfEndpointIsSafeWhenSecurityDisablesCsrf() {
        AuthController controller = new AuthController(
                mock(AuthenticationManager.class), mock(SecurityContextRepository.class));

        AuthController.CsrfTokenResponse response = controller.csrf(null);

        assertThat(response.headerName()).isEmpty();
        assertThat(response.parameterName()).isEmpty();
        assertThat(response.token()).isEmpty();
    }
}
