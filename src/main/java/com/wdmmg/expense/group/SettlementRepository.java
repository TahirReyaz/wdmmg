package com.wdmmg.expense.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    @Query("""
            select s from Settlement s join fetch s.fromUser join fetch s.toUser
            where s.group.id = :groupId order by s.date desc, s.id desc
            """)
    List<Settlement> findForGroup(@Param("groupId") Long groupId);

    Optional<Settlement> findByIdAndGroupId(Long id, Long groupId);

    @Query("select s.fromUser.id, sum(s.amount) from Settlement s where s.group.id = :groupId group by s.fromUser.id")
    List<Object[]> sentByMember(@Param("groupId") Long groupId);

    @Query("select s.toUser.id, sum(s.amount) from Settlement s where s.group.id = :groupId group by s.toUser.id")
    List<Object[]> receivedByMember(@Param("groupId") Long groupId);

    @Query("""
            select s.group.id, sum(s.amount) from Settlement s
            where s.fromUser.id = :userId and s.group.id in :ids group by s.group.id
            """)
    List<Object[]> sentByUserPerGroup(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    @Query("""
            select s.group.id, sum(s.amount) from Settlement s
            where s.toUser.id = :userId and s.group.id in :ids group by s.group.id
            """)
    List<Object[]> receivedByUserPerGroup(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
}
