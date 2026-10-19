package com.campusdesk.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
public class LocationDto {
    private UUID id;
    private String name;
    private boolean active;
}
