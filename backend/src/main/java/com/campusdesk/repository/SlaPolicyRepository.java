package com.campusdesk.repository;

import com.campusdesk.domain.Category;
import com.campusdesk.domain.SlaPolicy;
import com.campusdesk.domain.TicketPriority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {
    Optional<SlaPolicy> findByCategoryAndPriority(Category category, TicketPriority priority);

    List<SlaPolicy> findByActiveTrueOrderByPriorityAsc();
}
