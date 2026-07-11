package com.northstar.core.study;

import jakarta.validation.constraints.NotNull;

public record SpeakingQuestion(@NotNull String question) {
}
