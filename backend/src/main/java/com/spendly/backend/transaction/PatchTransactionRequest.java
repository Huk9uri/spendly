package com.spendly.backend.transaction;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class PatchTransactionRequest {
    private final long version;
    private final Map<String, Object> fields;

    private PatchTransactionRequest(long version, Map<String, Object> fields) {
        this.version = version;
        this.fields = new HashMap<>(fields);
        this.fields.remove("version");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchTransactionRequest fromJson(Map<String, Object> fields) {
        if (!Set.of("version", "type", "amount", "categoryId", "occurredOn", "description", "memo").containsAll(fields.keySet())
                || fields.size() < 2 || !fields.containsKey("version")) {
            throw new IllegalArgumentException("Invalid patch fields");
        }
        Object value = fields.get("version");
        if (!(value instanceof Integer || value instanceof Long || value instanceof BigInteger)) {
            throw new IllegalArgumentException("Version must be an integer");
        }
        long version = new BigInteger(value.toString()).longValueExact();
        if (version < 0 || fields.entrySet().stream().anyMatch(e -> !e.getKey().equals("memo") && e.getValue() == null)) {
            throw new IllegalArgumentException("Invalid null or version");
        }
        return new PatchTransactionRequest(version, fields);
    }

    public long version() { return version; }

    public CreateTransactionRequest merge(Transaction transaction) {
        Map<String, Object> merged = new HashMap<>();
        merged.put("type", transaction.getType().name());
        merged.put("amount", transaction.getAmount().toBigIntegerExact());
        merged.put("categoryId", transaction.getCategoryId().toString());
        merged.put("occurredOn", transaction.getOccurredOn().toString());
        merged.put("description", transaction.getDescription());
        merged.put("memo", transaction.getMemo());
        merged.putAll(fields);
        return CreateTransactionRequest.fromJson(merged);
    }

    @Override public String toString() { return "PatchTransactionRequest[REDACTED]"; }
}
