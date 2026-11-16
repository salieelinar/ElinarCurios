package elinarcurios.util;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Helper for the "stolen held item" guard.
 *
 * <p>
 * Several mods implement disarming by first spawning an {@link EntityItem} with the player's held
 * stack and then clearing the held slot (Witchery's Expelliarmus symbol does exactly this). Blocking
 * only the slot clear would duplicate the stack, so the spawn is recorded here and the slot-clear
 * guard removes the just-spawned item entity before cancelling the clear. Both steps happen in the
 * same call stack / tick, so a tick number is enough to pair them.
 *
 * <p>
 * Only server-side players are tracked, on the server thread.
 */
public final class DisarmGuard {

    private static final Map<EntityPlayer, Pending> PENDING = new WeakHashMap<EntityPlayer, Pending>();

    private DisarmGuard() {}

    public static void expect(final EntityPlayer player, final EntityItem item) {
        if (player == null || item == null) return;
        PENDING.put(player, new Pending(item, player.ticksExisted));
    }

    /**
     * @return true when a matching disarm drop was recorded for this player in the current tick; the
     *         recorded item entity is removed so the caller can safely cancel the slot clear.
     */
    public static boolean consume(final EntityPlayer player) {
        if (player == null) return false;
        final Pending pending = PENDING.get(player);
        if (pending == null) return false;
        if (pending.tick != player.ticksExisted) return false;
        PENDING.remove(player);
        if (pending.item != null && !pending.item.isDead) pending.item.setDead();
        return true;
    }

    private static final class Pending {

        private final EntityItem item;
        private final int tick;

        private Pending(final EntityItem item, final int tick) {
            this.item = item;
            this.tick = tick;
        }
    }
}
