package com.campusdesk.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class AssignTicketRequest {
    @NotNull
    private UUID assigneeId;
}
