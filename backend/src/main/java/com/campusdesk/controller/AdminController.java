package com.campusdesk.controller;

import com.campusdesk.dto.CategoryCreateRequest;
import com.campusdesk.dto.CategoryDto;
import com.campusdesk.dto.DashboardResponse;
import com.campusdesk.dto.LocationCreateRequest;
import com.campusdesk.dto.LocationDto;
import com.campusdesk.dto.SlaPolicyCreateRequest;
import com.campusdesk.dto.SlaPolicyDto;
import com.campusdesk.dto.UserSummaryDto;
import com.campusdesk.service.AdminService;
import com.campusdesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final TicketService ticketService;
    private final AdminService adminService;

    public AdminController(TicketService ticketService, AdminService adminService) {
        this.ticketService = ticketService;
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return ticketService.getDashboard();
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return adminService.listCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        return adminService.createCategory(request.getName(), request.getDescription());
    }

    @GetMapping("/locations")
    public List<LocationDto> locations() {
        return adminService.listLocations();
    }

    @GetMapping("/users")
    public List<UserSummaryDto> users() {
        return adminService.listUsers();
    }

    @PostMapping("/locations")
    @ResponseStatus(HttpStatus.CREATED)
    public LocationDto createLocation(@Valid @RequestBody LocationCreateRequest request) {
        return adminService.createLocation(request.getName());
    }

    @GetMapping("/sla-policies")
    public List<SlaPolicyDto> slaPolicies() {
        return adminService.listPolicies();
    }

    @PostMapping("/sla-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public SlaPolicyDto createPolicy(@Valid @RequestBody SlaPolicyCreateRequest request) {
        return adminService.createPolicy(request.getCategoryId(), request.getPriority(),
            request.getResponseMinutes(), request.getResolutionMinutes());
    }
}
