package com.flagship.server.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration properties for FlagShip Server with Bean Validation (C3).
 */
@Validated
@ConfigurationProperties(prefix = "flagship")
public class FlagshipServerProperties {

    @Valid
    @NotNull
    private SecurityProperties security = new SecurityProperties();

    public SecurityProperties getSecurity() {
        return security;
    }

    public void setSecurity(SecurityProperties security) {
        this.security = security;
    }

    public static class SecurityProperties {

        @NotBlank(message = "JWT signing secret must not be blank")
        @Size(min = 32, message = "JWT signing secret must be at least 32 characters")
        private String jwtSecret;

        @Min(value = 60, message = "JWT expiration must be at least 60 seconds")
        private long jwtExpirationSeconds = 3600;

        @NotBlank
        private String adminUsername = "admin";

        @NotBlank
        private String adminPassword;

        @NotBlank
        private String approverUsername = "approver";

        @NotBlank
        private String approverPassword = "approver_dev_password";

        @NotBlank
        private String editorUsername = "editor";

        @NotBlank
        private String editorPassword = "editor_dev_password";

        @NotBlank
        private String viewerUsername = "viewer";

        @NotBlank
        private String viewerPassword = "viewer_dev_password";

        @NotBlank
        private String bootstrapApiKey;

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public long getJwtExpirationSeconds() {
            return jwtExpirationSeconds;
        }

        public void setJwtExpirationSeconds(long jwtExpirationSeconds) {
            this.jwtExpirationSeconds = jwtExpirationSeconds;
        }

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public String getApproverUsername() {
            return approverUsername;
        }

        public void setApproverUsername(String approverUsername) {
            this.approverUsername = approverUsername;
        }

        public String getApproverPassword() {
            return approverPassword;
        }

        public void setApproverPassword(String approverPassword) {
            this.approverPassword = approverPassword;
        }

        public String getEditorUsername() {
            return editorUsername;
        }

        public void setEditorUsername(String editorUsername) {
            this.editorUsername = editorUsername;
        }

        public String getEditorPassword() {
            return editorPassword;
        }

        public void setEditorPassword(String editorPassword) {
            this.editorPassword = editorPassword;
        }

        public String getViewerUsername() {
            return viewerUsername;
        }

        public void setViewerUsername(String viewerUsername) {
            this.viewerUsername = viewerUsername;
        }

        public String getViewerPassword() {
            return viewerPassword;
        }

        public void setViewerPassword(String viewerPassword) {
            this.viewerPassword = viewerPassword;
        }

        public String getBootstrapApiKey() {
            return bootstrapApiKey;
        }

        public void setBootstrapApiKey(String bootstrapApiKey) {
            this.bootstrapApiKey = bootstrapApiKey;
        }
    }
}
