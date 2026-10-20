package com.spendly.backend.transaction;

import com.spendly.backend.auth.SecurityConfig;
import com.spendly.backend.auth.SessionUser;
import com.spendly.backend.common.ApiErrorWriter;
import com.spendly.backend.common.ApiException;
import com.spendly.backend.common.ApiExceptionHandler;
import com.spendly.backend.domain.TransactionType;
import com.spendly.backend.user.User;
import com.spendly.backend.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransactionController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiExceptionHandler.class})
class TransactionWebTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private TransactionService service;
    @MockitoBean private UserRepository users;
    private UUID ownerId;
    private UUID categoryId;
    private UUID transactionId;
    private SessionUser principal;

    @BeforeEach
    void identity() {
        User user = new User("test@example.invalid", "test-only-placeholder");
        ownerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        transactionId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", ownerId);
        principal = new SessionUser(user);
        principal.eraseCredentials();
    }

    @Test
    void missingAndNullMemoBecomeEmptyAndDescriptionIsTrimmed() throws Exception {
        when(service.create(eq(ownerId), any())).thenAnswer(call -> response(call.getArgument(1)));
        for (String memo : List.of("", ",\"memo\":null")) {
            mvc.perform(post("/api/transactions").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("12000", "2026-10-21", "  lunch  ", memo)))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("lunch"))
                    .andExpect(jsonPath("$.memo").value(""))
                    .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.userId").doesNotExist())
                    .andExpect(jsonPath("$.user_id").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        }
        verify(service, times(2)).create(ownerId, new CreateTransactionRequest(TransactionType.EXPENSE,
                new BigDecimal("12000"), categoryId, LocalDate.of(2026, 10, 21), "lunch", ""));
    }

    @Test
    void preservesMemoAndAcceptsExactAmountAndDateBoundaries() throws Exception {
        when(service.create(eq(ownerId), any())).thenAnswer(call -> response(call.getArgument(1)));
        for (String amount : List.of("1", "999999999999999")) {
            mvc.perform(post("/api/transactions").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body(amount, "2024-02-29", "x".repeat(100), ",\"memo\":\"  note  \"")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.memo").value("  note  "))
                    .andExpect(jsonPath("$.occurredOn").value("2024-02-29"));
        }
    }

    @Test
    void rejectsInvalidAmountsBeforeCallingService() throws Exception {
        for (String amount : List.of("0", "-1", "1000000000000000", "\"12000\"", "12.5", "12000.0", "1e3", "null", "true", "{}")) {
            mvc.perform(post("/api/transactions").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body(amount, "2026-10-21", "lunch", ""))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingInvalidOrUnsupportedFields() throws Exception {
        String valid = body("12000", "2026-10-21", "lunch", "");
        for (String json : List.of("{}", valid.replace("\"EXPENSE\"", "\"OTHER\""),
                valid.replace("\"EXPENSE\"", "1"), valid.replace(categoryId.toString(), "1-1-1-1-1"),
                valid.replace("\"" + categoryId + "\"", "null"), valid.replace("\"lunch\"", "null"),
                valid.replace("\"lunch\"", "123"), body("12000", "2026-10-21", " ", ""),
                body("12000", "2026-10-21", "x".repeat(101), ""),
                body("12000", "2026-10-21", "lunch", ",\"memo\":123"),
                body("12000", "2026-10-21", "lunch", ",\"memo\":\"" + "x".repeat(1001) + "\""),
                body("12000", "2026-10-21", "lunch", ",\"user_id\":\"forged\""),
                body("12000", "2026-10-21", "lunch", ",\"userId\":\"forged\""),
                body("12000", "2026-10-21", "lunch", ",\"version\":5"))) {
            mvc.perform(post("/api/transactions").with(user(principal)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        }
        for (String date : List.of("2026-02-29", "2026-13-01", "2026-2-01", "0000-01-01", "2026-10-21T00:00:00Z")) {
            mvc.perform(post("/api/transactions").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("12000", date, "lunch", ""))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/transactions/{id}", transactionId)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/transactions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("12000", "2026-10-21", "lunch", ""))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/transactions").with(user(principal)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("12000", "2026-10-21", "lunch", "")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void returnsOwnerScopedDetailAndValidatesPath() throws Exception {
        when(service.detail(ownerId, transactionId)).thenReturn(response(new CreateTransactionRequest(TransactionType.EXPENSE,
                new BigDecimal("12000"), categoryId, LocalDate.of(2026, 10, 21), "lunch", "")));
        mvc.perform(get("/api/transactions/{id}", transactionId).with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(transactionId.toString()))
                .andExpect(jsonPath("$.amount").value(12000)).andExpect(jsonPath("$.userId").doesNotExist());
        mvc.perform(get("/api/transactions/not-a-uuid").with(user(principal))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/transactions/1-1-1-1-1").with(user(principal))).andExpect(status().isBadRequest());
        verify(service).detail(ownerId, transactionId);
        verifyNoMoreInteractions(service);
    }

    @Test
    void mapsBusinessFailuresWithoutExposingInput() throws Exception {
        when(service.detail(ownerId, transactionId)).thenThrow(new ApiException(HttpStatus.NOT_FOUND,
                "TRANSACTION_NOT_FOUND", "거래를 찾을 수 없습니다."));
        mvc.perform(get("/api/transactions/{id}", transactionId).with(user(principal)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
        for (String code : List.of("CATEGORY_NOT_FOUND", "CATEGORY_TYPE_MISMATCH", "CATEGORY_ARCHIVED")) {
            HttpStatus status = code.equals("CATEGORY_NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            doThrow(new ApiException(status, code, "카테고리를 확인해 주세요.")).when(service).create(eq(ownerId), any());
            mvc.perform(post("/api/transactions").with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(body("12000", "2026-10-21", "lunch", "")))
                    .andExpect(status().is(status.value())).andExpect(jsonPath("$.code").value(code));
        }
    }

    private String body(String amount, String date, String description, String extra) {
        return "{\"type\":\"EXPENSE\",\"amount\":" + amount + ",\"categoryId\":\"" + categoryId
                + "\",\"occurredOn\":\"" + date + "\",\"description\":\"" + description + "\"" + extra + "}";
    }

    private TransactionResponse response(CreateTransactionRequest request) {
        return new TransactionResponse(transactionId, request.type(), request.amount(), request.categoryId(),
                request.occurredOn(), request.description(), request.memo(), 0);
    }
}
