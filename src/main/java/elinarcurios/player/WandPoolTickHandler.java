package elinarcurios.player;

import net.minecraft.entity.player.EntityPlayerMP;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Flushes dirty wand pools once per tick (one packet per player, not one per vis point) and sends the
 * full pool when the owner logs in.
 *
 * <p>
 * Registered from {@code ThaumcraftCompat#init} only when Thaumcraft is loaded: this class references
 * TC types through {@link WandPool}.
 */
public final class WandPoolTickHandler {

    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (event.player.worldObj == null || event.player.worldObj.isRemote) return;
        WandPool.flush((EntityPlayerMP) event.player);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (event.player.worldObj == null || event.player.worldObj.isRemote) return;
        final EntityPlayerMP player = (EntityPlayerMP) event.player;
        // Migrate the previous dev build's pool out of the player's NBT (one-time) BEFORE the sync.
        WandPool.migrateFromPlayerData(player);
        WandPool.sendFull(player);
    }
}
