package com.flagship.server.command;

/**
 * Command interface encapsulating flag/segment mutations for audit logging and two-person approvals.
 *
 * @param <T> Return type of the command execution
 */
public interface FlagshipCommand<T> {

    String getProjectKey();

    String getEnvKey();

    String getEntityType();

    String getEntityKey();

    String getAction();

    String getAuthor();

    String getReason();

    T execute();
}
