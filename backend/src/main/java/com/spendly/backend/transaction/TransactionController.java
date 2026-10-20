package com.spendly.backend.transaction;

import com.spendly.backend.auth.SessionUser;
import com.spendly.backend.common.ApiException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal SessionUser user,
            @Valid @RequestBody CreateTransactionRequest request) {
        return service.create(user.getId(), request);
    }

    @GetMapping("/{id}")
    public TransactionResponse detail(@AuthenticationPrincipal SessionUser user, @PathVariable String id) {
        UUID parsed;
        try {
            parsed = UUID.fromString(id);
            if (!parsed.toString().equalsIgnoreCase(id)) { throw new IllegalArgumentException(); }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "거래 ID 형식이 올바르지 않습니다.");
        }
        return service.detail(user.getId(), parsed);
    }
}
