package elinarcurios.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.util.BagUtils;
import elinarcurios.util.DragonBuffs;

public class PlayerEventHandler {

    // ------------------------------------------------------------------ setup

    @SubscribeEvent
    public void registerBagProperty(final EntityEvent.EntityConstructing event) {
        if (event.entity instanceof EntityPlayer) {
            final EntityPlayer player = (EntityPlayer) event.entity;
            if (BagProperty.get(player) == null) {
                BagProperty.register(player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            BagUtils.syncWornBagToClient(event.player);
        }
    }

    // ------------------------------------------------------------------ worn-bag passive effects

    /** Applies the Dragon buffs (Night Vision) while the backpack is worn. */
    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        final EntityPlayer player = event.player;
        if (player == null || player.isDead) return;
        final ItemStack worn = BagUtils.getWornInfiniteBackpack(player);
        if (worn != null) {
            DragonBuffs.apply(player, worn);
        }
    }

    // ------------------------------------------------------------------ Kami invulnerability
    // Ported behavior of NH-Utilities' Kami Divine Ring (GPL-3.0, reimplemented with
    // Forge events instead of the original Entity.invulnerable mixin — same coverage,
    // since vanilla invulnerability only guards Entity.attackEntityFrom as well).

    private static boolean isProtected(final EntityPlayer player) {
        return !player.worldObj.isRemote && BagUtils.isWearingInfiniteBackpack(player);
    }

    /** Cancel all damage before knockback/fire are applied (like Entity.invulnerable). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(final LivingAttackEvent event) {
        if (event.entityLiving instanceof EntityPlayer && isProtected((EntityPlayer) event.entityLiving)) {
            event.setCanceled(true);
        }
    }

    /** Belt-and-suspenders: also cancel damage that reaches the final settlement step. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(final LivingHurtEvent event) {
        if (event.entityLiving instanceof EntityPlayer && isProtected((EntityPlayer) event.entityLiving)) {
            event.setCanceled(true);
        }
    }

    /** Prevent death while worn (replaces the ring's isDead=false resurrection). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDeath(final LivingDeathEvent event) {
        if (event.entityLiving instanceof EntityPlayer && isProtected((EntityPlayer) event.entityLiving)) {
            event.setCanceled(true);
            event.entityLiving.setHealth(Math.max(event.entityLiving.getHealth(), 1.0F));
        }
    }

    // ------------------------------------------------------------------ death drop (non-invulnerable path)

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlayerDeath(final LivingDeathEvent event) {
        if (!(event.entity instanceof EntityPlayer)) return;
        final EntityPlayer player = (EntityPlayer) event.entity;
        if (player.worldObj.isRemote) return;
        final ItemStack worn = BagUtils.getWornInfiniteBackpack(player);
        if (worn == null) return;
        // While worn the player is invulnerable: the HIGHEST death handler cancelled
        // this death, and the 1.7.10 event bus still invokes later listeners, so the
        // backpack must NOT be dropped or the buff removed here.
        if (BagUtils.isWearingInfiniteBackpack(player)) return;

        // This path only runs for genuine deaths (e.g. invulnerability disabled).
        DragonBuffs.remove(player);
        if (!player.getEntityWorld()
            .getGameRules()
            .getGameRuleBooleanValue("keepInventory")) {
            player.dropPlayerItemWithRandomChoice(worn.copy(), false);
        }
        final BagProperty prop = BagProperty.get(player);
        if (prop != null) prop.setWearable(null);
        BagUtils.syncWornBagToClient(player);
    }
}
