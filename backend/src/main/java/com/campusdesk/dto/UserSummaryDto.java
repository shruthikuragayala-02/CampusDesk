package com.campusdesk.dto;

import com.campusdesk.domain.Role;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class UserSummaryDto {
    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private Role role;
}
