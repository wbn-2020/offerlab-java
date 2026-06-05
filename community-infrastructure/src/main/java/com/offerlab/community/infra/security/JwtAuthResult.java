package com.offerlab.community.infra.security;

public record JwtAuthResult(Long uid, boolean revocationCheckDegraded) {

    public static JwtAuthResult authenticated(Long uid) {
        return new JwtAuthResult(uid, false);
    }

    public static JwtAuthResult degraded(Long uid) {
        return new JwtAuthResult(uid, true);
    }

    public static JwtAuthResult invalid() {
        return new JwtAuthResult(null, false);
    }
}
