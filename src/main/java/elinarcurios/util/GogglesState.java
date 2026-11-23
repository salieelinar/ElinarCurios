package elinarcurios.util;

import net.minecraft.item.ItemStack;

/**
 * Per-tick cached "should the virtual goggles be swapped in during rendering" state.
 *
 * <p>
 * Kept in a Thaumcraft-free class so the hot render path only costs one volatile read when the
 * feature is inactive. Written once per client tick by {@code ThaumGogglesSwap.refresh}.
 */
public final class GogglesState {

    public static volatile boolean active;
    public static volatile ItemStack swapStack;

    private GogglesState() {}
}
