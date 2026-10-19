package com.campusdesk.dto;

import com.campusdesk.domain.TicketPriority;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SlaPolicyCreateRequest {
    @NotNull
    private UUID categoryId;

    @NotNull
    private TicketPriority priority;

    @NotNull
    @Positive
    private Long responseMinutes;

    @NotNull
    @Positive
    private Long resolutionMinutes;

    @AssertTrue(message = "Resolution time cannot precede response time")
    public boolean isResolutionAfterResponse() {
        return responseMinutes == null || resolutionMinutes == null || resolutionMinutes >= responseMinutes;
    }
}
