package com.example.customerservice.model;

import jakarta.validation.constraints.NotBlank;

public record CustomerMessage(
        @NotBlank String userId,
        @NotBlank String sessionId,
        @NotBlank String text
) {
}
