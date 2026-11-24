package com.flagship.server.command;

import com.flagship.server.dto.FlagResponse;
import com.flagship.server.dto.UpdateFlagRequest;
import com.flagship.server.service.FlagService;

public class UpdateFlagCommand implements FlagshipCommand<FlagResponse> {

    private final FlagService flagService;
    private final String projectKey;
    private final String envKey;
    private final String flagKey;
    private final UpdateFlagRequest request;
    private final String author;
    private final String reason;

    public UpdateFlagCommand(
            FlagService flagService,
            String projectKey,
            String envKey,
            String flagKey,
            UpdateFlagRequest request,
            String author,
            String reason
    ) {
        this.flagService = flagService;
        this.projectKey = projectKey;
        this.envKey = envKey;
        this.flagKey = flagKey;
        this.request = request;
        this.author = author;
        this.reason = reason;
    }

    @Override
    public String getProjectKey() {
        return projectKey;
    }

    @Override
    public String getEnvKey() {
        return envKey;
    }

    @Override
    public String getEntityType() {
        return "FLAG";
    }

    @Override
    public String getEntityKey() {
        return flagKey;
    }

    @Override
    public String getAction() {
        return "UPDATE";
    }

    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public FlagResponse execute() {
        return flagService.updateFlag(projectKey, envKey, flagKey, request);
    }
}
