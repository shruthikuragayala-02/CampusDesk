package com.campusdesk.repository;

import com.campusdesk.domain.TicketEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketEventRepository extends JpaRepository<TicketEvent, UUID> {
    List<TicketEvent> findByTicket_Id(UUID ticketId);
}
