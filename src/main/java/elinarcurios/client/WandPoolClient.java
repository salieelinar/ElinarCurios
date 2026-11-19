package elinarcurios.client;

import elinarcurios.util.WandPoolConstants;

/**
 * Client-side copy of the local player's shared wand vis pool.
 *
 * <p>
 * The authoritative pool lives in the server player's data. This cache is fed by
 * {@code WandPoolSyncPacket} and is the single display source for every Celestial Wand the local
 * player owns — no matter whether the wand is in hand, in a chest, in the arcane workbench or in an
 * enchanter. That is what keeps two wands in two different inventories showing the same number.
 *
 * <p>
 * The packet handler runs on the client network thread while the HUD/tooltip read on the render
 * thread, so the array is published through a volatile reference (write a fresh copy, then swap).
 */
public final class WandPoolClient {

    private static volatile long[] pool = new long[WandPoolConstants.ASPECT_COUNT];
    private static volatile String ownerName = "";
    private static volatile boolean valid;

    private WandPoolClient() {}

    public static void apply(final long[] values, final String owner) {
        final long[] copy = new long[WandPoolConstants.ASPECT_COUNT];
        if (values != null) {
            System.arraycopy(values, 0, copy, 0, Math.min(values.length, copy.length));
        }
        pool = copy;
        ownerName = owner == null ? "" : owner;
        valid = true;
    }

    public static boolean isValid() {
        return valid;
    }

    public static long get(final int index) {
        final long[] current = pool;
        return index >= 0 && index < current.length ? current[index] : 0L;
    }

    public static String ownerName() {
        return ownerName;
    }
}
