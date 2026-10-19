package com.campusdesk.service;

import com.campusdesk.domain.Category;
import com.campusdesk.domain.Location;
import com.campusdesk.domain.Role;
import com.campusdesk.domain.SlaPolicy;
import com.campusdesk.domain.TicketPriority;
import com.campusdesk.domain.User;
import com.campusdesk.dto.CategoryDto;
import com.campusdesk.dto.LocationDto;
import com.campusdesk.dto.SlaPolicyDto;
import com.campusdesk.dto.UserSummaryDto;
import com.campusdesk.repository.CategoryRepository;
import com.campusdesk.repository.LocationRepository;
import com.campusdesk.repository.SlaPolicyRepository;
import com.campusdesk.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SlaPolicyRepository slaPolicyRepository;
    private final UserRepository userRepository;

    public AdminService(CategoryRepository categoryRepository, LocationRepository locationRepository,
                       SlaPolicyRepository slaPolicyRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.slaPolicyRepository = slaPolicyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories() {
        return categoryRepository.findAll().stream()
            .map(category -> CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .active(category.isActive())
                .build())
            .toList();
    }

    @Transactional
    public CategoryDto createCategory(String name, String description) {
        String normalized = name == null ? "" : name.trim();
        String normalizedDescription = description == null ? "" : description.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name must be between 1 and 80 characters");
        }
        if (normalizedDescription.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category description must be at most 255 characters");
        }
        if (categoryRepository.findByNameIgnoreCase(normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists");
        }

        Category category = new Category();
        category.setName(normalized);
        category.setDescription(normalizedDescription);
        categoryRepository.save(category);
        return CategoryDto.builder().id(category.getId()).name(category.getName()).description(category.getDescription()).active(category.isActive()).build();
    }

    @Transactional(readOnly = true)
    public List<LocationDto> listLocations() {
        return locationRepository.findAll().stream()
            .map(location -> LocationDto.builder()
                .id(location.getId())
                .name(location.getName())
                .active(location.isActive())
                .build())
            .toList();
    }

    @Transactional
    public LocationDto createLocation(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location name must be between 1 and 80 characters");
        }
        if (locationRepository.findByNameIgnoreCase(normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Location already exists");
        }

        Location location = new Location();
        location.setName(normalized);
        locationRepository.save(location);
        return LocationDto.builder().id(location.getId()).name(location.getName()).active(location.isActive()).build();
    }

    @Transactional(readOnly = true)
    public List<UserSummaryDto> listUsers() {
        return userRepository.findAll().stream()
            .filter(user -> user.isEnabled())
            .filter(user -> user.getRole() == Role.STAFF || user.getRole() == Role.ADMIN)
            .sorted((left, right) -> left.getFullName().compareToIgnoreCase(right.getFullName()))
            .map(user -> UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .build())
            .toList();
    }

    @Transactional(readOnly = true)
    public List<SlaPolicyDto> listPolicies() {
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

    @Transactional
    public SlaPolicyDto createPolicy(UUID categoryId, TicketPriority priority, long responseMinutes, long resolutionMinutes) {
        if (responseMinutes <= 0 || resolutionMinutes < responseMinutes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "SLA durations must be positive and resolution time cannot precede response time");
        }
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));
        if (slaPolicyRepository.findByCategoryAndPriority(category, priority).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SLA policy exists for this category and priority");
        }

        SlaPolicy policy = new SlaPolicy();
        policy.setCategory(category);
        policy.setPriority(priority);
        policy.setResponseMinutes(responseMinutes);
        policy.setResolutionMinutes(resolutionMinutes);
        slaPolicyRepository.save(policy);
        return SlaPolicyDto.builder()
            .id(policy.getId())
            .categoryName(category.getName())
            .priority(policy.getPriority())
            .responseMinutes(policy.getResponseMinutes())
            .resolutionMinutes(policy.getResolutionMinutes())
            .active(policy.isActive())
            .build();
    }
}
