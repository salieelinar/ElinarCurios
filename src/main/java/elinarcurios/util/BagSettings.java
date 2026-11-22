package elinarcurios.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import elinarcurios.item.ItemInfiniteBackpack;

/**
 * Per-backpack "extra configuration" values (movement + perception), stored in the bag NBT.
 *
 * <p>
 * The first {@link #PROTECTION_COUNT} settings are the always-on protections and are NOT exposed
 * here on purpose (GT hazards, Thaumcraft vis discount, bee immunity, space suit). Everything in
 * this class is user adjustable from the backpack GUI's "extra config" page.
 */
public final class BagSettings {

    public static final String TAG_SETTINGS = "settings";

    public static final int FLIGHT = 0;
    public static final int FLY_SPEED = 1;
    public static final int FLY_VERTICAL = 2;
    public static final int FLY_LOCK = 3;
    public static final int FLY_INERTIA = 4;
    public static final int WALK_SPEED = 5;
    public static final int JUMP = 6;
    public static final int HILL_STEP = 7;
    public static final int MINE_BOOST = 8;
    public static final int NATURALIST = 9;
    public static final int REVEAL = 10;
    public static final int NODES = 11;
    public static final int COUNT = 12;

    /** NBT keys, in setting id order. */
    public static final String[] KEYS = { "flight", "flySpeed", "flyVertical", "flyLock", "flyInertia", "walkSpeed",
        "jump", "hillStep", "mineBoost", "naturalist", "reveal", "nodes" };

    /** Translation keys of the labels, in setting id order. */
    public static final String[] LANG_KEYS = { "elinarcurios.config.flight", "elinarcurios.config.flySpeed",
        "elinarcurios.config.flyVertical", "elinarcurios.config.flyLock", "elinarcurios.config.flyInertia",
        "elinarcurios.config.walkSpeed", "elinarcurios.config.jump", "elinarcurios.config.hillStep",
        "elinarcurios.config.mineBoost", "elinarcurios.config.naturalist", "elinarcurios.config.reveal",
        "elinarcurios.config.nodes" };

    private static final boolean[] BOOLEAN = { true, false, false, true, true, false, false, true, true, true, true,
        true };
    private static final float[] DEFAULTS = { 1F, 0F, 0F, 0F, 0F, 0F, 0F, 1F, 1F, 1F, 1F, 1F };
    private static final float[] MAXIMUMS = { 1F, 6F, 8F, 1F, 1F, 8F, 15F, 1F, 1F, 1F, 1F, 1F };

    private BagSettings() {}

    public static boolean isBoolean(final int id) {
        return id >= 0 && id < COUNT && BOOLEAN[id];
    }

    public static float minValue(final int id) {
        return 0F;
    }

    public static float maxValue(final int id) {
        return id >= 0 && id < COUNT ? MAXIMUMS[id] : 0F;
    }

    public static float step(final int id) {
        return isBoolean(id) ? 1F : 0.1F;
    }

    private static NBTTagCompound peekTag(final ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        final NBTTagCompound root = stack.getTagCompound()
            .getCompoundTag(ItemInfiniteBackpack.TAG_ROOT);
        if (!root.hasKey(TAG_SETTINGS, NBT.TAG_COMPOUND)) return null;
        return root.getCompoundTag(TAG_SETTINGS);
    }

    private static NBTTagCompound getTag(final ItemStack stack) {
        final NBTTagCompound root = ItemInfiniteBackpack.getRootTag(stack);
        if (!root.hasKey(TAG_SETTINGS, NBT.TAG_COMPOUND)) root.setTag(TAG_SETTINGS, new NBTTagCompound());
        return root.getCompoundTag(TAG_SETTINGS);
    }

    public static float getFloat(final ItemStack stack, final int id) {
        if (id < 0 || id >= COUNT) return 0F;
        final NBTTagCompound tag = peekTag(stack);
        if (tag == null || !tag.hasKey(KEYS[id])) return DEFAULTS[id];
        return tag.getFloat(KEYS[id]);
    }

    public static boolean getBoolean(final ItemStack stack, final int id) {
        return getFloat(stack, id) != 0F;
    }

    public static float clamp(final int id, final float value) {
        if (isBoolean(id)) return value == 0F ? 0F : 1F;
        final float max = maxValue(id);
        if (value < 0F) return 0F;
        return value > max ? max : value;
    }

    /** Writes a value (clamped to its range). Returns the value actually stored. */
    public static float set(final ItemStack stack, final int id, float value) {
        if (stack == null || id < 0 || id >= COUNT) return 0F;
        value = clamp(id, value);
        if (isBoolean(id)) getTag(stack).setBoolean(KEYS[id], value != 0F);
        else getTag(stack).setFloat(KEYS[id], value);
        return value;
    }

    public static float[] readAll(final ItemStack stack) {
        final float[] values = new float[COUNT];
        for (int i = 0; i < COUNT; i++) values[i] = getFloat(stack, i);
        return values;
    }

    public static void writeAll(final ItemStack stack, final float[] values) {
        if (stack == null || values == null) return;
        for (int i = 0; i < COUNT && i < values.length; i++) set(stack, i, values[i]);
    }
}
