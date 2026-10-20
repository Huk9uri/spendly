package com.spendly.backend.category;

import com.spendly.backend.auth.SessionUser;
import com.spendly.backend.common.ApiException;
import com.spendly.backend.domain.TransactionType;
import com.spendly.backend.transaction.Transaction;
import com.spendly.backend.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class CategoryIntegrationTests {
    @Autowired private CategoryService service;
    @Autowired private CategoryRepository categories;
    @Autowired private MockMvc mvc;
    @PersistenceContext private EntityManager entityManager;
    private User owner;
    private User other;

    @BeforeEach
    void persistUsers() {
        owner = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
        other = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
        entityManager.persist(owner);
        entityManager.persist(other);
        entityManager.flush();
    }

    @Test
    void createsRenamesAndArchivesThroughAuthenticatedHttp() throws Exception {
        MockHttpSession session = session(owner);
        mvc.perform(post("/api/categories").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  food  \",\"type\":\"EXPENSE\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("food"));
        Category created = categories.findAllByUserId(owner.getId()).get(0);
        UUID id = created.getId();
        entityManager.clear();
        mvc.perform(patch("/api/categories/{id}", id).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  dining  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("dining"))
                .andExpect(jsonPath("$.type").value("EXPENSE"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/categories/{id}", id).session(session).with(csrf()))
                    .andExpect(status().isNoContent());
        }
        entityManager.clear();
        assertTrue(categories.findByIdAndUserId(id, owner.getId()).orElseThrow().isArchived());
        mvc.perform(get("/api/categories").session(session)).andExpect(content().json("[]"));
        mvc.perform(get("/api/categories?includeArchived=true").session(session))
                .andExpect(jsonPath("$[0].id").value(id.toString())).andExpect(jsonPath("$[0].archived").value(true));
        mvc.perform(patch("/api/categories/{id}", id).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"archived-name\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void enforcesDuplicateScopeEvenAfterArchiving() {
        CategoryResponse first = create(owner, "food", "EXPENSE");
        service.rename(owner.getId(), first.id(), new RenameCategoryRequest("food"));
        assertConflict(() -> create(owner, "  food  ", "EXPENSE"));
        create(owner, "food", "INCOME");
        create(other, "food", "EXPENSE");
        create(owner, "Food", "EXPENSE");
        CategoryResponse second = create(owner, "transport", "EXPENSE");
        assertConflict(() -> service.rename(owner.getId(), second.id(), new RenameCategoryRequest("food")));
        service.archive(owner.getId(), first.id());
        assertConflict(() -> create(owner, "food", "EXPENSE"));
    }

    @Test
    void twoSessionsCannotModifyOrArchiveEachOthersResources() throws Exception {
        UUID id = create(owner, "private", "EXPENSE").id();
        MockHttpSession otherSession = session(other);
        mvc.perform(get("/api/categories?includeArchived=true").session(otherSession)).andExpect(content().json("[]"));
        for (UUID inaccessible : List.of(id, UUID.randomUUID())) {
            mvc.perform(patch("/api/categories/{id}", inaccessible).session(otherSession).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"stolen\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
            mvc.perform(delete("/api/categories/{id}", inaccessible).session(otherSession).with(csrf()))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        }
        mvc.perform(post("/api/categories").session(otherSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"forged\",\"type\":\"EXPENSE\",\"user_id\":\"" + owner.getId() + "\"}"))
                .andExpect(status().isBadRequest());
        entityManager.clear();
        Category unchanged = categories.findByIdAndUserId(id, owner.getId()).orElseThrow();
        assertEquals("private", unchanged.getName());
        assertFalse(unchanged.isArchived());
        assertTrue(categories.findAllByUserId(other.getId()).isEmpty());
    }

    @Test
    void listsOnlyOwnerWithArchiveFilterAndStableNameIdOrder() {
        UUID last = create(owner, "다과", "EXPENSE").id();
        UUID first = create(owner, "가계", "EXPENSE").id();
        UUID sameName = create(owner, "가계", "INCOME").id();
        UUID archived = create(owner, "나들이", "EXPENSE").id();
        create(other, "other-user", "EXPENSE");
        service.archive(owner.getId(), archived);
        entityManager.clear();
        List<UUID> ties = List.of(first, sameName).stream().sorted(Comparator.comparing(UUID::toString)).toList();
        List<UUID> activeIds = service.list(owner.getId(), false).stream().map(CategoryResponse::id).toList();
        assertEquals(List.of(ties.get(0), ties.get(1), last), activeIds);
        List<UUID> allIds = service.list(owner.getId(), true).stream().map(CategoryResponse::id).toList();
        assertEquals(List.of(ties.get(0), ties.get(1), archived, last), allIds);
        assertTrue(service.list(UUID.randomUUID(), true).isEmpty());
    }

    @Test
    void renameAndArchivePreserveExistingTransactionReferences() {
        UUID id = create(owner, "food", "EXPENSE").id();
        Transaction transaction = new Transaction(owner.getId(), id, TransactionType.EXPENSE,
                new BigDecimal("12000"), LocalDate.of(2026, 10, 20), "test transaction", "");
        entityManager.persist(transaction);
        entityManager.flush();
        UUID transactionId = transaction.getId();
        service.rename(owner.getId(), id, new RenameCategoryRequest("dining"));
        service.archive(owner.getId(), id);
        entityManager.clear();
        assertEquals(id, entityManager.find(Transaction.class, transactionId).getCategoryId());
        Category category = categories.findByIdAndUserId(id, owner.getId()).orElseThrow();
        assertEquals("dining", category.getName());
        assertTrue(category.isArchived());
    }

    private CategoryResponse create(User user, String name, String type) {
        return service.create(user.getId(), new CreateCategoryRequest(name, type));
    }

    private void assertConflict(Runnable action) {
        ApiException exception = assertThrows(ApiException.class, action::run);
        assertEquals(409, exception.getStatus().value());
        assertEquals("CATEGORY_ALREADY_EXISTS", exception.getCode());
    }

    private MockHttpSession session(User user) {
        SessionUser principal = new SessionUser(user);
        principal.eraseCredentials();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
