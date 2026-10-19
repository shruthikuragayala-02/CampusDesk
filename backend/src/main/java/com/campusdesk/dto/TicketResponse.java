package com.campusdesk.dto;

import com.campusdesk.domain.TicketPriority;
import com.campusdesk.domain.TicketStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class TicketResponse {
    private UUID id;
    private String title;
    private String description;
    private TicketPriority priority;
    private TicketStatus status;
    private String categoryName;
    private String locationName;
    private String reporterName;
    private String assigneeName;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant dueAt;
    private Instant responseDeadline;
    private Instant resolvedAt;
    private boolean escalated;
    private String resolutionNotes;
    private List<TicketCommentDto> comments;
}
