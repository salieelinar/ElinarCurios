package elinarcurios.player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.block.material.Material;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.ElinarCurios;
import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import elinarcurios.util.DeArmorCompat;

/**
 * Movement / auxiliary abilities granted by the worn Infinite Backpack.
 *
 * <p>
 * Replaces the Draconic armour's mobility package (flight, flight speed, vertical acceleration,
 * flight lock, inertia cancellation, walk speed, jump boost, hill step and the water/air mining
 * bonus). Everything is server-authoritative where it matters; the purely client-side bits
 * (fly speed, vertical motion, inertia, step height) go through the client proxy.
 *
 * <p>
 * While the backpack is worn Draconic Evolution's own handler is suppressed by a mixin, so these
 * values are the only source. If the backpack is taken off and a draconic chest is still worn, the
 * flight state is handed back to DE instead of being revoked for one tick.
 */
public final class BackpackAbilityHandler {

    private static final UUID WALK_SPEED_ID = UUID.fromString("6d1f3c4a-7a52-4f3f-9f3f-2f1c8b0d6a11");
    private static final String WALK_SPEED_NAME = "elinarcurios.bag.walkSpeed";
    private static final float HILL_STEP_HEIGHT = 1.0F;
    private static final float DEFAULT_STEP_HEIGHT = 0.5F;

    private final Set<UUID> flightGranted = new HashSet<UUID>();
    private final Map<UUID, Float> savedStepHeight = new HashMap<UUID, Float>();

    // ---------------------------------------------------------------- tick

    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        final EntityPlayer player = event.player;
        if (player == null || player.worldObj == null || player.isDead) return;

        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        if (bag == null) {
            // No bag: give everything back. Flight is only revoked when DE is not going to
            // pick it up again (a worn draconic chest keeps the player flying without a gap);
            // in that case the grant is simply forgotten and DE takes over on its next tick.
            if (DeArmorCompat.hasDraconicChest(player)) flightGranted.remove(player.getUniqueID());
            else revokeFlight(player);
            removeWalkSpeed(player);
            restoreStepHeight(player);
            return;
        }

        applyFlight(player, bag);
        applyWalkSpeed(player, bag);
        applyStepHeight(player, bag);
    }

    private void applyFlight(final EntityPlayer player, final ItemStack bag) {
        if (!BagSettings.getBoolean(bag, BagSettings.FLIGHT)) {
            flightGranted.remove(player.getUniqueID());
            revokeFlight(player);
            return;
        }
        flightGranted.add(player.getUniqueID());
        if (!player.capabilities.allowFlying) {
            player.capabilities.allowFlying = true;
            if (!player.worldObj.isRemote) player.sendPlayerAbilities();
        }
        if (BagSettings.getBoolean(bag, BagSettings.FLY_LOCK)) {
            player.capabilities.isFlying = true;
        }
        if (player.worldObj.isRemote) {
            ElinarCurios.proxy.applyBackpackClientFlight(player, bag);
        }
    }

    private void revokeFlight(final EntityPlayer player) {
        if (!flightGranted.remove(player.getUniqueID())) return;
        if (player.capabilities.isCreativeMode) return;
        player.capabilities.allowFlying = false;
        player.capabilities.isFlying = false;
        if (!player.worldObj.isRemote) {
            player.sendPlayerAbilities();
        } else {
            player.capabilities.setFlySpeed(0.05F);
        }
    }

    private void applyWalkSpeed(final EntityPlayer player, final ItemStack bag) {
        final float speed = BagSettings.getFloat(bag, BagSettings.WALK_SPEED);
        final IAttributeInstance attribute = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        final AttributeModifier existing = attribute.getModifier(WALK_SPEED_ID);
        if (speed <= 0F) {
            if (existing != null) attribute.removeModifier(existing);
            return;
        }
        if (existing == null) {
            attribute.applyModifier(new AttributeModifier(WALK_SPEED_ID, WALK_SPEED_NAME, speed, 1));
        } else if (existing.getAmount() != speed) {
            attribute.removeModifier(existing);
            attribute.applyModifier(new AttributeModifier(WALK_SPEED_ID, WALK_SPEED_NAME, speed, 1));
        }
    }

    private void removeWalkSpeed(final EntityPlayer player) {
        final IAttributeInstance attribute = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        final AttributeModifier existing = attribute.getModifier(WALK_SPEED_ID);
        if (existing != null) attribute.removeModifier(existing);
    }

    private void applyStepHeight(final EntityPlayer player, final ItemStack bag) {
        if (!BagSettings.getBoolean(bag, BagSettings.HILL_STEP)) {
            restoreStepHeight(player);
            return;
        }
        final UUID id = player.getUniqueID();
        if (!savedStepHeight.containsKey(id)) savedStepHeight.put(id, player.stepHeight);
        if (player.stepHeight < HILL_STEP_HEIGHT) player.stepHeight = HILL_STEP_HEIGHT;
    }

    private void restoreStepHeight(final EntityPlayer player) {
        final Float saved = savedStepHeight.remove(player.getUniqueID());
        if (saved != null) {
            player.stepHeight = saved.floatValue();
        } else if (player.stepHeight > DEFAULT_STEP_HEIGHT) {
            player.stepHeight = DEFAULT_STEP_HEIGHT;
        }
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public void onLivingJump(final LivingEvent.LivingJumpEvent event) {
        if (!(event.entityLiving instanceof EntityPlayer)) return;
        final EntityPlayer player = (EntityPlayer) event.entityLiving;
        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        if (bag == null) return;
        final float jump = BagSettings.getFloat(bag, BagSettings.JUMP);
        if (jump > 0F) player.motionY += jump * 0.1F;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onBreakSpeed(final PlayerEvent.BreakSpeed event) {
        final EntityPlayer player = event.entityPlayer;
        if (player == null) return;
        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        if (bag == null || !BagSettings.getBoolean(bag, BagSettings.MINE_BOOST)) return;
        // Same rule as the draconic chest: five times while under water, five times while airborne
        // (both together multiply, exactly like Draconic Evolution does).
        if (player.isInsideOfMaterial(Material.water)) event.newSpeed *= 5F;
        if (!player.onGround) event.newSpeed *= 5F;
    }
}
