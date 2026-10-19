package com.campusdesk.service;

import com.campusdesk.domain.*;
import com.campusdesk.dto.*;
import com.campusdesk.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TicketService {
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final TicketEventRepository ticketEventRepository;

    public TicketService(TicketRepository ticketRepository,
                        UserRepository userRepository,
                        CategoryRepository categoryRepository,
                        LocationRepository locationRepository,
                        SlaPolicyRepository slaPolicyRepository,
                        TicketEventRepository ticketEventRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.ticketEventRepository = ticketEventRepository;
    }

    @Transactional
    public TicketResponse createTicket(User reporter, TicketCreateRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));
        Location location = locationRepository.findById(request.getLocationId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location not found"));

        SlaPolicy policy = slaPolicyRepository.findByCategoryAndPriority(category, request.getPriority())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No SLA policy found for this category and priority"));

        Ticket ticket = new Ticket();
        ticket.setTitle(request.getTitle().trim());
        ticket.setDescription(request.getDescription().trim());
        ticket.setPriority(request.getPriority());
        ticket.setCategory(category);
        ticket.setLocation(location);
        ticket.setReporter(reporter);
        ticket.setStatus(TicketStatus.OPEN);

        Instant now = Instant.now();
        ticket.setDueAt(now.plus(Duration.ofMinutes(policy.getResolutionMinutes())));
        ticket.setResponseDeadline(now.plus(Duration.ofMinutes(policy.getResponseMinutes())));

        Ticket saved = ticketRepository.save(ticket);
        saveEvent(saved, TicketEventType.CREATED, null, null, reporter, "Ticket created");

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(UUID ticketId, User currentUser) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureAccess(currentUser, ticket);
        return mapToResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getMyTickets(User currentUser, Pageable pageable) {
        return ticketRepository.findByReporter(currentUser, pageable).stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getAssignedTickets(User currentUser, Pageable pageable) {
        return ticketRepository.findByAssignee(currentUser, pageable).stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> listTickets(User currentUser, String query, Pageable pageable) {
        Page<Ticket> tickets;
        if (query == null || query.isBlank()) {
            tickets = currentUser.getRole() == Role.STUDENT
                ? ticketRepository.findByReporter(currentUser, pageable)
                : ticketRepository.findAll(pageable);
        } else if (currentUser.getRole() == Role.STUDENT) {
            tickets = ticketRepository.searchByReporterAndTerm(currentUser, query.trim().toLowerCase(), pageable);
        } else {
            tickets = ticketRepository.searchByTerm(query.trim().toLowerCase(), pageable);
        }
        return tickets.stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public TicketResponse assignTicket(UUID ticketId, User actor, UUID assigneeId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if (actor.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Students cannot assign tickets");
        }

        User assignee = userRepository.findById(assigneeId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Assignee not found"));
        if (assignee.getRole() == Role.STUDENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only staff/admin can be assigned tickets");
        }

        ticket.setAssignee(assignee);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            ticket.setStatus(TicketStatus.ASSIGNED);
        }
        saveEvent(ticket, TicketEventType.ASSIGNED, null, ticket.getStatus() != null ? ticket.getStatus().name() : null, actor, "Ticket assigned");
        return mapToResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse updateStatus(UUID ticketId, User actor, StatusUpdateRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureAccess(actor, ticket);

        TicketStatus oldStatus = ticket.getStatus();
        validateTransition(oldStatus, request.getStatus());

        ticket.setStatus(request.getStatus());
        ticket.setLastStatusChangeAt(Instant.now());
        if (request.getStatus() == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(Instant.now());
            ticket.setResolutionNotes(request.getResolutionNotes() == null ? "Resolved." : request.getResolutionNotes());
            saveEvent(ticket, TicketEventType.RESOLVED, oldStatus.name(), request.getStatus().name(), actor, request.getResolutionNotes());
        } else if (request.getStatus() == TicketStatus.OPEN && oldStatus == TicketStatus.RESOLVED) {
            ticket.setResolvedAt(null);
            ticket.setResolutionNotes(request.getResolutionNotes());
            saveEvent(ticket, TicketEventType.REOPENED, oldStatus.name(), request.getStatus().name(), actor, request.getResolutionNotes());
        } else {
            saveEvent(ticket, TicketEventType.STATUS_CHANGED, oldStatus.name(), request.getStatus().name(), actor, request.getResolutionNotes());
        }

        return mapToResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse addComment(UUID ticketId, User actor, TicketCommentRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureAccess(actor, ticket);

        TicketComment comment = new TicketComment();
        comment.setTicket(ticket);
        comment.setAuthor(actor);
        comment.setContent(request.getContent().trim());
        ticket.getComments().add(comment);
        saveEvent(ticket, TicketEventType.COMMENTED, null, ticket.getStatus().name(), actor, request.getContent());
        return mapToResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public void processOverdueTickets() {
        List<Ticket> overdue = ticketRepository.findOverdueTickets(Instant.now());
        for (Ticket ticket : overdue) {
            ticket.setEscalated(true);
            saveEvent(ticket, TicketEventType.ESCALATED, ticket.getStatus().name(), ticket.getStatus().name(), null,
                "Ticket exceeded its SLA deadline and was escalated");
            ticketRepository.save(ticket);
        }
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        long total = ticketRepository.count();
        long open = ticketRepository.countByStatus(TicketStatus.OPEN);
        long inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long resolved = ticketRepository.countByStatus(TicketStatus.RESOLVED) + ticketRepository.countByStatus(TicketStatus.CLOSED);
        long overdue = ticketRepository.countOverdue(Instant.now());

        List<Ticket> allTickets = ticketRepository.findAll();
        Map<String, Long> byCategory = allTickets.stream()
            .collect(Collectors.groupingBy(t -> t.getCategory().getName(), Collectors.counting()));
        Map<String, Long> byPriority = allTickets.stream()
            .collect(Collectors.groupingBy(t -> t.getPriority().name(), Collectors.counting()));

        double avgResolutionHours = allTickets.stream()
            .filter(t -> t.getResolvedAt() != null)
            .mapToDouble(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes() / 60.0)
            .average()
            .orElse(0.0);

        List<Ticket> resolvedTickets = allTickets.stream()
            .filter(t -> t.getResolvedAt() != null || t.getStatus() == TicketStatus.RESOLVED || t.getStatus() == TicketStatus.CLOSED)
            .toList();

        double slaCompliance = 0.0;
        if (!resolvedTickets.isEmpty()) {
            long compliant = resolvedTickets.stream()
                .filter(t -> t.getResolvedAt() != null && t.getResolvedAt().isBefore(t.getDueAt().plusSeconds(1)))
                .count();
            slaCompliance = (compliant * 100.0) / resolvedTickets.size();
        }

        return DashboardResponse.builder()
            .totalTickets(total)
            .openTickets(open)
            .inProgressTickets(inProgress)
            .resolvedTickets(resolved)
            .overdueTickets(overdue)
            .ticketsByCategory(byCategory)
            .ticketsByPriority(byPriority)
            .averageResolutionHours(avgResolutionHours)
            .slaCompliancePercent(slaCompliance)
            .build();
    }

    @Transactional(readOnly = true)
    public List<SlaPolicyDto> getSlaPolicies() {
        return slaPolicyRepository.findByActiveTrueOrderByPriorityAsc().stream()
            .map(policy -> SlaPolicyDto.builder()
                .id(policy.getId())
                .categoryName(policy.getCategory().getName())
                .priority(policy.getPriority())
                .responseMinutes(policy.getResponseMinutes())
                .resolutionMinutes(policy.getResolutionMinutes())
                .active(policy.isActive())
                .build())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> getCategories() {
        return categoryRepository.findAll().stream().map(c -> CategoryDto.builder()
            .id(c.getId())
            .name(c.getName())
            .description(c.getDescription())
            .active(c.isActive())
            .build()).toList();
    }

    @Transactional(readOnly = true)
    public List<LocationDto> getLocations() {
        return locationRepository.findAll().stream().map(l -> LocationDto.builder()
            .id(l.getId())
            .name(l.getName())
            .active(l.isActive())
            .build()).toList();
    }

    private void ensureAccess(User currentUser, Ticket ticket) {
        if (currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.STAFF) {
            return;
        }
        if (ticket.getReporter() != null && ticket.getReporter().getId().equals(currentUser.getId())) {
            return;
        }
        if (ticket.getAssignee() != null && ticket.getAssignee().getId().equals(currentUser.getId())) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this ticket");
    }

    private void validateTransition(TicketStatus from, TicketStatus to) {
        if (from == to) {
            return;
        }
        boolean allowed = switch (from) {
            case OPEN -> to == TicketStatus.ASSIGNED || to == TicketStatus.IN_PROGRESS || to == TicketStatus.CLOSED;
            case ASSIGNED -> to == TicketStatus.IN_PROGRESS || to == TicketStatus.RESOLVED || to == TicketStatus.CLOSED;
            case IN_PROGRESS -> to == TicketStatus.RESOLVED || to == TicketStatus.ASSIGNED || to == TicketStatus.OPEN || to == TicketStatus.CLOSED;
            case RESOLVED -> to == TicketStatus.OPEN || to == TicketStatus.CLOSED;
            case CLOSED -> to == TicketStatus.OPEN;
        };

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid status transition from " + from + " to " + to);
        }
    }

    private void saveEvent(Ticket ticket, TicketEventType type, String oldStatus, String newStatus, User actor, String details) {
        TicketEvent ticketEvent = new TicketEvent();
        ticketEvent.setTicket(ticket);
        ticketEvent.setEventType(type);
        ticketEvent.setOldStatus(oldStatus);
        ticketEvent.setNewStatus(newStatus);
        ticketEvent.setActor(actor);
        ticketEvent.setDetails(details);
        ticketEventRepository.save(ticketEvent);
    }

    private TicketResponse mapToResponse(Ticket ticket) {
        return TicketResponse.builder()
            .id(ticket.getId())
            .title(ticket.getTitle())
            .description(ticket.getDescription())
            .priority(ticket.getPriority())
            .status(ticket.getStatus())
            .categoryName(ticket.getCategory() != null ? ticket.getCategory().getName() : null)
            .locationName(ticket.getLocation() != null ? ticket.getLocation().getName() : null)
            .reporterName(ticket.getReporter() != null ? ticket.getReporter().getFullName() : null)
            .assigneeName(ticket.getAssignee() != null ? ticket.getAssignee().getFullName() : null)
            .createdAt(ticket.getCreatedAt())
            .updatedAt(ticket.getUpdatedAt())
            .dueAt(ticket.getDueAt())
            .responseDeadline(ticket.getResponseDeadline())
            .resolvedAt(ticket.getResolvedAt())
            .escalated(ticket.isEscalated())
            .resolutionNotes(ticket.getResolutionNotes())
            .comments(ticket.getComments() == null ? new ArrayList<>() : ticket.getComments().stream().map(comment -> TicketCommentDto.builder()
                .id(comment.getId())
                .authorId(comment.getAuthor() != null ? comment.getAuthor().getId() : null)
                .authorName(comment.getAuthor() != null ? comment.getAuthor().getFullName() : null)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build()).toList())
            .build();
    }
}
