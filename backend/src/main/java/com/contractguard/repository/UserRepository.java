package com.contractguard.repository;

import com.contractguard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data generates the implementation at runtime from the method name.
 * findByEmail becomes "select u from User u where u.email = ?1".
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
