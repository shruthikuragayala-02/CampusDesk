package com.campusdesk.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TicketSlaScheduler {
    private final TicketService ticketService;

    public TicketSlaScheduler(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @Scheduled(fixedRate = 60000)
    public void processOverdueTickets() {
        ticketService.processOverdueTickets();
    }
}
