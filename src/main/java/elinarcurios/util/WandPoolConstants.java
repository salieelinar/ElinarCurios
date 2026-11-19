package elinarcurios.util;

/**
 * Values shared by the server-side wand pool and the client-side display cache.
 *
 * <p>
 * Kept free of Thaumcraft types on purpose: the network handler and the client cache must be loadable
 * on both sides even before TC's classes are touched.
 */
public final class WandPoolConstants {

    /**
     * Raw vis capacity (Thaumcraft's internal unit = displayed vis x100).
     *
     * <p>
     * 9.2e18 keeps a margin below {@link Long#MAX_VALUE} so even a full pool plus one int-sized
     * {@code addVis} (int.MaxValue x100) cannot overflow. Deliberately far beyond anything the game can
     * produce — the pool is meant to be "never full".
     */
    public static final long CAPACITY = 9_200_000_000_000_000_000L;

    /**
     * Largest raw value Thaumcraft's int-based getters expose.
     *
     * <p>
     * Slightly below {@link Integer#MAX_VALUE}: addons do {@code storeVis(getVis() + 100)} (Gadomancy's
     * infusion claw), {@code + 5} (vis amulet) or {@code + 1} (ThaumicHorizons familiars). With no headroom
     * those additions would overflow to a negative int and be lost; 1024 covers every such caller found in
     * the pack while being invisible on the dial (21,474,826 instead of 21,474,836).
     */
    public static final int INT_FACE = Integer.MAX_VALUE - 1024;

    /** Player-persisted compound of the previous dev build (only read once for migration). */
    public static final String PLAYER_POOL_TAG = "elinarcurios:wandPool";

    private WandPoolConstants() {}
}
