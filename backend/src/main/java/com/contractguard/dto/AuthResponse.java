package com.contractguard.dto;

public record AuthResponse(String token, String email, String fullName) {
}
