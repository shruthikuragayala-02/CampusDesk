package com.campusdesk.repository;

import com.campusdesk.domain.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {
}
