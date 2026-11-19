package elinarcurios.player;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import elinarcurios.ElinarCurios;
import elinarcurios.network.WandPoolSyncPacket;
import elinarcurios.util.WandPoolConstants;
import thaumcraft.api.aspects.Aspect;

/**
 * The shared vis pool of the Celestial Wand (天玄之杖).
 *
 * <p>
 * Design (2026-11-19, player-approved):
 * <ul>
 * <li>authoritative storage: a 6 x long pool inside the player's
 * {@code PlayerPersisted} tag — survives death and dimension changes because Forge copies that tag in
 * {@code EntityPlayer#clonePlayer};</li>
 * <li>the wand itself only stores its owner UUID/name plus a display snapshot, so every wand of one
 * player reads the same pool;</li>
 * <li>binding is permanent on first use: the wand may sit inside a machine (arcane workbench, enchanter)
 * where there is no player context, so the owner must be resolvable from the item alone.</li>
 * </ul>
 *
 * <p>
 * Only loaded when Thaumcraft is present (all call sites are TC-only classes).
 */
public final class WandPool {

    /** Fixed order for storage and for the sync packet (Aspect#getPrimalAspects() has HashMap order). */
    public static final Aspect[] PRIMALS = { Aspect.AIR, Aspect.EARTH, Aspect.FIRE, Aspect.WATER, Aspect.ORDER,
        Aspect.ENTROPY };

    /** Player-persisted compound holding the six longs. */
    public static final String PLAYER_POOL_TAG = "elinarcurios:wandPool";
    /** Wand root NBT: owner UUID string. */
    public static final String OWNER_TAG = "elinarOwner";
    /** Wand root NBT: owner display name (used by the client to match "is this wand mine"). */
    public static final String OWNER_NAME_TAG = "elinarOwnerName";
    /** Wand root NBT: display snapshot compound (longs) for foreign wands / direct NBT readers. */
    public static final String SNAPSHOT_TAG = "elinarPool";

    private static final Set<UUID> DIRTY = new HashSet<UUID>();

    private WandPool() {}

    // ---------------------------------------------------------------- aspects

    public static int indexOf(final Aspect aspect) {
        for (int i = 0; i < PRIMALS.length; i++) {
            if (PRIMALS[i] == aspect) return i;
        }
        return -1;
    }

    public static long clamp(final long value) {
        if (value <= 0L) return 0L;
        return value >= WandPoolConstants.CAPACITY ? WandPoolConstants.CAPACITY : value;
    }

    /** The int value vanilla Thaumcraft getters expose; its dial cannot render past Integer.MAX_VALUE. */
    public static int face(final long value) {
        return value >= (long) WandPoolConstants.INT_FACE ? WandPoolConstants.INT_FACE : (int) value;
    }

    // ---------------------------------------------------------------- player data

    private static NBTTagCompound persisted(final EntityPlayer player) {
        final NBTTagCompound data = player.getEntityData();
        final NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }

    private static NBTTagCompound poolTag(final EntityPlayer player, final boolean create) {
        if (player == null) return null;
        final NBTTagCompound persisted = persisted(player);
        if (!persisted.hasKey(PLAYER_POOL_TAG, 10)) {
            if (!create) return null;
            persisted.setTag(PLAYER_POOL_TAG, new NBTTagCompound());
        }
        return persisted.getCompoundTag(PLAYER_POOL_TAG);
    }

    public static long get(final EntityPlayer player, final Aspect aspect) {
        if (player == null || aspect == null) return 0L;
        final NBTTagCompound pool = poolTag(player, false);
        if (pool == null || !pool.hasKey(aspect.getTag(), 4)) return 0L;
        return clamp(pool.getLong(aspect.getTag()));
    }

    public static void set(final EntityPlayer player, final Aspect aspect, final long value) {
        if (player == null || aspect == null) return;
        final long next = clamp(value);
        if (next == get(player, aspect)) return;
        poolTag(player, true).setLong(aspect.getTag(), next);
        DIRTY.add(player.getUniqueID());
    }

    public static void add(final EntityPlayer player, final Aspect aspect, final long value) {
        if (value <= 0L) return;
        set(player, aspect, get(player, aspect) + value);
    }

    public static long[] values(final EntityPlayer player) {
        final long[] out = new long[PRIMALS.length];
        for (int i = 0; i < PRIMALS.length; i++) out[i] = get(player, PRIMALS[i]);
        return out;
    }

    // ---------------------------------------------------------------- ownership

    public static String ownerName(final ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return null;
        final NBTTagCompound tag = stack.stackTagCompound;
        return tag.hasKey(OWNER_NAME_TAG, 8) ? tag.getString(OWNER_NAME_TAG) : null;
    }

    public static UUID ownerId(final ItemStack stack) {
        if (stack == null || stack.stackTagCompound == null) return null;
        final NBTTagCompound tag = stack.stackTagCompound;
        if (!tag.hasKey(OWNER_TAG, 8)) return null;
        try {
            return UUID.fromString(tag.getString(OWNER_TAG));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean hasOwner(final ItemStack stack) {
        return ownerName(stack) != null || ownerId(stack) != null;
    }

    public static boolean isOwnedBy(final ItemStack stack, final EntityPlayer player) {
        final String name = ownerName(stack);
        return name != null && player != null && name.equalsIgnoreCase(player.getCommandSenderName());
    }

    /** Online owner of the wand, or null when the wand is unbound / its owner is offline. */
    public static EntityPlayer resolveOwner(final ItemStack stack) {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        final UUID id = ownerId(stack);
        final String name = ownerName(stack);
        if (id == null && name == null) return null;
        final List players = server.getConfigurationManager().playerEntityList;
        EntityPlayer byName = null;
        for (int i = 0; i < players.size(); i++) {
            final EntityPlayer candidate = (EntityPlayer) players.get(i);
            if (id != null && id.equals(candidate.getUniqueID())) return candidate;
            if (id == null && name != null && name.equalsIgnoreCase(candidate.getCommandSenderName())) {
                byName = candidate;
            }
        }
        return byName;
    }

    /**
     * Permanent first-use binding. Also migrates the legacy per-wand int vis (pre-pool saves) into the
     * player pool exactly once, then removes the old keys.
     */
    public static void bind(final ItemStack stack, final EntityPlayer player) {
        if (stack == null || player == null || !(player instanceof EntityPlayerMP) || hasOwner(stack)) return;
        final NBTTagCompound tag = ensureTag(stack);
        for (final Aspect aspect : PRIMALS) {
            if (!tag.hasKey(aspect.getTag(), 3)) continue;
            final int legacy = tag.getInteger(aspect.getTag());
            if (legacy > 0) add(player, aspect, legacy);
            tag.removeTag(aspect.getTag());
        }
        tag.setString(
            OWNER_TAG,
            player.getUniqueID()
                .toString());
        tag.setString(OWNER_NAME_TAG, player.getCommandSenderName());
        writeSnapshot(stack, player);
    }

    // ---------------------------------------------------------------- display snapshot

    public static long snapshot(final ItemStack stack, final Aspect aspect) {
        if (stack == null || stack.stackTagCompound == null || aspect == null) return 0L;
        final NBTTagCompound tag = stack.stackTagCompound;
        if (!tag.hasKey(SNAPSHOT_TAG, 10)) return 0L;
        final NBTTagCompound snap = tag.getCompoundTag(SNAPSHOT_TAG);
        return snap.hasKey(aspect.getTag(), 4) ? clamp(snap.getLong(aspect.getTag())) : 0L;
    }

    public static boolean snapshotMatches(final ItemStack stack, final EntityPlayer owner) {
        for (final Aspect aspect : PRIMALS) {
            if (snapshot(stack, aspect) != get(owner, aspect)) return false;
        }
        return true;
    }

    /**
     * Writes both the long display snapshot (exact numbers for tooltips) and the legacy int aspect tags
     * (so addons that read wand NBT directly, e.g. ThaumicHorizons' familiars, still see a sane value).
     */
    public static void writeSnapshot(final ItemStack stack, final EntityPlayer owner) {
        if (stack == null || owner == null) return;
        final NBTTagCompound tag = ensureTag(stack);
        final NBTTagCompound snap = new NBTTagCompound();
        for (final Aspect aspect : PRIMALS) {
            final long value = get(owner, aspect);
            snap.setLong(aspect.getTag(), value);
            tag.setInteger(aspect.getTag(), face(value));
        }
        tag.setTag(SNAPSHOT_TAG, snap);
    }

    private static NBTTagCompound ensureTag(final ItemStack stack) {
        if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
        return stack.stackTagCompound;
    }

    // ---------------------------------------------------------------- sync

    public static void flush(final EntityPlayerMP player) {
        if (player == null || !DIRTY.remove(player.getUniqueID())) return;
        send(player);
    }

    public static void sendFull(final EntityPlayerMP player) {
        if (player == null) return;
        send(player);
    }

    private static void send(final EntityPlayerMP player) {
        ElinarCurios.networkWrapper
            .sendTo(new WandPoolSyncPacket.Message(values(player), player.getCommandSenderName()), player);
    }

    // ---------------------------------------------------------------- formatting

    /** Formats a raw vis value (x100) as the number the player sees, e.g. {@code 1,234.56}. */
    public static String format(final long raw) {
        if (raw <= 0L) return "0";
        final long whole = raw / 100L;
        final long fraction = raw % 100L;
        if (whole < 10_000_000L) {
            return fraction == 0L ? String.format("%,d", whole) : String.format("%,d.%02d", whole, fraction);
        }
        return String.format("%,d", whole);
    }
}
