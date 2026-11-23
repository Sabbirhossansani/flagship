package com.flagship.server.dto;

import jakarta.validation.constraints.NotNull;

public record KillSwitchRequest(
        @NotNull(message = "killSwitch must not be null")
        Boolean killSwitch,
        String reason
) {
}
