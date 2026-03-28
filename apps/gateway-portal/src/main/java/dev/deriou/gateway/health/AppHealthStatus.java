package dev.deriou.gateway.health;

public enum AppHealthStatus {
    UP,
    DOWN,
    DEGRADED,
    UNKNOWN;

    public static AppHealthStatus fromActuatorStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return UNKNOWN;
        }

        return switch (rawStatus.trim().toUpperCase()) {
            case "UP" -> UP;
            case "DOWN", "OUT_OF_SERVICE" -> DOWN;
            default -> UNKNOWN;
        };
    }
}
