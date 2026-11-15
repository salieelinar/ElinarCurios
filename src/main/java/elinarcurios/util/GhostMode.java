package elinarcurios.util;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import elinarcurios.item.ItemInfiniteBackpack;

/**
 * Server-side "ghost mode": while the worn backpack has the ghost toggle enabled, hostile mobs
 * neither pick the player as a target ({@code EntityAITarget#isSuitableTarget}) nor find him
 * through {@code World#getClosestVulnerablePlayer*}.
 *
 * <p>
 * The ghost set is rebuilt from the online player list once per server tick, so toggling the buff
 * takes effect within one tick and stale player entities (dimension travel creates a new
 * {@code EntityPlayerMP}) cannot linger in the set. Both AI entry points are hooked with mixins
 * ({@code MixinEntityAITarget}, {@code MixinWorldClosestVulnerablePlayer}); already-locked targets
 * are dropped by {@link #clearTargets(EntityPlayer)} from {@code GhostModeHandler}.
 *
 * <p>
 * Deliberately does NOT touch {@code player.capabilities.disableDamage}: that flag would also stop
 * food from being eaten, hunger exhaustion and the nether portal delay, and would make other mods
 * treat the player as a creative/invulnerable one.
 */
public final class GhostMode {

    /**
     * Rebuilt every server tick. Concurrent because AI (server thread) and a few client-side
     * lookups may read it at the same time; {@code Entity} keeps identity equals/hashCode, so
     * recreation of player entities cannot leave stale equal-looking entries.
     */
    private static final Set<EntityPlayer> GHOSTS = Collections
        .newSetFromMap(new ConcurrentHashMap<EntityPlayer, Boolean>());

    private GhostMode() {}

    public static void rebuild(final MinecraftServer server) {
        GHOSTS.clear();
        if (server == null) return;
        final List players = server.getConfigurationManager().playerEntityList;
        for (int i = 0; i < players.size(); i++) {
            final EntityPlayer player = (EntityPlayer) players.get(i);
            if (isGhostByBag(player)) GHOSTS.add(player);
        }
    }

    public static boolean hasAnyGhost() {
        return !GHOSTS.isEmpty();
    }

    public static boolean isGhost(final Entity entity) {
        return entity instanceof EntityPlayer && GHOSTS.contains(entity);
    }

    /** Direct NBT check, used to rebuild the set (and as the single source of truth). */
    public static boolean isGhostByBag(final EntityPlayer player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return false;
        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        return bag != null && ItemInfiniteBackpack.isBuffEnabled(bag, ItemInfiniteBackpack.BUFF_GHOST);
    }

    /**
     * Drops every existing target that points at the ghost player. Covers both target slots of
     * 1.7.10: {@code EntityLiving.attackTarget} (AI mobs) and {@code EntityCreature.entityToAttack}
     * (endermen, spiders, slimes, silverfish, and mobs that were hit by the player).
     */
    public static void clearTargets(final EntityPlayer player) {
        final World world = player.worldObj;
        if (world == null || world.isRemote) return;
        final double radius = 64.0D;
        final List entities = world
            .getEntitiesWithinAABB(EntityCreature.class, player.boundingBox.expand(radius, radius, radius));
        for (int i = 0; i < entities.size(); i++) {
            final EntityCreature creature = (EntityCreature) entities.get(i);
            if (creature.getEntityToAttack() == player) creature.setTarget(null);
            if (creature.getAttackTarget() == player) creature.setAttackTarget(null);
        }
    }

    /**
     * Vanilla-faithful replacement for {@code World#getClosestVulnerablePlayer} that also skips
     * ghost players, so a ghost player never hides a non-ghost one standing further away.
     */
    public static EntityPlayer findClosestOtherPlayer(final World world, final double x, final double y, final double z,
        final double distance) {
        double closest = -1.0D;
        EntityPlayer result = null;
        for (int i = 0; i < world.playerEntities.size(); i++) {
            final EntityPlayer candidate = (EntityPlayer) world.playerEntities.get(i);
            if (candidate.capabilities.disableDamage || !candidate.isEntityAlive() || GHOSTS.contains(candidate)) {
                continue;
            }
            final double distSq = candidate.getDistanceSq(x, y, z);
            double range = distance;
            if (candidate.isSneaking()) range *= 0.8D;
            if (candidate.isInvisible()) {
                float visibility = candidate.getArmorVisibility();
                if (visibility < 0.1F) visibility = 0.1F;
                range *= 0.7F * visibility;
            }
            if ((distance < 0.0D || distSq < range * range) && (closest == -1.0D || distSq < closest)) {
                closest = distSq;
                result = candidate;
            }
        }
        return result;
    }
}
