package com.spendly.backend.transaction;

import java.util.List;

public record TransactionPage(List<TransactionResponse> content, int page, int size, long totalElements, long totalPages) {}
