package com.northstar.core.brief;

import jakarta.validation.constraints.NotBlank;

public record BriefTopicCount(@NotBlank String topic, int count) {
}
