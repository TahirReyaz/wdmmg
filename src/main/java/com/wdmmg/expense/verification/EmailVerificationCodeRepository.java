package com.wdmmg.expense.verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {

    Optional<EmailVerificationCode> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<EmailVerificationCode> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    /** A new code replaces any earlier one. */
    @Modifying
    @Query("update EmailVerificationCode c set c.consumedAt = :now where c.userId = :userId and c.consumedAt is null")
    int consumeOutstanding(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from EmailVerificationCode c where c.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
