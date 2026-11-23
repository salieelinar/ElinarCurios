package elinarcurios.compat;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;

/**
 * Builds the "Aspects" NBT payload that Waila's Thaumcraft addon would send when real goggles are
 * worn. Called from the (Waila-gated) mixin so the addon's own tooltip renderer can display the
 * aspect lines exactly like it does for goggles.
 */
public final class ThaumWailaAspectNbt {

    private ThaumWailaAspectNbt() {}

    /** Returns true when the tag was filled and the caller should short-circuit the original method. */
    public static boolean addAspects(final EntityPlayerMP player, final TileEntity tile, final NBTTagCompound tag) {
        if (player == null || tile == null || tag == null) return false;
        if (!(tile instanceof IAspectContainer)) return false;

        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        if (bag == null || !BagSettings.getBoolean(bag, BagSettings.REVEAL)) return false;

        final AspectList aspects = ((IAspectContainer) tile).getAspects();
        if (aspects == null) return false;

        final NBTTagList list = new NBTTagList();
        for (final Aspect aspect : aspects.getAspects()) {
            final int amount = aspects.getAmount(aspect);
            if (amount <= 0) continue;
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setString("key", aspect.getTag());
            entry.setInteger("value", amount);
            list.appendTag(entry);
        }
        if (list.tagCount() <= 0) return false;
        tag.setTag("Aspects", list);
        return true;
    }
}
