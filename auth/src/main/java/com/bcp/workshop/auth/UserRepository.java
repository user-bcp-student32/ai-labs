package com.bcp.workshop.auth;

import java.util.Optional;

public interface UserRepository {
    Optional<UserRecord> findByUsername(String username);
}
