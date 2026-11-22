package elinarcurios.player;

import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.Config;
import elinarcurios.compat.WitcheryPowerCompat;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.util.BagUtils;
import elinarcurios.util.DisarmGuard;
import elinarcurios.util.DragonBuffs;
import elinarcurios.util.StatusCleanse;
import elinarcurios.util.ThaumWarpSupport;

public class PlayerEventHandler {

    /** PlayerPersisted tag holding backpacks rescued from a death drop (Forge copies it on respawn). */
    private static final String NBT_SAVED_BAGS = "elinarcurios:savedBags";

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
            restoreSavedBags(event.player);
        }
    }

    /** Witchery: drop the wearable power source on logout (the per-tick sync stops firing). */
    @SubscribeEvent
    public void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        if (Loader.isModLoaded("witchery")) {
            WitcheryPowerCompat.remove(event.player);
        }
    }

    /** Witchery: the integrated server keeps statics across worlds, so clear every source on stop. */
    @SubscribeEvent
    public void onServerStopping(final FMLServerStoppingEvent event) {
        if (Loader.isModLoaded("witchery")) {
            WitcheryPowerCompat.clearAll();
        }
    }

    /**
     * Forge only copies {@code IExtendedEntityProperties} on dimension changes, not on death respawn
     * (see EntityPlayer#clonePlayer). Put the worn backpack back on the fresh player and rescue any
     * backpack that was pulled out of the death drops.
     */
    @SubscribeEvent
    public void onClone(final net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        // Witchery: the old entity is about to be discarded; drop its power source. The fresh player
        // re-registers on his next tick (the UUID-keyed entry is reused).
        if (Loader.isModLoaded("witchery")) {
            WitcheryPowerCompat.remove(event.original);
        }
        if (!event.wasDeath || !(event.entityPlayer instanceof EntityPlayerMP)) return;

        final BagProperty original = BagProperty.get(event.original);
        BagProperty fresh = BagProperty.get(event.entityPlayer);
        if (fresh == null) {
            BagProperty.register(event.entityPlayer);
            fresh = BagProperty.get(event.entityPlayer);
        }
        if (original != null && original.getWearable() != null && fresh != null) {
            fresh.setWearable(
                original.getWearable()
                    .copy());
            BagUtils.syncWornBagToClient(event.entityPlayer);
        }
        restoreSavedBags(event.entityPlayer);
    }

    // ------------------------------------------------------------------ worn-bag passive effects

    /** Applies the Dragon buffs (Night Vision), zero-health rescue, debuff cleanse and warp wipe. */
    @SubscribeEvent
    public void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        final EntityPlayer player = event.player;
        if (player == null || player.worldObj == null || player.isDead) return;
        final ItemStack worn = BagUtils.getWornInfiniteBackpack(player);
        // Witchery soft link: publish the wearer as an altar power source while the bag is worn. The
        // guard lives at this call site, so the compat class is not loaded when Witchery is absent.
        if (!player.worldObj.isRemote && Loader.isModLoaded("witchery")) {
            WitcheryPowerCompat.update(player, worn != null && Config.witcheryFreeAltarPower);
        }
        if (worn == null) return;

        // Keep the client-side self-refresh: a 239-tick Night Vision effect is only stable when the
        // client re-applies it too. Server-side packets alone can leave the local effect to expire
        // (the player sees the backpack's Night Vision "switched off").
        DragonBuffs.apply(player, worn);
        if (player.worldObj.isRemote) return;

        // A mod that sets health to 0 without calling onDeath would otherwise leave a living corpse.
        if (Config.clampZeroHealth && player.getHealth() <= 0.0F) {
            player.setHealth(1.0F);
        }

        if (player.ticksExisted % 20 != 0) return;
        if (Config.cleanseNegativeEffects) {
            StatusCleanse.cleanse(player);
        }
        if (Config.clearThaumWarp && Loader.isModLoaded("Thaumcraft")) {
            ThaumWarpSupport.clear(player);
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

    // ------------------------------------------------------------------ data protection

    /**
     * PlayerDropsEvent is posted after vanilla has captured the death drops but before they enter
     * the world, so removing the backpack from the list keeps it out of the world entirely. It is
     * re-delivered on respawn by {@link #restoreSavedBags(EntityPlayer)} through the Forge-persisted
     * PlayerPersisted tag.
     */
    @SubscribeEvent
    public void onPlayerDrops(final PlayerDropsEvent event) {
        if (!Config.protectInfiniteBackpack) return;
        final EntityPlayer player = event.entityPlayer;
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;

        final Iterator<EntityItem> iterator = event.drops.iterator();
        while (iterator.hasNext()) {
            final EntityItem drop = iterator.next();
            if (drop == null) continue;
            final ItemStack stack = drop.getEntityItem();
            if (!ItemInfiniteBackpack.isInfiniteBackpack(stack)) continue;
            iterator.remove();
            rememberBag(player, stack.copy());
        }
    }

    /**
     * Stops {@code /clear <player>} (1.7.10 has the command) from wiping a backpack wearer's
     * inventory. Fires on the Forge bus before CommandHandler executes anything; cancelling it
     * leaves the inventory untouched.
     */
    @SubscribeEvent
    public void onCommand(final CommandEvent event) {
        if (!Config.blockClearCommand) return;
        if (event.command == null || !"clear".equals(event.command.getCommandName())) return;
        final World world = event.sender.getEntityWorld();
        if (world != null && world.isRemote) return;

        final EntityPlayer target = resolveClearTarget(event);
        if (target == null || target.worldObj == null || target.worldObj.isRemote) return;
        if (!BagUtils.isWearingInfiniteBackpack(target)) return;

        event.setCanceled(true);
        event.sender
            .addChatMessage(new ChatComponentText(StatCollector.translateToLocal("elinarcurios.clear.blocked")));
    }

    /**
     * Records a world item that carries exactly the current held stack of a backpack wearer and was
     * spawned next to him - the first half of a "drop then clear the hand" disarm. The matching
     * {@code setCurrentItemOrArmor(0, null)} is intercepted by {@code MixinEntityPlayerSlotGuard}.
     */
    @SubscribeEvent
    public void onEntityItemJoin(final EntityJoinWorldEvent event) {
        if (event.world == null || event.world.isRemote) return;
        if (!(event.entity instanceof EntityItem)) return;
        final EntityItem item = (EntityItem) event.entity;
        final ItemStack stack = item.getEntityItem();
        if (stack == null) return;

        EntityPlayer nearest = null;
        double nearestDistance = 9.0D;
        final List players = event.world.playerEntities;
        for (int i = 0; i < players.size(); i++) {
            final EntityPlayer player = (EntityPlayer) players.get(i);
            if (!BagUtils.isWearingInfiniteBackpack(player)) continue;
            final ItemStack held = player.getHeldItem();
            if (held == null || !ItemStack.areItemStacksEqual(held, stack)) continue;
            final double distance = item.getDistanceSq(player.posX, player.posY, player.posZ);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        if (nearest != null) DisarmGuard.expect(nearest, item);
    }

    // ------------------------------------------------------------------ helpers

    /** Resolves the player targeted by {@code /clear [player] [item] [data]}. */
    private static EntityPlayer resolveClearTarget(final CommandEvent event) {
        final String[] args = event.parameters;
        final MinecraftServer server = MinecraftServer.getServer();
        if (args != null && args.length > 0 && server != null) {
            final String name = args[0];
            final List players = server.getConfigurationManager().playerEntityList;
            for (int i = 0; i < players.size(); i++) {
                final EntityPlayer candidate = (EntityPlayer) players.get(i);
                if (candidate.getCommandSenderName()
                    .equalsIgnoreCase(name)) {
                    return candidate;
                }
            }
        }
        return event.sender instanceof EntityPlayer ? (EntityPlayer) event.sender : null;
    }

    private static void rememberBag(final EntityPlayer player, final ItemStack stack) {
        if (stack == null) return;
        final NBTTagCompound data = player.getEntityData();
        final NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        final NBTTagList list = persisted.getTagList(NBT_SAVED_BAGS, 10);
        list.appendTag(stack.writeToNBT(new NBTTagCompound()));
        persisted.setTag(NBT_SAVED_BAGS, list);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private static void restoreSavedBags(final EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        final NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) return;
        final NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(NBT_SAVED_BAGS, 9)) return;

        final NBTTagList list = persisted.getTagList(NBT_SAVED_BAGS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            final ItemStack stack = ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i));
            if (stack == null) continue;
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
        }
        persisted.removeTag(NBT_SAVED_BAGS);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        if (player instanceof EntityPlayerMP) {
            player.inventoryContainer.detectAndSendChanges();
        }
    }
}
