package com.flagship.core.strategy;

import java.util.Objects;

/**
 * Lightweight SemVer 2.0.0 value object supporting major.minor.patch and pre-release ordering.
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;

    public SemanticVersion(int major, int minor, int patch, String preRelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
    }

    public static SemanticVersion tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        // Strip build metadata (+...)
        int plusIdx = trimmed.indexOf('+');
        if (plusIdx >= 0) {
            trimmed = trimmed.substring(0, plusIdx);
        }
        String preRelease = null;
        int dashIdx = trimmed.indexOf('-');
        if (dashIdx >= 0) {
            preRelease = trimmed.substring(dashIdx + 1);
            trimmed = trimmed.substring(0, dashIdx);
            if (preRelease.isEmpty() || !isValidPreRelease(preRelease)) {
                return null;
            }
        }
        String[] parts = trimmed.split("\\.", -1);
        if (parts.length < 2 || parts.length > 3) {
            return null;
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                return null;
            }
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
            if (major < 0 || minor < 0 || patch < 0) {
                return null;
            }
            return new SemanticVersion(major, minor, patch, preRelease);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isValidPreRelease(String preRelease) {
        String[] identifiers = preRelease.split("\\.", -1);
        for (String id : identifiers) {
            if (id.isEmpty()) {
                return false;
            }
            for (int i = 0; i < id.length(); i++) {
                char c = id.charAt(i);
                boolean validChar = (c >= '0' && c <= '9')
                        || (c >= 'A' && c <= 'Z')
                        || (c >= 'a' && c <= 'z')
                        || c == '-';
                if (!validChar) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int cmp = Integer.compare(this.major, other.major);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.minor, other.minor);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.patch, other.patch);
        if (cmp != 0) return cmp;

        // Per SemVer 2.0.0 §11: normal version has higher precedence than pre-release version
        if (this.preRelease == null && other.preRelease == null) {
            return 0;
        }
        if (this.preRelease == null) {
            return 1;
        }
        if (other.preRelease == null) {
            return -1;
        }
        return comparePreRelease(this.preRelease, other.preRelease);
    }

    private static int comparePreRelease(String left, String right) {
        String[] leftParts = left.split("\\.", -1);
        String[] rightParts = right.split("\\.", -1);
        int minLen = Math.min(leftParts.length, rightParts.length);

        for (int i = 0; i < minLen; i++) {
            String a = leftParts[i];
            String b = rightParts[i];
            if (a.equals(b)) {
                continue;
            }
            boolean aNumeric = isAllDigits(a);
            boolean bNumeric = isAllDigits(b);
            if (aNumeric && bNumeric) {
                int numCmp = Integer.compare(a.length(), b.length());
                if (numCmp == 0) {
                    numCmp = a.compareTo(b);
                }
                if (numCmp != 0) {
                    return numCmp;
                }
            } else if (aNumeric) {
                // Numeric identifiers always have lower precedence than non-numeric identifiers
                return -1;
            } else if (bNumeric) {
                return 1;
            } else {
                int lexCmp = a.compareTo(b);
                if (lexCmp != 0) {
                    return lexCmp;
                }
            }
        }
        return Integer.compare(leftParts.length, rightParts.length);
    }

    private static boolean isAllDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticVersion that)) return false;
        return major == that.major
                && minor == that.minor
                && patch == that.patch
                && Objects.equals(preRelease, that.preRelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }
}
