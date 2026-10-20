package com.spendly.backend.auth;

import java.util.UUID;

public record UserResponse(UUID id, String email) { }
