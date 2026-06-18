package com.olp.auth.repository;

import com.olp.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the users table.
 *
 * Spring auto-generates the SQL for all these methods at startup.
 * You never write SQL for these standard operations.
 *
 * findByEmail    → SELECT * FROM users WHERE email = ?
 * existsByEmail  → SELECT COUNT(*) FROM users WHERE email = ?
 * save()         → INSERT INTO users ... (inherited from JpaRepository)
 * findById()     → SELECT * FROM users WHERE id = ? (inherited)
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
