package com.spendly.backend.transaction;

import com.spendly.backend.auth.SessionUser;
import com.spendly.backend.category.Category;
import com.spendly.backend.category.CategoryRepository;
import com.spendly.backend.category.CategoryService;
import com.spendly.backend.domain.TransactionType;
import com.spendly.backend.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class TransactionIntegrationTests {
    @Autowired private TransactionService service;
    @Autowired private CategoryService categoryService;
    @Autowired private TransactionRepository transactions;
    @Autowired private CategoryRepository categories;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @PersistenceContext private EntityManager entityManager;
    private User owner;
    private User other;
    private UUID categoryId;

    @BeforeEach
    void fixture() {
        owner = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
        other = new User(UUID.randomUUID() + "@example.invalid", "test-only-placeholder");
        entityManager.persist(owner);
        entityManager.persist(other);
        entityManager.flush();
        categoryId = categories.saveAndFlush(new Category(owner.getId(), "food", TransactionType.EXPENSE)).getId();
        entityManager.clear();
    }

    @Test
    void persistsAmountDateVersionAndBothEmptyMemoFormsThroughHttp() throws Exception {
        MockHttpSession session = session(owner);
        List<String> amounts = List.of("1", "999999999999999");
        List<String> memos = List.of("", ",\"memo\":null");
        for (int i = 0; i < amounts.size(); i++) {
            String body = "{\"type\":\"EXPENSE\",\"amount\":" + amounts.get(i) + ",\"categoryId\":\"" + categoryId
                    + "\",\"occurredOn\":\"2024-02-29\",\"description\":\"  lunch  \"" + memos.get(i) + "}";
            var result = mvc.perform(post("/api/transactions").session(session).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.memo").value(""))
                    .andExpect(jsonPath("$.description").value("lunch")).andReturn();
            TransactionResponse created = mapper.readValue(result.getResponse().getContentAsString(), TransactionResponse.class);
            entityManager.clear();
            Transaction stored = transactions.findByIdAndUserId(created.id(), owner.getId()).orElseThrow();
            assertEquals(0, new BigDecimal(amounts.get(i)).compareTo(stored.getAmount()));
            assertEquals(LocalDate.of(2024, 2, 29), stored.getOccurredOn());
            assertEquals("", stored.getMemo());
            assertEquals(0, stored.getVersion());
            assertEquals(owner.getId(), stored.getUserId());
            mvc.perform(get("/api/transactions/{id}", created.id()).session(session))
                    .andExpect(status().isOk()).andExpect(content().json(result.getResponse().getContentAsString()));
        }
    }

    @Test
    void deniesOtherUsersDetailAndCategoryAndRejectsForgedOwner() throws Exception {
        TransactionResponse created = service.create(owner.getId(), request(new BigDecimal("12000"), TransactionType.EXPENSE));
        for (UUID id : List.of(created.id(), UUID.randomUUID())) {
            mvc.perform(get("/api/transactions/{id}", id).session(session(other)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
        }
        String body = "{\"type\":\"EXPENSE\",\"amount\":12000,\"categoryId\":\"" + categoryId
                + "\",\"occurredOn\":\"2026-10-21\",\"description\":\"lunch\"}";
        mvc.perform(post("/api/transactions").session(session(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        String forged = body.substring(0, body.length() - 1) + ",\"user_id\":\"" + owner.getId() + "\"}";
        mvc.perform(post("/api/transactions").session(session(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(forged))
                .andExpect(status().isBadRequest());
        assertTrue(transactions.findAllByUserId(other.getId()).isEmpty());
        assertEquals(1, transactions.findAllByUserId(owner.getId()).size());
    }

    @Test
    void deniesMissingAndMismatchedCategoryWithoutInserting() throws Exception {
        String body = "{\"type\":\"INCOME\",\"amount\":12000,\"categoryId\":\"" + categoryId
                + "\",\"occurredOn\":\"2026-10-21\",\"description\":\"income\"}";
        mvc.perform(post("/api/transactions").session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATEGORY_TYPE_MISMATCH"));
        mvc.perform(post("/api/transactions").session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace(categoryId.toString(), UUID.randomUUID().toString())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
        assertTrue(transactions.findAllByUserId(owner.getId()).isEmpty());
    }

    @Test
    void archivedCategoryRejectsNewTransactionButKeepsExistingDetail() throws Exception {
        TransactionResponse created = service.create(owner.getId(), request(new BigDecimal("12000"), TransactionType.EXPENSE));
        categoryService.archive(owner.getId(), categoryId);
        entityManager.clear();
        mvc.perform(get("/api/transactions/{id}", created.id()).session(session(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memo").value(""));
        String body = "{\"type\":\"EXPENSE\",\"amount\":12000,\"categoryId\":\"" + categoryId
                + "\",\"occurredOn\":\"2026-10-21\",\"description\":\"new\"}";
        mvc.perform(post("/api/transactions").session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
        assertEquals(1, transactions.findAllByUserId(owner.getId()).size());
    }

    @Test
    void serviceValidationRejectsFractionAndRangeBeforeDatabaseRounding() {
        for (BigDecimal amount : List.of(new BigDecimal("12.5"), BigDecimal.ZERO, new BigDecimal("1000000000000000"))) {
            assertThrows(ConstraintViolationException.class,
                    () -> service.create(owner.getId(), request(amount, TransactionType.EXPENSE)));
        }
        assertTrue(transactions.findAllByUserId(owner.getId()).isEmpty());
    }

    @Test
    void filtersLiteralSearchPagesAndIsolatesUsers() throws Exception {
        service.create(owner.getId(), new CreateTransactionRequest(TransactionType.EXPENSE, new BigDecimal("100"), categoryId,
                LocalDate.of(2026, 10, 1), "SALE%_\\", "MixedCase"));
        service.create(owner.getId(), new CreateTransactionRequest(TransactionType.EXPENSE, new BigDecimal("200"), categoryId,
                LocalDate.of(2026, 10, 31), "other", ""));
        service.create(owner.getId(), new CreateTransactionRequest(TransactionType.EXPENSE, new BigDecimal("300"), categoryId,
                LocalDate.of(2026, 11, 1), "next month", ""));
        mvc.perform(get("/api/transactions").session(session(owner)).param("month", "2026-10").param("size", "1").param("sort", "amount,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(100));
        for (String q : List.of("%", "_", "\\", "mixedcase")) {
            mvc.perform(get("/api/transactions").session(session(owner)).param("q", q))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        }
        mvc.perform(get("/api/transactions").session(session(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/transactions").session(session(owner)).param("page", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/transactions").session(session(owner)).param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void patchesRetainOmittedValuesClearMemoAndCheckVersions() throws Exception {
        var created = service.create(owner.getId(), request(new BigDecimal("12000"), TransactionType.EXPENSE));
        String path = "/api/transactions/" + created.id();
        mvc.perform(patch(path).session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"memo\":\"changed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.amount").value(12000));
        mvc.perform(patch(path).session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"memo\":\"changed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch(path).session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"memo\":\"changed\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRANSACTION_VERSION_CONFLICT"));
        mvc.perform(patch(path).session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"memo\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.memo").value(""));
    }

    @Test
    void patchRejectsInvalidValuesAndUnknownFields() throws Exception {
        var created = service.create(owner.getId(), request(BigDecimal.ONE, TransactionType.EXPENSE));
        for (String body : List.of("{}", "{\"version\":0}", "{\"version\":-1,\"memo\":\"x\"}",
                "{\"version\":0.0,\"memo\":\"x\"}", "{\"version\":0,\"amount\":null}",
                "{\"version\":0,\"amount\":1.5}", "{\"version\":0,\"amount\":0}",
                "{\"version\":0,\"occurredOn\":\"2026-02-30\"}", "{\"version\":0,\"user_id\":\"x\"}")) {
            mvc.perform(patch("/api/transactions/{id}", created.id()).session(session(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void keepsArchivedCategoryForEditsButRejectsSelectingAnotherArchivedCategory() throws Exception {
        var created = service.create(owner.getId(), request(BigDecimal.ONE, TransactionType.EXPENSE));
        Category archived = categories.saveAndFlush(new Category(owner.getId(), "old", TransactionType.EXPENSE));
        archived.archive();
        categoryService.archive(owner.getId(), categoryId);
        mvc.perform(patch("/api/transactions/{id}", created.id()).session(session(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"description\":\"edited\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/api/transactions/{id}", created.id()).session(session(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"categoryId\":\"" + archived.getId() + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATEGORY_ARCHIVED"));
    }

    @Test
    void deleteIsPhysicalScopedAndRequiresCsrf() throws Exception {
        var created = service.create(owner.getId(), request(BigDecimal.ONE, TransactionType.EXPENSE));
        String path = "/api/transactions/" + created.id();
        mvc.perform(delete(path).session(session(owner))).andExpect(status().isForbidden());
        mvc.perform(delete(path).session(session(other)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(patch(path).session(session(other)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"memo\":\"x\"}")).andExpect(status().isNotFound());
        mvc.perform(delete(path).session(session(owner)).with(csrf())).andExpect(status().isNoContent());
        entityManager.clear();
        assertTrue(transactions.findByIdAndUserId(created.id(), owner.getId()).isEmpty());
        mvc.perform(delete(path).session(session(owner)).with(csrf())).andExpect(status().isNotFound());
        assertTrue(categories.findByIdAndUserId(categoryId, owner.getId()).isPresent());
    }

    @Test
    void rejectsInvalidListParameters() throws Exception {
        for (String[] pair : List.of(new String[]{"month", "2026-13"}, new String[]{"month", "0000-01"},
                new String[]{"page", "-1"}, new String[]{"size", "101"}, new String[]{"sort", "id,asc"},
                new String[]{"sort", "amount,ASC"}, new String[]{"categoryId", "1-1-1-1-1"}, new String[]{"type", "OTHER"})) {
            mvc.perform(get("/api/transactions").session(session(owner)).param(pair[0], pair[1])).andExpect(status().isBadRequest());
        }
    }

    @Test
    void stableTiesAndCombinedFiltersUseSameCountPredicate() throws Exception {
        var first = service.create(owner.getId(), request(new BigDecimal("50"), TransactionType.EXPENSE));
        var second = service.create(owner.getId(), request(new BigDecimal("50"), TransactionType.EXPENSE));
        String firstPage = mvc.perform(get("/api/transactions").session(session(owner)).param("size", "1")
                        .param("month", "2026-10").param("type", "EXPENSE").param("categoryId", categoryId.toString()).param("q", "LUNCH"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andReturn().getResponse().getContentAsString();
        String secondPage = mvc.perform(get("/api/transactions").session(session(owner)).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String high = first.id().toString().compareTo(second.id().toString()) > 0 ? first.id().toString() : second.id().toString();
        String low = high.equals(first.id().toString()) ? second.id().toString() : first.id().toString();
        assertEquals(high, mapper.readTree(firstPage).get("content").get(0).get("id").asString());
        assertEquals(low, mapper.readTree(secondPage).get("content").get(0).get("id").asString());
        mvc.perform(get("/api/transactions").session(session(owner)).param("type", "INCOME"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void changingTypeRequiresMatchingOwnedActiveCategory() throws Exception {
        var created = service.create(owner.getId(), request(BigDecimal.ONE, TransactionType.EXPENSE));
        Category income = categories.saveAndFlush(new Category(owner.getId(), "salary", TransactionType.INCOME));
        Category foreign = categories.saveAndFlush(new Category(other.getId(), "foreign", TransactionType.INCOME));
        String path = "/api/transactions/" + created.id();
        mvc.perform(patch(path).session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"type\":\"INCOME\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATEGORY_TYPE_MISMATCH"));
        mvc.perform(patch(path).session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"type\":\"INCOME\",\"categoryId\":\"" + foreign.getId() + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch(path).session(session(owner)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"type\":\"INCOME\",\"categoryId\":\"" + income.getId() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("INCOME")).andExpect(jsonPath("$.version").value(1));
    }

    private CreateTransactionRequest request(BigDecimal amount, TransactionType type) {
        return new CreateTransactionRequest(type, amount, categoryId, LocalDate.of(2026, 10, 21), "lunch", null);
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
