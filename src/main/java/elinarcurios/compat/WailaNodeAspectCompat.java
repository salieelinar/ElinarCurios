package elinarcurios.compat;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.SpecialChars;
import mcp.mobius.waila.api.impl.ModuleRegistrar;
import thaumcraft.api.IGoggles;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.nodes.INode;
import thaumcraft.common.blocks.BlockAiry;

/**
 * Waila support: show aura node aspects while the Infinite Backpack is worn.
 *
 * <p>
 * Waila's own Thaumcraft addon only sends aspect data when the queried player wears real goggles -
 * it reads the helmet slot directly on the server and bails out when it is empty, so no TC render
 * hook can ever affect it (two completely different code paths). Instead of mixing into Waila this
 * registers a regular body provider for the node block: the aspect lines are built on the client
 * with Waila's own {@code waila.tcaspect} renderer, so they look exactly like the goggles tooltip.
 * When real goggles are worn the provider stays silent and lets Waila's own data through.
 */
public final class WailaNodeAspectCompat {

    private WailaNodeAspectCompat() {}

    public static void register() {
        ModuleRegistrar.instance()
            .registerBodyProvider(new NodeAspectProvider(), BlockAiry.class);
    }

    public static final class NodeAspectProvider implements IWailaDataProvider {

        @Override
        public ItemStack getWailaStack(final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
            return null;
        }

        @Override
        public List<String> getWailaHead(final ItemStack stack, final List<String> tips,
            final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
            return tips;
        }

        @Override
        public List<String> getWailaBody(final ItemStack stack, final List<String> tips,
            final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
            if (!config.getConfig("thaumcraft.aspects")) return tips;
            final EntityPlayer player = accessor.getPlayer();
            if (player == null) return tips;
            final TileEntity tile = accessor.getTileEntity();
            if (!(tile instanceof INode)) return tips;

            // Real goggles are already handled by Waila's own addon; don't duplicate its lines.
            final ItemStack helmet = player.getCurrentArmor(3);
            if (helmet != null && helmet.getItem() instanceof IGoggles) return tips;

            final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
            if (bag == null || !BagSettings.getBoolean(bag, BagSettings.REVEAL)) return tips;

            final AspectList aspects = ((INode) tile).getAspects();
            if (aspects == null) return tips;
            for (final Aspect aspect : aspects.getAspects()) {
                final int amount = aspects.getAmount(aspect);
                if (amount <= 0) continue;
                tips.add(
                    SpecialChars.getRenderString("waila.tcaspect", aspect.getTag()) + SpecialChars.TAB
                        + SpecialChars.ALIGNRIGHT
                        + SpecialChars.WHITE
                        + amount);
            }
            return tips;
        }

        @Override
        public List<String> getWailaTail(final ItemStack stack, final List<String> tips,
            final IWailaDataAccessor accessor, final IWailaConfigHandler config) {
            return tips;
        }

        @Override
        public NBTTagCompound getNBTData(final EntityPlayerMP player, final TileEntity tile, final NBTTagCompound tag,
            final World world, final int x, final int y, final int z) {
            return tag;
        }
    }
}
