package elinarcurios.player;

import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ServersideAttributeMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S20PacketEntityProperties;

import elinarcurios.util.BagSettings;

/**
 * Health bonuses of the worn Infinite Backpack.
 *
 * <p>
 * Both values are plain server-side effects, not potion effects: {@link BagSettings#HEALTH_BONUS} is
 * an additive {@code maxHealth} attribute modifier and {@link BagSettings#HEALTH_REGEN} heals the
 * player directly every tick (stopping at full health).
 *
 * <p>
 * The modifier is re-applied unconditionally when the player changes dimension ({@link #resync}).
 * 1.7.10 sends attribute values to the client only when the server marks the attribute dirty, and the
 * client rebuilds its {@code EntityPlayerSP} on a dimension change (losing every modifier). Re-adding
 * the same modifier marks it dirty again; {@link #resync} also sends an attribute snapshot
 * immediately instead of waiting for the next tracker tick. This is the same dirty-marking mechanism
 * Spice of Life's carrot hearts use; Tinkers' Construct's heart canisters only recalculate when the
 * stored bonus value changes, so their modifier is not resent after a dimension change.
 */
public final class BackpackHealthHandler {

    /** Stable modifier id: a dimension change must re-apply the same modifier, not stack a new one. */
    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("8f4c2b1e-6a7d-4f3c-9b2e-1d5a7c8e9f01");
    private static final String HEALTH_MODIFIER_NAME = "elinarcurios.bag.health";

    private BackpackHealthHandler() {}

    /** Called every tick while the backpack is worn. */
    public static void update(final EntityPlayer player, final ItemStack bag) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote || player.isDead) return;
        applyModifier(player, bonus(bag), false);
        heal(player, bag);
    }

    /**
     * Re-applies the (unchanged) modifier to force an attribute update packet. Called after a
     * dimension change, login and respawn.
     */
    public static void resync(final EntityPlayer player, final ItemStack bag) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        final int bonus = bag == null ? 0 : bonus(bag);
        applyModifier(player, bonus, true);
        if (bonus > 0) sendAttributeSnapshot(player);
    }

    /** Removes the modifier (backpack taken off, player data reset). */
    public static void removeAll(final EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        final IAttributeInstance attribute = maxHealth(player);
        if (attribute == null) return;
        final AttributeModifier existing = attribute.getModifier(HEALTH_MODIFIER_ID);
        if (existing == null) return;
        attribute.removeModifier(existing);
        clampHealth(player);
    }

    private static void applyModifier(final EntityPlayer player, final int bonus, final boolean force) {
        final IAttributeInstance attribute = maxHealth(player);
        if (attribute == null) return;

        final AttributeModifier existing = attribute.getModifier(HEALTH_MODIFIER_ID);
        if (bonus <= 0) {
            if (existing != null) {
                attribute.removeModifier(existing);
                clampHealth(player);
            }
            return;
        }
        if (!force && existing != null && existing.getAmount() == bonus) return;
        if (existing != null) attribute.removeModifier(existing);
        attribute.applyModifier(new AttributeModifier(HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, bonus, 0));
        clampHealth(player);
    }

    private static void heal(final EntityPlayer player, final ItemStack bag) {
        final int amount = (int) BagSettings.getFloat(bag, BagSettings.HEALTH_REGEN);
        if (amount <= 0) return;
        final float health = player.getHealth();
        if (health <= 0.0F || health >= player.getMaxHealth()) return;
        player.setHealth(Math.min(player.getMaxHealth(), health + Math.min(amount, 64)));
    }

    private static int bonus(final ItemStack bag) {
        final int value = (int) BagSettings.getFloat(bag, BagSettings.HEALTH_BONUS);
        return Math.max(0, Math.min(value, 65535));
    }

    private static IAttributeInstance maxHealth(final EntityPlayer player) {
        return player.getEntityAttribute(SharedMonsterAttributes.maxHealth);
    }

    /**
     * Sends the watched attributes right away (the tracker would only resend them on its next tick).
     * The client rebuilds its player entity on dimension changes / respawns, so waiting for the
     * tracker leaves a visible frame with the default 10 hearts.
     */
    private static void sendAttributeSnapshot(final EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return;
        final EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        if (serverPlayer.playerNetServerHandler == null) return;
        if (!(serverPlayer.getAttributeMap() instanceof ServersideAttributeMap)) return;
        final ServersideAttributeMap attributes = (ServersideAttributeMap) serverPlayer.getAttributeMap();
        serverPlayer.playerNetServerHandler
            .sendPacket(new S20PacketEntityProperties(serverPlayer.getEntityId(), attributes.getWatchedAttributes()));
    }

    private static void clampHealth(final EntityPlayer player) {
        final float max = player.getMaxHealth();
        if (player.getHealth() > max) player.setHealth(max);
    }
}
