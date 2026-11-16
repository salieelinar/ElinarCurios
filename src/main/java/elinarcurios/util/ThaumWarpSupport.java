package elinarcurios.util;

import net.minecraft.entity.player.EntityPlayer;

import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.research.PlayerKnowledge;

/**
 * Wipes Thaumcraft 4.2.3.5 warp counters for a player.
 *
 * <p>
 * Warp is stored in TC's {@link PlayerKnowledge} (per player name), not in the player's NBT:
 * {@code warp} = permanent, {@code warpTemp}, {@code warpSticky} and {@code warpCount} (the counter
 * that picks which warp event fires). Permanent warp is only <em>accumulated</em> when a research
 * completes ({@code ResearchManager} splits the research warp into permanent + sticky once), so it
 * is not recomputed from the research list - zeroing it stays zero until a new warp source appears.
 * TC's own {@code /thaumcraft warp <player> set 0 PERM} uses the same setters.
 *
 * <p>
 * The call site must be guarded with {@code Loader.isModLoaded("Thaumcraft")} so this class (and
 * therefore the Thaumcraft types in its signatures) is never resolved when TC is absent.
 */
public final class ThaumWarpSupport {

    private ThaumWarpSupport() {}

    /** Called every 20 ticks (server side) while the backpack is worn. */
    public static void clear(final EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (Thaumcraft.proxy == null) return;

        final PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();
        if (knowledge == null) return;

        final String name = player.getCommandSenderName();
        final int permanent = knowledge.getWarpPerm(name);
        final int temporary = knowledge.getWarpTemp(name);
        final int sticky = knowledge.getWarpSticky(name);
        final int counter = knowledge.getWarpCounter(name);
        if (permanent + temporary + sticky + counter == 0) return;

        knowledge.setWarpPerm(name, 0);
        knowledge.setWarpTemp(name, 0);
        knowledge.setWarpSticky(name, 0);
        knowledge.setWarpCounter(name, 0);
    }
}
