package com.campusdesk.repository;

import com.campusdesk.domain.Ticket;
import com.campusdesk.domain.TicketStatus;
import com.campusdesk.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    Page<Ticket> findByReporter(User reporter, Pageable pageable);

    Page<Ticket> findByAssignee(User assignee, Pageable pageable);

    @Query("select t from Ticket t where t.status in :statuses")
    List<Ticket> findByStatusIn(@Param("statuses") List<TicketStatus> statuses);

    @Query("select t from Ticket t where t.status <> 'CLOSED' and t.dueAt < :now and not t.escalated")
    List<Ticket> findOverdueTickets(@Param("now") Instant now);

    @Query("select count(t) from Ticket t where t.status = :status")
    long countByStatus(@Param("status") TicketStatus status);

    @Query("select count(t) from Ticket t where t.status <> 'CLOSED' and t.dueAt < :now")
    long countOverdue(@Param("now") Instant now);

    @Query("select t from Ticket t where lower(t.title) like %:term% or lower(t.description) like %:term%")
    Page<Ticket> searchByTerm(@Param("term") String term, Pageable pageable);

    @Query("select t from Ticket t where t.reporter = :reporter and "
        + "(lower(t.title) like %:term% or lower(t.description) like %:term%)")
    Page<Ticket> searchByReporterAndTerm(@Param("reporter") User reporter,
                                         @Param("term") String term,
                                         Pageable pageable);

}
