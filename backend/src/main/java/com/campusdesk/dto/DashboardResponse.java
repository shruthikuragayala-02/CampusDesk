package com.campusdesk.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Builder
public class DashboardResponse {
    private long totalTickets;
    private long openTickets;
    private long inProgressTickets;
    private long resolvedTickets;
    private long overdueTickets;
    private Map<String, Long> ticketsByCategory;
    private Map<String, Long> ticketsByPriority;
    private double averageResolutionHours;
    private double slaCompliancePercent;
}
