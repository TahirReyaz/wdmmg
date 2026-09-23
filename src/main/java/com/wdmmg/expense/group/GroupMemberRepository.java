package com.wdmmg.expense.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    @Query("select m from GroupMember m join fetch m.user where m.group.id = :groupId order by m.joinedAt")
    List<GroupMember> findMembers(@Param("groupId") Long groupId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    Optional<GroupMember> findByGroupIdAndUserId(Long groupId, Long userId);

    @Query("select m.group.id, count(m) from GroupMember m where m.group.id in :ids group by m.group.id")
    List<Object[]> countByGroupIds(@Param("ids") Collection<Long> ids);
}
