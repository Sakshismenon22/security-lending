package com.example.fix_adapter_service.config;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

@Getter @Setter
public class RouteProperties {
    @NotBlank @Size(max=100) private String counterpartyId;
    @NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{1,64}") private String targetCompId;
    @NotBlank private String host="127.0.0.1";
    @Min(1) @Max(65535) private int port=9876;
    @NotEmpty private Set<@Positive Long> organizationIds=new HashSet<>();
}
