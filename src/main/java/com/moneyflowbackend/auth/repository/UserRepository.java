package com.moneyflowbackend.auth.repository;

import com.moneyflowbackend.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameAndDeletedAtIsNull(String username);
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND (LOWER(u.username) = LOWER(:identifier) OR LOWER(u.email) = LOWER(:identifier))")
    Optional<User> findByUsernameOrEmailIgnoreCaseAndDeletedAtIsNull(@Param("identifier") String identifier);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.deletedAt IS NULL AND LOWER(u.username) = LOWER(:username)")
    boolean existsByUsernameIgnoreCaseAndDeletedAtIsNull(@Param("username") String username);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.deletedAt IS NULL AND LOWER(u.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND u.status = com.moneyflowbackend.auth.model.UserStatus.ACTIVE AND LOWER(u.username) LIKE LOWER(CONCAT(:username, '%')) ORDER BY u.username ASC")
    List<User> searchActiveByUsernamePrefix(@Param("username") String username);

    boolean existsByUsernameAndDeletedAtIsNull(String username);
    boolean existsByEmailAndDeletedAtIsNull(String email);
}
