package com.northstar.api.auth;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@NullMarked
@ConfigurationProperties(prefix = "northstar.auth")
public class AuthProperties {

    private boolean enabled = true;

    private String username = "";

    private String passwordHash = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    void requireConfigured() {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(passwordHash)) {
            throw new IllegalStateException(
                    "northstar.auth.username and northstar.auth.password-hash must be set when auth is enabled");
        }
    }
}
