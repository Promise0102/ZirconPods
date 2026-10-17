package local.roselink.lite;

/** Exact-device filtering and a finite event cooldown; there is no polling loop. */
final class AutoConnectPolicy {
    static final long COOLDOWN_MS = 30_000;
    static boolean matches(String remembered, String candidate) {
        return remembered != null && remembered.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")
                && candidate != null && remembered.equalsIgnoreCase(candidate);
    }
    static boolean allow(boolean enabled, boolean paused, String remembered, String candidate,
                         boolean busy, long now, long lastAttempt) {
        if (!enabled || paused || busy || !matches(remembered, candidate)) return false;
        return lastAttempt <= 0 || now < lastAttempt || now - lastAttempt >= COOLDOWN_MS;
    }
}
