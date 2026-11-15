package elinarcurios.player;

import net.minecraft.entity.player.EntityPlayer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.util.GhostMode;

/**
 * Keeps {@link GhostMode}'s per-tick state fresh and sweeps already-locked mob targets while any
 * ghost player is online.
 *
 * <p>
 * FML events only ({@code TickEvent}), registered on the FML bus in {@code CommonProxy.preInit}.
 * The sweep runs every 5 ticks (0.25s) and only touches creatures within 64 blocks of a ghost
 * player, so it is free when nobody uses ghost mode.
 */
public class GhostModeHandler {

    private int sweepTimer;

    @SubscribeEvent
    public void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        GhostMode.rebuild(
            FMLCommonHandler.instance()
                .getMinecraftServerInstance());
    }

    @SubscribeEvent
    public void onWorldTick(final TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (event.world == null || event.world.isRemote || !GhostMode.hasAnyGhost()) return;
        if (++sweepTimer < 5) return;
        sweepTimer = 0;
        final int playerCount = event.world.playerEntities.size();
        for (int i = 0; i < playerCount; i++) {
            final EntityPlayer player = (EntityPlayer) event.world.playerEntities.get(i);
            if (GhostMode.isGhost(player)) GhostMode.clearTargets(player);
        }
    }
}
