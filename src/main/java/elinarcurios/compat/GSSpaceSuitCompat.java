package elinarcurios.compat;

import java.lang.reflect.Method;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import elinarcurios.util.BagUtils;

/**
 * Reflection-only bridge for GalaxySpace's space-suit check.
 *
 * <p>
 * GalaxySpace decides whether the player is wearing a full space suit by asking each armour slot
 * for {@code IHazardProtector.protectsAgainst(stack, Hazard.SPACE)}. With no armour at all this
 * always fails, so the (GS-gated) mixin redirects that single call and answers "4 pieces" when the
 * Infinite Backpack is worn. The original call is kept through reflection so a non-backpack wearer
 * behaves exactly like unmodified GalaxySpace.
 */
public final class GSSpaceSuitCompat {

    private static final ThreadLocal<EntityLivingBase> CURRENT = new ThreadLocal<EntityLivingBase>();
    private static boolean resolved;
    private static Method originalCount;

    private GSSpaceSuitCompat() {}

    public static void remember(final EntityLivingBase entity) {
        CURRENT.set(entity);
    }

    public static void forget() {
        CURRENT.remove();
    }

    public static boolean wearingBackpackHere(final EntityLivingBase fallback) {
        EntityLivingBase entity = CURRENT.get();
        if (entity == null) entity = fallback;
        return entity instanceof EntityPlayer && BagUtils.isWearingInfiniteBackpack((EntityPlayer) entity);
    }

    public static int originalCount(final ItemStack helmet, final ItemStack chest, final ItemStack leggings,
        final ItemStack boots) {
        if (!resolved) {
            resolved = true;
            try {
                final Class<?> handler = Class.forName("galaxyspace.core.handler.GSSpaceSuitHandler");
                originalCount = handler
                    .getMethod("getSpaceSuitCount", ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class);
            } catch (Throwable ignored) {
                originalCount = null;
            }
        }
        if (originalCount == null) return 0;
        try {
            final Object value = originalCount.invoke(null, helmet, chest, leggings, boots);
            return value instanceof Integer ? ((Integer) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
