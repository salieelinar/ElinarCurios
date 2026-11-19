package elinarcurios.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.item.ItemElinarWand;
import elinarcurios.util.WandPoolConstants;
import thaumcraft.api.aspects.Aspect;

/**
 * Flushes dirty wand pools once per tick (one packet per player, not one per vis point) and sends the
 * full pool when the owner logs in.
 *
 * <p>
 * Also runs the crucible-style decomposition: every {@link #DECOMPOSE_INTERVAL} ticks, if the online
 * player carries a bound Celestial Wand in the main inventory, one random <b>compound</b> aspect is
 * removed (1 raw) and <b>every</b> component of that aspect is added (1 raw each). Primals are never
 * selected (the crucible's random re-pick + spill is skipped). Compound aspects are the late-game
 * pool content, so 1 -> all components is intentionally generous.
 *
 * <p>
 * Registered from {@code ThaumcraftCompat#init} only when Thaumcraft is loaded: this class references
 * TC types through {@link WandPool}.
 */
public final class WandPoolTickHandler {

    /** Crucible cadence: 101 ticks ≈ 5.05 s. */
    private static final int DECOMPOSE_INTERVAL = 101;

    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof EntityPlayerMP)) return;
        if (event.player.worldObj == null || event.player.worldObj.isRemote) return;
        final EntityPlayerMP player = (EntityPlayerMP) event.player;

        if (player.ticksExisted % DECOMPOSE_INTERVAL == 0) {
            decompose(player);
        }
        WandPool.flush(player);
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

    /**
     * Crucible-style decomposition for the online owner's pool. The wand stack is used only to
     * resolve the owner UUID and to refresh its NBT snapshot; the authoritative pool is world data.
     */
    private static void decompose(final EntityPlayerMP player) {
        final ItemStack wand = findWand(player);
        if (wand == null) return;

        final UUID owner = WandPool.ownerId(wand);
        if (owner == null) return;

        final Map<String, Long> pool = WandPool.poolFor(owner);
        final List<Aspect> compounds = new ArrayList<Aspect>();
        for (final Aspect aspect : Aspect.aspects.values()) {
            if (aspect.isPrimal()) continue; // never pick the six primals
            final Long value = pool.get(aspect.getTag());
            if (value != null && value > 0L) compounds.add(aspect);
        }
        if (compounds.isEmpty()) return;

        final Aspect chosen = compounds.get(player.worldObj.rand.nextInt(compounds.size()));
        final long current = WandPool.getForOwner(wand, chosen);
        if (current <= 0L) return;

        // 1 raw of the compound -> 1 raw of EVERY component (all components, not a random one).
        WandPool.setForOwner(wand, chosen, current - 1L);
        final Aspect[] components = chosen.getComponents();
        if (components != null) {
            for (final Aspect component : components) {
                final long value = WandPool.getForOwner(wand, component);
                if (value < WandPoolConstants.CAPACITY) {
                    WandPool.setForOwner(wand, component, value + 1L);
                }
            }
        }
        WandPool.writeSnapshotFor(wand);
    }

    private static ItemStack findWand(final EntityPlayerMP player) {
        for (final ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() instanceof ItemElinarWand && WandPool.hasOwner(stack)) {
                return stack;
            }
        }
        return null;
    }
}
