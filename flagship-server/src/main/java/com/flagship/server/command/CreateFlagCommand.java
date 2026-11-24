package com.flagship.server.command;

import com.flagship.server.dto.CreateFlagRequest;
import com.flagship.server.dto.FlagResponse;
import com.flagship.server.service.FlagService;

public class CreateFlagCommand implements FlagshipCommand<FlagResponse> {

    private final FlagService flagService;
    private final String projectKey;
    private final String envKey;
    private final CreateFlagRequest request;
    private final String author;
    private final String reason;

    public CreateFlagCommand(
            FlagService flagService,
            String projectKey,
            String envKey,
            CreateFlagRequest request,
            String author,
            String reason
    ) {
        this.flagService = flagService;
        this.projectKey = projectKey;
        this.envKey = envKey;
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
        return request.key();
    }

    @Override
    public String getAction() {
        return "CREATE";
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
        return flagService.createFlag(projectKey, envKey, request);
    }
}
