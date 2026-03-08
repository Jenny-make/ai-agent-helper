package com.example.customerservice.model;

import java.util.List;

public record ReplyResult(
        String answer,
        String provider,
        List<String> citations
) {
}
