package elinarcurios.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side copy of the local player's shared wand pool.
 *
 * <p>
 * The authoritative pool lives in the server's world-level database. This cache is fed by
 * {@code WandPoolSyncPacket} and is the single display source for every Celestial Wand the local
 * player owns — no matter whether the wand is in hand, in a chest, in the arcane workbench or in an
 * enchanter. That is what keeps two wands in two different inventories showing the same number.
 *
 * <p>
 * Keyed by aspect tag so compound/custom aspects work exactly like the six primals. The packet
 * handler runs on the client network thread while the HUD/tooltip read on the render thread, so the
 * map is published through a volatile reference (build a fresh map, then swap).
 */
public final class WandPoolClient {

    private static volatile Map<String, Long> pool = Collections.emptyMap();
    private static volatile String ownerName = "";
    private static volatile boolean valid;

    private WandPoolClient() {}

    public static void apply(final Map<String, Long> values, final String owner) {
        pool = values == null ? Collections.<String, Long>emptyMap() : new HashMap<String, Long>(values);
        ownerName = owner == null ? "" : owner;
        valid = true;
    }

    public static boolean isValid() {
        return valid;
    }

    public static long get(final String tag) {
        if (tag == null) return 0L;
        final Long value = pool.get(tag);
        return value == null ? 0L : value;
    }

    /** Read-only view of the currently published map (never mutated after publication). */
    public static Map<String, Long> snapshot() {
        return pool;
    }

    public static String ownerName() {
        return ownerName;
    }
}
