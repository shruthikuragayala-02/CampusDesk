package com.campusdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationCreateRequest {
    @NotBlank
    @Size(max = 80)
    private String name;
}
