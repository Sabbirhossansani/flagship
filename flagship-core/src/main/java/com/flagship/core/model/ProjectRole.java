package com.flagship.core.model;

/**
 * Per-project RBAC roles (Section 1 & FR10).
 */
public enum ProjectRole {
    VIEWER,
    EDITOR,
    APPROVER,
    ADMIN;

    public boolean canEdit() {
        return this == EDITOR || this == APPROVER || this == ADMIN;
    }

    public boolean canApprove() {
        return this == APPROVER || this == ADMIN;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }
}
