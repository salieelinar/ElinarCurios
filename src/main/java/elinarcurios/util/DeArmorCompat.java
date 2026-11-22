package elinarcurios.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

/**
 * Soft, name-based access to Draconic Evolution's armour pieces.
 *
 * <p>
 * Deliberately NOT a compile-time dependency: the items are resolved through {@link GameRegistry}
 * so the classes of that mod are never touched (a pack without DE simply resolves nothing).
 * Used for two things:
 * <ul>
 * <li>when the player wears a draconic chest piece the backpack leaves flight ownership to DE
 * (DE's own handler is suppressed while the backpack is worn, see the DE mixin);</li>
 * <li>the backpack must not revoke flight that DE is about to manage again.</li>
 * </ul>
 */
public final class DeArmorCompat {

    private static final String MOD_ID = "DraconicEvolution";

    private static boolean resolved;
    private static Item helm;
    private static Item chest;
    private static Item leggs;
    private static Item boots;

    private DeArmorCompat() {}

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        if (!Loader.isModLoaded(MOD_ID)) return;
        helm = GameRegistry.findItem(MOD_ID, "draconicHelm");
        chest = GameRegistry.findItem(MOD_ID, "draconicChest");
        leggs = GameRegistry.findItem(MOD_ID, "draconicLeggs");
        boots = GameRegistry.findItem(MOD_ID, "draconicBoots");
    }

    private static boolean isWearing(final EntityPlayer player, final int armorIndex, final Item item) {
        if (player == null || item == null) return false;
        final ItemStack stack = player.getCurrentArmor(armorIndex);
        return stack != null && stack.getItem() == item;
    }

    public static boolean hasDraconicChest(final EntityPlayer player) {
        resolve();
        return isWearing(player, 2, chest);
    }
}
