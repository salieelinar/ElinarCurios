package elinarcurios.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.block.BlockElinarLight;
import elinarcurios.item.ItemElinarWand;

/**
 * Makes holding the Celestial Wand light the surroundings like a torch (light level 14).
 *
 * <p>
 * One invisible {@link BlockElinarLight} is kept at the holder's feet (or just above the head
 * when the feet block is occupied); it is moved as the player moves and removed when the wand
 * is no longer held. Every server tick only a single block comparison happens per holder.
 */
public final class ElinarWandLightHandler {

    private static final Map<UUID, LightRef> ACTIVE = new HashMap<UUID, LightRef>();

    private ElinarWandLightHandler() {}

    public static void register() {
        // The PlayerTickEvent/PlayerLoggedOutEvent live on the FML bus.
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(new Handler());
    }

    public static final class Handler {

        @SubscribeEvent
        public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.player == null) return;
            final EntityPlayer player = event.player;
            if (player.worldObj == null || player.worldObj.isRemote) return;

            final ItemStack held = player.getHeldItem();
            if (held == null || !(held.getItem() instanceof ItemElinarWand) || player.isDead) {
                clear(player);
                return;
            }

            final World world = player.worldObj;
            final int x = MathHelper.floor_double(player.posX);
            final int z = MathHelper.floor_double(player.posZ);
            final int feetY = MathHelper.floor_double(player.posY + 0.1D);
            final int headY = MathHelper.floor_double(player.posY + player.getEyeHeight());

            int y = Integer.MIN_VALUE;
            if (isFree(world, x, feetY, z)) {
                y = feetY;
            } else if (isFree(world, x, headY + 1, z)) {
                y = headY + 1;
            }
            if (y == Integer.MIN_VALUE) {
                clear(player);
                return;
            }

            final LightRef old = ACTIVE.get(player.getUniqueID());
            if (old != null && old.world == world && old.x == x && old.y == y && old.z == z) {
                // Re-place if the light block was removed/replaced while the ref stayed.
                if (world.getBlock(x, y, z) != BlockElinarLight.instance) {
                    world.setBlock(x, y, z, BlockElinarLight.instance, 0, 3);
                }
                return;
            }

            clear(player);
            world.setBlock(x, y, z, BlockElinarLight.instance, 0, 3);
            ACTIVE.put(player.getUniqueID(), new LightRef(world, x, y, z));
        }

        @SubscribeEvent
        public void onLoggedOut(final PlayerLoggedOutEvent event) {
            clear(event.player);
        }
    }

    private static boolean isFree(final World world, final int x, final int y, final int z) {
        if (world.isAirBlock(x, y, z)) return true;
        return world.getBlock(x, y, z) == BlockElinarLight.instance;
    }

    private static void clear(final EntityPlayer player) {
        if (player == null) return;
        final LightRef ref = ACTIVE.remove(player.getUniqueID());
        if (ref != null) clear(ref);
    }

    private static void clear(final LightRef ref) {
        if (ref != null && ref.world != null && ref.world.getBlock(ref.x, ref.y, ref.z) == BlockElinarLight.instance) {
            ref.world.setBlockToAir(ref.x, ref.y, ref.z);
        }
    }

    /** Called from the mod lifecycle before the world goes away. */
    public static void removeAll() {
        for (final LightRef ref : ACTIVE.values()) {
            clear(ref);
        }
        ACTIVE.clear();
    }

    private static final class LightRef {

        final World world;
        final int x;
        final int y;
        final int z;

        LightRef(final World world, final int x, final int y, final int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
