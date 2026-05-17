package com.lynx.apigateway.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bot")
public class BotController {

    private final RestClient restClient;
    private final String botServiceUrl;

    public BotController(
            RestClient.Builder restClientBuilder,
            @Value("${services.bot.url}") String botServiceUrl
    ) {
        this.restClient = restClientBuilder.build();
        this.botServiceUrl = botServiceUrl;
    }

    record BotStartBody(@JsonProperty("starting_sum") BigDecimal startingSum) {}

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(
            Authentication auth,
            HttpServletRequest request,
            @RequestBody BotStartBody body
    ) {
        String userId = auth.getName();
        String token  = extractToken(request);

        BigDecimal startingSum = body.startingSum() != null ? body.startingSum() : BigDecimal.ZERO;

        Map<String, Object> forwardBody = new HashMap<>();
        forwardBody.put("user_id", userId);
        forwardBody.put("token", token);
        forwardBody.put("starting_sum", startingSum);

        Map<String, Object> response = restClient.post()
                .uri(botServiceUrl + "/start")
                .body(forwardBody)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return ResponseEntity.ok(response);
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop(Authentication auth) {
        String userId = auth.getName();

        Map<String, Object> response = restClient.post()
                .uri(botServiceUrl + "/stop/{userId}", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(Authentication auth) {
        String userId = auth.getName();

        Map<String, Object> response = restClient.get()
                .uri(botServiceUrl + "/status/{userId}", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return ResponseEntity.ok(response);
    }

    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("jwt".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return "";
    }
}
