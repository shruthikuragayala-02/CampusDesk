package com.campusdesk.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class TicketCommentDto {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String content;
    private Instant createdAt;
}
