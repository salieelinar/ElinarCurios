package elinarcurios.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import cpw.mods.fml.relauncher.ReflectionHelper;

/**
 * Removes every negative potion effect from a player and puts out the fire.
 *
 * <p>
 * Modelled on Draconic Evolution's {@code DraconicArmor.clearNegativeEffects}: 1.7.10 exposes the
 * "bad effect" flag as the protected field {@code Potion.isBadEffect} (obf {@code field_76418_K}),
 * so it has to be read reflectively. Unlike DE we do not cost energy, do not keep a per-effect
 * exception list and remove every bad effect at once (the backpack is sold as "no debuffs while
 * worn"). The result is cached per potion id, so the reflection runs at most once per effect type.
 *
 * <p>
 * Iterating {@link EntityPlayer#getActivePotionEffects()} directly and removing from it would throw
 * a {@link java.util.ConcurrentModificationException}; the collection is copied first.
 */
public final class StatusCleanse {

    private static final Map<Integer, Boolean> BAD_EFFECT_CACHE = new HashMap<Integer, Boolean>();

    private StatusCleanse() {}

    /** Called every 20 ticks (server side) while the backpack is worn. */
    public static void cleanse(final EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (player.isBurning()) player.extinguish();

        final List<PotionEffect> effects = new ArrayList<PotionEffect>(player.getActivePotionEffects());
        for (final PotionEffect effect : effects) {
            if (effect == null) continue;
            final int id = effect.getPotionID();
            // The backpack's own Night Vision must never be cleansed, even if a future mapping
            // change makes the isBadEffect lookup return the wrong value.
            if (id == Potion.nightVision.id) continue;
            if (!isBad(id)) continue;
            player.removePotionEffect(id);
        }
    }

    private static boolean isBad(final int id) {
        final Boolean cached = BAD_EFFECT_CACHE.get(Integer.valueOf(id));
        if (cached != null) return cached.booleanValue();

        boolean bad = false;
        if (id >= 0 && id < Potion.potionTypes.length) {
            final Potion potion = Potion.potionTypes[id];
            if (potion != null) bad = readBadFlag(potion);
        }
        BAD_EFFECT_CACHE.put(Integer.valueOf(id), Boolean.valueOf(bad));
        return bad;
    }

    private static boolean readBadFlag(final Potion potion) {
        try {
            final Object value = ReflectionHelper
                .getPrivateValue(Potion.class, potion, "isBadEffect", "field_76418_K", "J");
            return Boolean.TRUE.equals(value);
        } catch (final Throwable ignored) {
            // Unknown accessor layout: treat as harmless rather than risk removing a buff.
            return false;
        }
    }
}
