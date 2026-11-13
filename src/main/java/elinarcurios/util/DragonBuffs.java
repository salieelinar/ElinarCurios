package elinarcurios.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import elinarcurios.item.ItemInfiniteBackpack;

/**
 * Passive effects of the worn Infinite Backpack.
 * Currently only Night Vision (from AdventureBackpack2's Dragon backpack, LGPL-3.0).
 */
public final class DragonBuffs {

    private DragonBuffs() {}

    public static void apply(final EntityPlayer player, final ItemStack bag) {
        if (!ItemInfiniteBackpack.isBuffEnabled(bag, ItemInfiniteBackpack.BUFF_NIGHT_VISION)) return;

        PotionEffect nightVision = null;
        if (player.isPotionActive(Potion.nightVision.id)) {
            nightVision = player.getActivePotionEffect(Potion.nightVision);
        }
        if (nightVision == null || nightVision.getDuration() < 222) {
            player.addPotionEffect(new PotionEffect(Potion.nightVision.id, 239, -1, true));
        }
    }

    /** Remove the effect if it is the one this backpack grants. */
    public static void removeBuff(final EntityPlayer player, final String buffKey) {
        if (buffKey == null || !buffKey.equals(ItemInfiniteBackpack.BUFF_NIGHT_VISION)) return;
        if (player.isPotionActive(Potion.nightVision.id)) {
            final PotionEffect potion = player.getActivePotionEffect(Potion.nightVision);
            if (potion != null && potion.getAmplifier() == -1) {
                if (player.worldObj.isRemote) {
                    player.removePotionEffectClient(Potion.nightVision.id);
                } else {
                    player.removePotionEffect(Potion.nightVision.id);
                }
            }
        }
    }

    /** Remove all effects this backpack grants (unequip / death). */
    public static void remove(final EntityPlayer player) {
        for (String key : ItemInfiniteBackpack.BUFF_KEYS) {
            removeBuff(player, key);
        }
    }
}
