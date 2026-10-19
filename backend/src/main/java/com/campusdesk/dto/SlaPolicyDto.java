package com.campusdesk.dto;

import com.campusdesk.domain.TicketPriority;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class SlaPolicyDto {
    private UUID id;
    private String categoryName;
    private TicketPriority priority;
    private long responseMinutes;
    private long resolutionMinutes;
    private boolean active;
}
