package elinarcurios.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import elinarcurios.item.ItemInfiniteBackpack;

/**
 * Per-backpack "extra configuration" values (movement + perception + passive buffs), stored in the
 * bag NBT.
 *
 * <p>
 * Settings 0-11 are the original movement/perception page (page 1); 12-21 are the health + potion
 * page 2 (2 custom health values, 7 potion buffs and the miner light); 22-24 are the on/off page 3.
 * New settings default to 0 (off), so existing bags keep their old behaviour.
 */
public final class BagSettings {

    public static final String TAG_SETTINGS = "settings";

    // Page 1: movement / perception.
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

    // Page 2: custom health + leveled buffs (0 = off, 1 = level I) + miner light.
    public static final int HEALTH_BONUS = 12;
    public static final int HEALTH_REGEN = 13;
    public static final int BUFF_RESISTANCE = 14;
    public static final int BUFF_ABSORPTION = 15;
    public static final int BUFF_HASTE = 16;
    public static final int BUFF_STRENGTH = 17;
    public static final int BUFF_JUMP = 18;
    public static final int BUFF_REFLECT = 19;
    public static final int BUFF_VIS_REGEN = 20;
    public static final int MINE_LIGHT = 21;

    // Page 3: on/off buffs (no levels).
    public static final int BUFF_WELL_FED = 22;
    public static final int BUFF_VAMPIRIC_AURA = 23;
    public static final int BUFF_SOUL_CROSS = 24;

    public static final int COUNT = 25;

    /** NBT keys, in setting id order. */
    public static final String[] KEYS = { "flight", "flySpeed", "flyVertical", "flyLock", "flyInertia", "walkSpeed",
        "jump", "hillStep", "mineBoost", "naturalist", "reveal", "nodes", "healthBonus", "healthRegen",
        "buffResistance", "buffAbsorption", "buffHaste", "buffStrength", "buffJump", "buffReflect", "buffVisRegen",
        "buffMineLight", "buffWellFed", "buffVampiricAura", "buffSoulCross" };

    /** Translation keys of the labels, in setting id order. */
    public static final String[] LANG_KEYS = { "elinarcurios.config.flight", "elinarcurios.config.flySpeed",
        "elinarcurios.config.flyVertical", "elinarcurios.config.flyLock", "elinarcurios.config.flyInertia",
        "elinarcurios.config.walkSpeed", "elinarcurios.config.jump", "elinarcurios.config.hillStep",
        "elinarcurios.config.mineBoost", "elinarcurios.config.naturalist", "elinarcurios.config.reveal",
        "elinarcurios.config.nodes", "elinarcurios.config.healthBonus", "elinarcurios.config.healthRegen",
        "elinarcurios.config.buffResistance", "elinarcurios.config.buffAbsorption", "elinarcurios.config.buffHaste",
        "elinarcurios.config.buffStrength", "elinarcurios.config.buffJump", "elinarcurios.config.buffReflect",
        "elinarcurios.config.buffVisRegen", "elinarcurios.config.buffMineLight", "elinarcurios.config.buffWellFed",
        "elinarcurios.config.buffVampiricAura", "elinarcurios.config.buffSoulCross" };

    private static final boolean[] BOOLEAN = { true, false, false, true, true, false, false, true, true, true, true,
        true, false, false, false, false, false, false, false, false, false, false, true, true, true };
    private static final float[] DEFAULTS = { 1F, 0F, 0F, 0F, 0F, 0F, 0F, 1F, 1F, 1F, 1F, 1F, 0F, 0F, 0F, 0F, 0F, 0F,
        0F, 0F, 0F, 0F, 0F, 0F, 0F };
    private static final float[] MAXIMUMS = { 1F, 6F, 8F, 1F, 1F, 8F, 15F, 1F, 1F, 1F, 1F, 1F, 65535F, 64F, 5F, 10F,
        10F, 10F, 10F, 10F, 8F, 15F, 1F, 1F, 1F };

    private BagSettings() {}

    public static boolean isBoolean(final int id) {
        return id >= 0 && id < COUNT && BOOLEAN[id];
    }

    /** GUI page of a setting: 1 = movement, 2 = health + leveled buffs + light, 3 = on/off buffs. */
    public static int pageOf(final int id) {
        if (id >= 0 && id <= NODES) return 1;
        if (id >= HEALTH_BONUS && id <= MINE_LIGHT) return 2;
        if (id >= BUFF_WELL_FED && id <= BUFF_SOUL_CROSS) return 3;
        return 0;
    }

    /** Row index of a setting inside its page. */
    public static int rowOf(final int id) {
        final int page = pageOf(id);
        if (page == 1) return id;
        if (page == 2) return id - HEALTH_BONUS;
        if (page == 3) return id - BUFF_WELL_FED;
        return 0;
    }

    /** True for page 2/3 settings that are implemented as a potion effect. */
    public static boolean isBuffSetting(final int id) {
        return id >= BUFF_RESISTANCE && id < COUNT && id != MINE_LIGHT;
    }

    public static float minValue(final int id) {
        return 0F;
    }

    public static float maxValue(final int id) {
        return id >= 0 && id < COUNT ? MAXIMUMS[id] : 0F;
    }

    public static float step(final int id) {
        if (isBoolean(id)) return 1F;
        return pageOf(id) == 1 ? 0.1F : 1F;
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
        // Booleans are stored as NBT booleans (byte tags); reading them with getFloat would
        // throw a ClassCastException, so they take a separate path.
        if (isBoolean(id)) return tag.getBoolean(KEYS[id]) ? 1F : 0F;
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
