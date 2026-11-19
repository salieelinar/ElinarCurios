package elinarcurios.player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import elinarcurios.ElinarCurios;
import elinarcurios.network.WandPoolSyncPacket;
import elinarcurios.util.WandPoolConstants;
import thaumcraft.api.aspects.Aspect;

/**
 * Shared vis/aspect pool of the Celestial Wand (天玄之杖).
 *
 * <p>
 * Design (2026-11-19, player-approved):
 * <ul>
 * <li>authoritative storage: a world-level {@link WandPoolData} database keyed by player UUID
 * (like an external "infinite storage"), so the pool survives death/dimension changes and is also
 * readable when the owner is offline — machines keep consuming their vis;</li>
 * <li>supports <b>all</b> aspects, not only the six primals: the pool is a {@code tag -> long} map,
 * so later mechanics can store compound/custom aspects; {@link #PRIMALS} is only used for the vanilla
 * wand HUD/tooltip ordering;</li>
 * <li>the wand itself only stores its owner UUID/name plus a display snapshot, so every wand of one
 * player reads the same pool;</li>
 * <li>binding is permanent on first use: the wand may sit inside a machine (arcane workbench,
 * enchanter) where there is no player context.</li>
 * </ul>
 *
 * <p>
 * Only loaded when Thaumcraft is present (all call sites are TC-only classes).
 */
public final class WandPool {

    /** Fixed order for the vanilla wand HUD and the compact tooltip line. */
    public static final Aspect[] PRIMALS = { Aspect.AIR, Aspect.EARTH, Aspect.FIRE, Aspect.WATER, Aspect.ORDER,
        Aspect.ENTROPY };

    /** Wand root NBT: owner UUID string. */
    public static final String OWNER_TAG = "elinarOwner";
    /** Wand root NBT: owner display name (used by the client to match "is this wand mine"). */
    public static final String OWNER_NAME_TAG = "elinarOwnerName";
    /** Wand root NBT: display snapshot compound (aspect tag -> long) for foreign wands / direct NBT readers. */
    public static final String SNAPSHOT_TAG = "elinarPool";

    private static final Set<UUID> DIRTY = new HashSet<UUID>();

    private WandPool() {}

    // ---------------------------------------------------------------- aspects

    public static long clamp(final long value) {
        if (value <= 0L) return 0L;
        return value >= WandPoolConstants.CAPACITY ? WandPoolConstants.CAPACITY : value;
    }

    /** The int value vanilla Thaumcraft getters expose; its dial cannot render past Integer.MAX_VALUE. */
    public static int face(final long value) {
        return value >= (long) WandPoolConstants.INT_FACE ? WandPoolConstants.INT_FACE : (int) value;
    }

    // ---------------------------------------------------------------- world database

    /** Non-zero aspects stored for one player (empty when unknown). */
    public static Map<String, Long> poolFor(final UUID id) {
        final WandPoolData data = WandPoolStorage.data();
        return data == null ? new HashMap<String, Long>() : data.snapshot(id);
    }

    /** Server-side pool read for a wand, online or offline. Client must use its synced cache. */
    public static long getForOwner(final ItemStack stack, final Aspect aspect) {
        if (aspect == null) return 0L;
        final WandPoolData data = WandPoolStorage.data();
        final UUID id = ownerId(stack);
        return data == null || id == null ? 0L : data.get(id, aspect.getTag());
    }

    /** Server-side pool write for a wand, online or offline. */
    public static void setForOwner(final ItemStack stack, final Aspect aspect, final long value) {
        if (aspect == null) return;
        final UUID id = ownerId(stack);
        if (id == null) return;
        final WandPoolData data = WandPoolStorage.data();
        if (data == null) return;
        if (data.set(id, aspect.getTag(), value)) DIRTY.add(id);
    }

    /** Owner-UUID variant, used by the decomposition while the owner has no wand in the inventory. */
    public static long getForOwner(final UUID id, final Aspect aspect) {
        if (id == null || aspect == null) return 0L;
        final WandPoolData data = WandPoolStorage.data();
        return data == null ? 0L : data.get(id, aspect.getTag());
    }

    /** Owner-UUID variant of {@link #setForOwner(ItemStack, Aspect, long)}. */
    public static void setForOwner(final UUID id, final Aspect aspect, final long value) {
        if (id == null || aspect == null) return;
        final WandPoolData data = WandPoolStorage.data();
        if (data == null) return;
        if (data.set(id, aspect.getTag(), value)) DIRTY.add(id);
    }

    /** True when the wand has an owner whose pool can be reached — the database works offline. */
    public static boolean isOwnerAccessible(final ItemStack stack) {
        return ownerId(stack) != null;
    }

    /** All non-zero aspects of this wand's owner (server-side; client uses its cache/snapshot). */
    public static Map<String, Long> storedForOwner(final ItemStack stack) {
        final UUID id = ownerId(stack);
        return id == null ? new HashMap<String, Long>() : poolFor(id);
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

    /**
     * Permanent first-use binding. Migrates (a) the legacy per-wand int vis from pre-pool saves and
     * (b) the previous dev build's per-player pool into the world database, then stamps the owner.
     */
    public static void bind(final ItemStack stack, final EntityPlayer player) {
        if (stack == null || player == null || !(player instanceof EntityPlayerMP) || hasOwner(stack)) return;

        final EntityPlayerMP serverPlayer = (EntityPlayerMP) player;
        migrateFromPlayerData(serverPlayer);

        final UUID id = player.getUniqueID();
        final NBTTagCompound tag = ensureTag(stack);
        final WandPoolData data = WandPoolStorage.data();
        if (data != null) {
            for (final Aspect aspect : PRIMALS) {
                if (!tag.hasKey(aspect.getTag(), 3)) continue;
                final int legacy = tag.getInteger(aspect.getTag());
                if (legacy > 0) data.set(id, aspect.getTag(), data.get(id, aspect.getTag()) + legacy);
                tag.removeTag(aspect.getTag());
            }
        }

        tag.setString(OWNER_TAG, id.toString());
        tag.setString(OWNER_NAME_TAG, player.getCommandSenderName());
        writeSnapshot(stack, player);
        DIRTY.add(id);
    }

    /**
     * One-time migration of the previous dev build's pool (stored in the player's
     * {@code PlayerPersisted}) into the world database. Removes the old tag so it cannot be
     * re-imported after the pool was legitimately emptied.
     */
    public static void migrateFromPlayerData(final EntityPlayerMP player) {
        if (player == null) return;
        final NBTTagCompound data = player.getEntityData();
        final NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey(WandPoolConstants.PLAYER_POOL_TAG, 10)) return;

        final UUID id = player.getUniqueID();
        final WandPoolData store = WandPoolStorage.data();
        if (store != null && !store.has(id)) {
            final NBTTagCompound old = persisted.getCompoundTag(WandPoolConstants.PLAYER_POOL_TAG);
            for (final Aspect aspect : PRIMALS) {
                if (old.hasKey(aspect.getTag(), 4)) {
                    store.set(id, aspect.getTag(), old.getLong(aspect.getTag()));
                }
            }
        }
        persisted.removeTag(WandPoolConstants.PLAYER_POOL_TAG);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    // ---------------------------------------------------------------- display snapshot

    public static long snapshot(final ItemStack stack, final Aspect aspect) {
        if (stack == null || stack.stackTagCompound == null || aspect == null) return 0L;
        final NBTTagCompound tag = stack.stackTagCompound;
        if (!tag.hasKey(SNAPSHOT_TAG, 10)) return 0L;
        final NBTTagCompound snap = tag.getCompoundTag(SNAPSHOT_TAG);
        return snap.hasKey(aspect.getTag(), 4) ? clamp(snap.getLong(aspect.getTag())) : 0L;
    }

    /** Non-zero aspects from the wand's own display snapshot (client fallback for foreign wands). */
    @SuppressWarnings("unchecked")
    public static Map<String, Long> snapshotAll(final ItemStack stack) {
        final Map<String, Long> out = new HashMap<String, Long>();
        if (stack == null || stack.stackTagCompound == null) return out;
        final NBTTagCompound tag = stack.stackTagCompound;
        if (!tag.hasKey(SNAPSHOT_TAG, 10)) return out;
        final NBTTagCompound snap = tag.getCompoundTag(SNAPSHOT_TAG);
        for (final String key : (Set<String>) snap.func_150296_c()) {
            final long value = clamp(snap.getLong(key));
            if (value > 0L) out.put(key, value);
        }
        return out;
    }

    public static boolean snapshotMatches(final ItemStack stack, final EntityPlayer owner) {
        return owner != null && poolFor(owner.getUniqueID()).equals(snapshotAll(stack));
    }

    public static void writeSnapshot(final ItemStack stack, final EntityPlayer owner) {
        if (owner == null) return;
        writeSnapshotValues(stack, poolFor(owner.getUniqueID()));
    }

    /** Server-side snapshot refresh that also works while the owner is offline. */
    public static void writeSnapshotFor(final ItemStack stack) {
        if (stack == null) return;
        writeSnapshotValues(stack, storedForOwner(stack));
    }

    /**
     * Writes the exact long snapshot (aspect tag -> long, non-zero only) plus the legacy int aspect
     * tags for the six primals (addons that read wand NBT directly, e.g. ThaumicHorizons' familiars).
     */
    public static void writeSnapshotValues(final ItemStack stack, final Map<String, Long> values) {
        if (stack == null) return;
        final NBTTagCompound tag = ensureTag(stack);
        final NBTTagCompound snap = new NBTTagCompound();
        if (values != null) {
            for (final Map.Entry<String, Long> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                final long value = clamp(entry.getValue());
                if (value > 0L) snap.setLong(entry.getKey(), value);
            }
        }
        for (final Aspect aspect : PRIMALS) {
            final Long value = values == null ? null : values.get(aspect.getTag());
            tag.setInteger(aspect.getTag(), face(value == null ? 0L : value));
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
        // The full map is on its way; a later flush would just send the same thing again.
        DIRTY.remove(player.getUniqueID());
        send(player);
    }

    private static void send(final EntityPlayerMP player) {
        ElinarCurios.networkWrapper.sendTo(
            new WandPoolSyncPacket.Message(poolFor(player.getUniqueID()), player.getCommandSenderName()),
            player);
    }

    // ---------------------------------------------------------------- formatting

    /** Formats a raw vis value (x100) as a whole display number, e.g. {@code 123456} (no decimals). */
    public static String format(final long raw) {
        if (raw <= 0L) return "0";
        return Long.toString(raw / 100L);
    }
}
