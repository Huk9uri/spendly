package com.spendly.backend.transaction;

import com.spendly.backend.auth.SessionUser;
import com.spendly.backend.common.ApiException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final TransactionOperations operations;

    public TransactionController(TransactionService service, TransactionOperations operations) {
        this.service = service;
        this.operations = operations;
    }

    @GetMapping
    public TransactionPage list(@AuthenticationPrincipal SessionUser user,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredOn,desc") String sort) {
        return operations.list(user.getId(), TransactionListQuery.parse(month, type, categoryId, q, page, size, sort));
    }

    @PatchMapping("/{id}")
    public TransactionResponse patch(@AuthenticationPrincipal SessionUser user, @PathVariable String id,
            @RequestBody PatchTransactionRequest request) {
        return operations.patch(user.getId(), parseId(id), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal SessionUser user, @PathVariable String id) {
        operations.delete(user.getId(), parseId(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal SessionUser user,
            @Valid @RequestBody CreateTransactionRequest request) {
        return service.create(user.getId(), request);
    }

    @GetMapping("/{id}")
    public TransactionResponse detail(@AuthenticationPrincipal SessionUser user, @PathVariable String id) {
        return service.detail(user.getId(), parseId(id));
    }

    private UUID parseId(String id) {
        UUID parsed;
        try {
            parsed = UUID.fromString(id);
            if (!parsed.toString().equalsIgnoreCase(id)) { throw new IllegalArgumentException(); }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "거래 ID 형식이 올바르지 않습니다.");
        }
        return parsed;
    }
}
