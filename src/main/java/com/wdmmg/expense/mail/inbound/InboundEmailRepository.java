package com.wdmmg.expense.mail.inbound;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundEmailRepository extends JpaRepository<InboundEmail, Long> {
    boolean existsByMessageId(String messageId);

    Page<InboundEmail> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
