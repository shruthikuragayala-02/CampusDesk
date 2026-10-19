package com.campusdesk.controller;

import com.campusdesk.domain.Role;
import com.campusdesk.domain.User;
import com.campusdesk.dto.*;
import com.campusdesk.repository.UserRepository;
import com.campusdesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TicketController {
    private final TicketService ticketService;
    private final UserRepository userRepository;
    private static final int MAX_TICKET_PAGE_SIZE = 100;

    public TicketController(TicketService ticketService, UserRepository userRepository) {
        this.ticketService = ticketService;
        this.userRepository = userRepository;
    }

    @PostMapping("/tickets")
    public TicketResponse createTicket(@Valid @RequestBody TicketCreateRequest request) {
        User user = currentUser();
        return ticketService.createTicket(user, request);
    }

    @GetMapping("/tickets/my")
    public List<TicketResponse> myTickets(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return ticketService.getMyTickets(currentUser(), ticketPageable(page, size));
    }

    @GetMapping("/tickets/assigned")
    public List<TicketResponse> assignedTickets(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        User user = currentUser();
        if (user.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Students cannot view assigned tickets");
        }
        return ticketService.getAssignedTickets(user, ticketPageable(page, size));
    }

    @GetMapping("/tickets")
    public List<TicketResponse> listTickets(@RequestParam(required = false) String search,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        if (search != null && search.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search term must be at most 200 characters");
        }
        User user = currentUser();
        Pageable pageable = ticketPageable(page, size);
        return ticketService.listTickets(user, search, pageable);
    }

    @GetMapping("/tickets/{ticketId}")
    public TicketResponse getTicket(@PathVariable UUID ticketId) {
        return ticketService.getTicket(ticketId, currentUser());
    }

    @PostMapping("/tickets/{ticketId}/assign")
    public TicketResponse assignTicket(@PathVariable UUID ticketId, @Valid @RequestBody AssignTicketRequest request) {
        return ticketService.assignTicket(ticketId, currentUser(), request.getAssigneeId());
    }

    @PatchMapping("/tickets/{ticketId}/status")
    public TicketResponse updateStatus(@PathVariable UUID ticketId, @Valid @RequestBody StatusUpdateRequest request) {
        return ticketService.updateStatus(ticketId, currentUser(), request);
    }

    @PostMapping("/tickets/{ticketId}/comments")
    public TicketResponse addComment(@PathVariable UUID ticketId, @Valid @RequestBody TicketCommentRequest request) {
        return ticketService.addComment(ticketId, currentUser(), request);
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        User user = currentUser();
        if (user.getRole() == Role.STUDENT) {
            throw new AccessDeniedException("Students do not access admin dashboard");
        }
        return ticketService.getDashboard();
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return ticketService.getCategories();
    }

    @GetMapping("/locations")
    public List<LocationDto> locations() {
        return ticketService.getLocations();
    }

    @GetMapping("/sla-policies")
    public List<SlaPolicyDto> slaPolicies() {
        return ticketService.getSlaPolicies();
    }

    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : null;
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private Pageable ticketPageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_TICKET_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Ticket page must be non-negative and size must be between 1 and " + MAX_TICKET_PAGE_SIZE);
        }
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));
        return PageRequest.of(page, size, newestFirst);
    }
}
