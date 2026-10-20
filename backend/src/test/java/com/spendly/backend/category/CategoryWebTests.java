package com.spendly.backend.category;

import com.spendly.backend.auth.SecurityConfig;
import com.spendly.backend.auth.SessionUser;
import com.spendly.backend.common.ApiErrorWriter;
import com.spendly.backend.common.ApiException;
import com.spendly.backend.common.ApiExceptionHandler;
import com.spendly.backend.domain.TransactionType;
import com.spendly.backend.user.User;
import com.spendly.backend.user.UserRepository;
import java.time.Instant;
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

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, ApiErrorWriter.class, ApiExceptionHandler.class})
class CategoryWebTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private CategoryService service;
    @MockitoBean private UserRepository users;
    private UUID ownerId;
    private SessionUser principal;

    @BeforeEach
    void owner() {
        User user = new User("test@example.invalid", "test-only-placeholder");
        ownerId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", ownerId);
        principal = new SessionUser(user);
        principal.eraseCredentials();
    }

    @Test
    void createsUsingPrincipalAndNormalizesName() throws Exception {
        CategoryResponse response = new CategoryResponse(UUID.randomUUID(), "food", TransactionType.EXPENSE, false, Instant.now());
        when(service.create(eq(ownerId), any())).thenReturn(response);
        mvc.perform(post("/api/categories").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  food  \",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("food"))
                .andExpect(jsonPath("$.archived").value(false)).andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.user_id").doesNotExist());
        verify(service).create(ownerId, new CreateCategoryRequest("food", "EXPENSE"));
    }

    @Test
    void rejectsInvalidOrUnsupportedFields() throws Exception {
        for (String body : List.of("{}", "{\"name\":null,\"type\":\"EXPENSE\"}",
                "{\"name\":\"   \",\"type\":\"EXPENSE\"}",
                "{\"name\":\"" + "x".repeat(51) + "\",\"type\":\"EXPENSE\"}",
                "{\"name\":\"food\",\"type\":\"OTHER\"}", "{\"name\":\"food\",\"type\":1}",
                "{\"name\":123,\"type\":\"EXPENSE\"}",
                "{\"name\":\"food\",\"type\":\"EXPENSE\",\"user_id\":\"forged\"}",
                "{\"name\":\"food\",\"type\":\"EXPENSE\",\"archived\":true}")) {
            mvc.perform(post("/api/categories").with(user(principal)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        for (String body : List.of("{}", "{\"name\":null}", "{\"name\":\" \"}",
                "{\"name\":\"new\",\"type\":\"INCOME\"}", "{\"name\":\"new\",\"archived\":false}",
                "{\"name\":123}",
                "{\"name\":\"new\",\"userId\":\"forged\"}")) {
            mvc.perform(patch("/api/categories/{id}", UUID.randomUUID()).with(user(principal)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/categories").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"food\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/categories").with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/categories/{id}", UUID.randomUUID()).with(user(principal)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/categories/{id}", UUID.randomUUID()).with(user(principal)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void routesListFiltersAndRejectsMalformedParameters() throws Exception {
        when(service.list(ownerId, false)).thenReturn(List.of());
        when(service.list(ownerId, true)).thenReturn(List.of());
        mvc.perform(get("/api/categories").with(user(principal)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/categories?includeArchived=true").with(user(principal))).andExpect(status().isOk());
        mvc.perform(get("/api/categories?includeArchived=invalid").with(user(principal)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(delete("/api/categories/not-a-uuid").with(user(principal)).with(csrf()))
                .andExpect(status().isBadRequest());
        verify(service).list(ownerId, false);
        verify(service).list(ownerId, true);
        verifyNoMoreInteractions(service);
    }

    @Test
    void returnsSanitizedNotFoundAndDuplicateErrors() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.rename(eq(ownerId), eq(id), any())).thenThrow(
                new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "카테고리를 찾을 수 없습니다."));
        when(service.create(eq(ownerId), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "CATEGORY_ALREADY_EXISTS", "같은 유형에 이미 사용 중인 이름입니다."));
        mvc.perform(patch("/api/categories/{id}", id).with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"food\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        mvc.perform(post("/api/categories").with(user(principal)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"food\",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));
    }
}
