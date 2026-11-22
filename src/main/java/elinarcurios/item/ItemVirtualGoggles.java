package elinarcurios.item;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import thaumcraft.api.IGoggles;
import thaumcraft.api.nodes.IRevealer;

/**
 * Invisible helper item: the exact same Thaumcraft interfaces the Draconic helmet implements
 * ({@link IGoggles} + {@link IRevealer}), but not {@code ItemArmor}, so it never renders and never
 * provides armour points.
 *
 * <p>
 * Thaumcraft, Waila and every other mod decide "does this player wear goggles?" by looking at the
 * helmet slot only. While the Infinite Backpack is worn, this stack is placed in the (otherwise
 * empty) helmet slot, which makes all of those systems behave exactly as if the draconic helmet
 * were worn. The visibility flags themselves still honour the backpack's "reveal"/"nodes" toggles.
 */
public class ItemVirtualGoggles extends Item implements IGoggles, IRevealer {

    public static ItemVirtualGoggles instance;

    public ItemVirtualGoggles() {
        setUnlocalizedName("virtualGoggles");
        // Reuse Thaumcraft's own goggles icon; this item is only ever visible in the inventory.
        setTextureName("thaumcraft:gogglesrevealing");
        setMaxStackSize(1);
    }

    @Override
    public boolean showIngamePopups(final ItemStack stack, final EntityLivingBase player) {
        return settingEnabled(player, BagSettings.REVEAL);
    }

    @Override
    public boolean showNodes(final ItemStack stack, final EntityLivingBase player) {
        return settingEnabled(player, BagSettings.NODES);
    }

    private static boolean settingEnabled(final EntityLivingBase player, final int setting) {
        if (!(player instanceof EntityPlayer)) return false;
        final ItemStack bag = BagUtils.getWornInfiniteBackpack((EntityPlayer) player);
        return bag != null && BagSettings.getBoolean(bag, setting);
    }
}
