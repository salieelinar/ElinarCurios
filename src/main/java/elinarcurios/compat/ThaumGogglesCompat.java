package elinarcurios.compat;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Reflection-only access to Thaumcraft's goggles item.
 *
 * <p>
 * Deliberately no compile-time TC dependency here: the class is only touched from the (TC-gated)
 * render mixins, and a missing Thaumcraft simply resolves nothing.
 */
public final class ThaumGogglesCompat {

    private static boolean resolved;
    private static ItemStack goggles;

    private ThaumGogglesCompat() {}

    /** Goggles stack, or null when Thaumcraft (or its goggles item) is absent. */
    public static ItemStack goggles() {
        if (resolved) return goggles;
        resolved = true;
        try {
            final Class<?> configItems = Class.forName("thaumcraft.common.config.ConfigItems");
            final Object value = configItems.getField("itemGoggles")
                .get(null);
            if (value instanceof Item) goggles = new ItemStack((Item) value);
        } catch (Throwable ignored) {
            goggles = null;
        }
        return goggles;
    }
}
