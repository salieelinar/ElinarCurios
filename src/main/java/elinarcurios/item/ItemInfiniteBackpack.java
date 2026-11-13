package elinarcurios.item;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.common.Reference;
import elinarcurios.network.OpenBagPacket;
import elinarcurios.util.FabulousText;

/**
 * The Infinite Backpack item.
 *
 * <p>
 * Storage core derived from Avaritiaddons' Infinity Chest (MPL-2.0, WanionCane):
 * 243 slots, each capable of holding up to {@link Integer#MAX_VALUE} items.
 */
public class ItemInfiniteBackpack extends Item {

    public static ItemInfiniteBackpack instance;

    public static final String TAG_ROOT = "elinarcurios";
    public static final String TAG_INVENTORY = "inventory";
    public static final String TAG_SLOT = "Slot";
    public static final int STORAGE_SLOTS = 243;

    // Multi-page storage: independent pages, each with STORAGE_SLOTS slots.
    public static final int PAGE_COUNT = 10;
    public static final String TAG_PAGES = "pages";
    public static final String TAG_CURRENT_PAGE = "currentPage";

    // Passive buff, toggleable from the backpack GUI
    public static final String TAG_BUFFS = "buffs";
    public static final String BUFF_NIGHT_VISION = "nightVision";
    public static final String[] BUFF_KEYS = { BUFF_NIGHT_VISION };

    public ItemInfiniteBackpack() {
        setUnlocalizedName("infiniteBackpack");
        setTextureName(Reference.MOD_ID + ":infiniteBackpack");
        setCreativeTab(ElinarCurios.creativeTab);
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack stack, final World world, final EntityPlayer player) {
        if (world.isRemote) {
            ElinarCurios.networkWrapper.sendToServer(new OpenBagPacket.Message(OpenBagPacket.FROM_HOLDING));
        }
        return stack;
    }

    public static boolean isInfiniteBackpack(final ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemInfiniteBackpack;
    }

    public static NBTTagCompound getRootTag(final ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        if (!stack.getTagCompound()
            .hasKey(TAG_ROOT, NBT.TAG_COMPOUND)) {
            stack.getTagCompound()
                .setTag(TAG_ROOT, new NBTTagCompound());
        }
        return stack.getTagCompound()
            .getCompoundTag(TAG_ROOT);
    }

    /** Remembered page (0-based, clamped to the valid range). */
    public static int getCurrentPage(final ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return 0;
        final NBTTagCompound root = stack.getTagCompound()
            .getCompoundTag(TAG_ROOT);
        final int page = root.getInteger(TAG_CURRENT_PAGE);
        return page < 0 ? 0 : page >= PAGE_COUNT ? PAGE_COUNT - 1 : page;
    }

    public static void setCurrentPage(final ItemStack stack, final int page) {
        if (stack == null) return;
        getRootTag(stack).setInteger(TAG_CURRENT_PAGE, page < 0 ? 0 : page >= PAGE_COUNT ? PAGE_COUNT - 1 : page);
    }

    /** Count of occupied storage slots across all pages, for tooltip display. */
    public static int getTotalUsedSlots(final ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return 0;
        final NBTTagCompound root = stack.getTagCompound()
            .getCompoundTag(TAG_ROOT);
        if (root.hasKey(TAG_PAGES, NBT.TAG_COMPOUND)) {
            final NBTTagCompound pages = root.getCompoundTag(TAG_PAGES);
            int total = 0;
            for (int i = 0; i < PAGE_COUNT; i++) {
                final NBTTagCompound pageTag = pages.getCompoundTag(Integer.toString(i));
                if (pageTag.hasKey(TAG_INVENTORY, NBT.TAG_LIST)) {
                    total += pageTag.getTagList(TAG_INVENTORY, NBT.TAG_COMPOUND)
                        .tagCount();
                }
            }
            return total;
        }
        // legacy single-page format
        return getUsedSlots(stack);
    }

    /** Count of occupied storage slots of the current page, for tooltip display. */
    public static int getUsedSlots(final ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        final NBTTagCompound root = stack.getTagCompound()
            .getCompoundTag(TAG_ROOT);
        if (root.hasKey(TAG_PAGES, NBT.TAG_COMPOUND)) {
            final NBTTagCompound pageTag = root.getCompoundTag(TAG_PAGES)
                .getCompoundTag(Integer.toString(getCurrentPage(stack)));
            if (pageTag.hasKey(TAG_INVENTORY, NBT.TAG_LIST)) {
                return pageTag.getTagList(TAG_INVENTORY, NBT.TAG_COMPOUND)
                    .tagCount();
            }
            return 0;
        }
        // legacy single-page format
        if (!root.hasKey(TAG_INVENTORY, NBT.TAG_LIST)) return 0;
        return root.getTagList(TAG_INVENTORY, NBT.TAG_COMPOUND)
            .tagCount();
    }

    /** All buffs are enabled by default. */
    public static boolean isBuffEnabled(final ItemStack stack, final String buffKey) {
        if (stack == null || !stack.hasTagCompound()) return true;
        final NBTTagCompound root = stack.getTagCompound()
            .getCompoundTag(TAG_ROOT);
        if (!root.hasKey(TAG_BUFFS, NBT.TAG_COMPOUND)) return true;
        return root.getCompoundTag(TAG_BUFFS)
            .getBoolean(buffKey);
    }

    /** Returns the new state. */
    public static boolean toggleBuff(final ItemStack stack, final String buffKey) {
        final boolean now = !isBuffEnabled(stack, buffKey);
        setBuffEnabled(stack, buffKey, now);
        return now;
    }

    public static void setBuffEnabled(final ItemStack stack, final String buffKey, final boolean enabled) {
        if (stack == null) return;
        final NBTTagCompound root = getRootTag(stack);
        if (!root.hasKey(TAG_BUFFS, NBT.TAG_COMPOUND)) root.setTag(TAG_BUFFS, new NBTTagCompound());
        root.getCompoundTag(TAG_BUFFS)
            .setBoolean(buffKey, enabled);
    }

    /** All four buff flags as booleans, in BUFF_KEYS order. */
    public static boolean[] getBuffFlags(final ItemStack stack) {
        final boolean[] flags = new boolean[BUFF_KEYS.length];
        for (int i = 0; i < BUFF_KEYS.length; i++) flags[i] = isBuffEnabled(stack, BUFF_KEYS[i]);
        return flags;
    }

    public static void setBuffFlags(final ItemStack stack, final boolean[] flags) {
        if (stack == null || flags == null) return;
        for (int i = 0; i < Math.min(flags.length, BUFF_KEYS.length); i++) {
            setBuffEnabled(stack, BUFF_KEYS[i], flags[i]);
        }
    }

    /**
     * Rainbow-animated display name (Avaritia's "fabulous" effect): shimmering colours in
     * NEI zoom, item tooltips and everywhere the name is rendered. NEI search strips the
     * colour codes, so searching still works normally.
     */
    @Override
    public String getItemStackDisplayName(final ItemStack stack) {
        return FabulousText.makeFabulous(super.getItemStackDisplayName(stack));
    }

    @Override
    @SuppressWarnings("unchecked")
    @SideOnly(Side.CLIENT)
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List tooltips,
        final boolean advanced) {
        tooltips.add(
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.ITALIC.toString()
                + I18n.format("item.elinarcurios.tooltip.motto"));
        tooltips.add(
            I18n.format(
                "item.elinarcurios.tooltip.slots",
                Integer.toString(getTotalUsedSlots(stack)),
                Integer.toString(STORAGE_SLOTS * PAGE_COUNT)));
        if (player.isSneaking()) {
            tooltips.add(
                I18n.format(
                    "item.elinarcurios.tooltip.pages",
                    Integer.toString(PAGE_COUNT),
                    Integer.toString(STORAGE_SLOTS)));
            tooltips.add(I18n.format("item.elinarcurios.tooltip.max"));
            tooltips.add(I18n.format("item.elinarcurios.tooltip.invulnerable"));
            tooltips.add(I18n.format("item.elinarcurios.tooltip.equip"));
        } else {
            tooltips.add(I18n.format("item.elinarcurios.tooltip.hint"));
        }
    }
}
