package com.wdmmg.expense.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserAvatarRepository extends JpaRepository<UserAvatar, UUID> {
    @Modifying
    @Query("delete from UserAvatar a where a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
