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
 */
public final class WandPoolClient {

    private static final long[] POOL = new long[WandPoolConstants.ASPECT_COUNT];

    private static String ownerName = "";
    private static boolean valid;

    private WandPoolClient() {}

    public static void apply(final long[] values, final String owner) {
        if (values != null && values.length == POOL.length) {
            System.arraycopy(values, 0, POOL, 0, POOL.length);
        }
        ownerName = owner == null ? "" : owner;
        valid = true;
    }

    public static boolean isValid() {
        return valid;
    }

    public static long get(final int index) {
        return index >= 0 && index < POOL.length ? POOL[index] : 0L;
    }

    public static String ownerName() {
        return ownerName;
    }
}
