package com.campusdesk.dto;

import com.campusdesk.domain.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StatusUpdateRequest {
    @NotNull
    private TicketStatus status;

    @Size(max = 2000)
    private String resolutionNotes;
}
