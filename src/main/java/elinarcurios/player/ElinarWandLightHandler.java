package elinarcurios.player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.block.BlockElinarLight;
import elinarcurios.item.ItemElinarWand;

/**
 * Makes the Celestial Wand light the surroundings like a torch (light level 14) while it is
 * held, or while it sits in a slot of the container the player currently has open (arcane
 * workbench wand slot, infinite backpack GUI, chest, ...).
 *
 * <p>
 * One invisible {@link BlockElinarLight} is kept at the feet (or just above the head, or at a
 * free side block when the player is in a tight space); it is moved as the player moves and
 * removed when the wand is no longer carried.
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

    /**
     * A wand counts as "carried" when it is held, or when it is in any slot of the container
     * the player currently has open. We deliberately do not skip the player-inventory slots:
     * some custom GUIs (infinite backpack, portable crafting) back their visible slots with the
     * player's inventory, and skipping them made the light disappear while the GUI was open.
     */
    public static boolean hasWand(final EntityPlayer player) {
        if (player == null) return false;
        final ItemStack held = player.getHeldItem();
        if (held != null && held.getItem() instanceof ItemElinarWand) return true;
        if (player.openContainer == null) return false;
        for (final Object object : player.openContainer.inventorySlots) {
            final Slot slot = (Slot) object;
            final ItemStack stack = slot.getStack();
            if (stack != null && stack.getItem() instanceof ItemElinarWand) return true;
        }
        return false;
    }

    public static final class Handler {

        @SubscribeEvent
        public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.player == null) return;
            final EntityPlayer player = event.player;
            if (player.worldObj == null || player.worldObj.isRemote) return;

            final boolean hasWand = hasWand(player);
            if (!hasWand || player.isDead) {
                clear(player);
                return;
            }

            final World world = player.worldObj;
            final int x = MathHelper.floor_double(player.posX);
            final int z = MathHelper.floor_double(player.posZ);
            final int feetY = MathHelper.floor_double(player.posY + 0.1D);
            final int headY = MathHelper.floor_double(player.posY + player.getEyeHeight());

            // Feet first, then above the head, then the four horizontal neighbours at feet
            // level (covers standing in a slab / low room / machine casing).
            final int[] dx = { 0, 0, 1, -1, 0, 0 };
            final int[] dz = { 0, 0, 0, 0, 1, -1 };
            final int[] dy = { feetY, headY + 1, feetY, feetY, feetY, feetY };
            int lightX = x;
            int lightY = Integer.MIN_VALUE;
            int lightZ = z;
            for (int i = 0; i < dx.length; i++) {
                final int tx = x + dx[i];
                final int tz = z + dz[i];
                final int ty = dy[i];
                if (isFree(world, tx, ty, tz)) {
                    lightX = tx;
                    lightY = ty;
                    lightZ = tz;
                    break;
                }
            }
            if (lightY == Integer.MIN_VALUE) {
                clear(player);
                return;
            }

            final LightRef old = ACTIVE.get(player.getUniqueID());
            if (old != null && old.world == world && old.x == lightX && old.y == lightY && old.z == lightZ) {
                // Re-place if the light block was removed/replaced while the ref stayed.
                if (world.getBlock(lightX, lightY, lightZ) != BlockElinarLight.instance) {
                    world.setBlock(lightX, lightY, lightZ, BlockElinarLight.instance, 0, 3);
                }
                return;
            }

            clear(player);
            world.setBlock(lightX, lightY, lightZ, BlockElinarLight.instance, 0, 3);
            ACTIVE.put(player.getUniqueID(), new LightRef(world, lightX, lightY, lightZ));
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
