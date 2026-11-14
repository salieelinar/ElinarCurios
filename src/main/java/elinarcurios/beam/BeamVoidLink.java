package elinarcurios.beam;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import elinarcurios.machine.MTEBeamVoidStorage;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 闪存（GT 数据棒，{@code gt.item.tool.data_stick}）绑定协议 + 存储器查找。
 *
 * <p>
 * 协议（芷若 2026-11-14 拍板）：闪存<b>左键</b>束流虚空存储器 → 把 {维度,x,y,z} 写进闪存的 NBT；
 * 然后<b>右键</b>输入仓 / 输出仓 → 仓把坐标抄进自己的 NBT，之后每秒去找那个存储器。
 * 一个存储器可以被非常多的仓绑定，仓只记"我绑的是哪个存储"。
 *
 * <p>
 * <b>跨维度</b>：坐标里带维度 id，查找时用 {@code MinecraftServer.worldServerForDimension}；
 * 目标区块没加载就返回 null（<b>不</b>为了取库存把远处区块拉起来，与样板总成侦测器同一套做法）。
 */
public final class BeamVoidLink {

    /** 闪存里存绑定坐标的根键名（独立命名空间，避免和 GT 自己的 type/x/y/z 冲突）。 */
    private static final String KEY = "elinarBeamVoidStorage";

    private static final String K_DIM = "dim";
    private static final String K_X = "x";
    private static final String K_Y = "y";
    private static final String K_Z = "z";
    private static final String K_UUID = "uuid";

    private BeamVoidLink() {}

    public static boolean isDataStick(ItemStack stack) {
        return stack != null && ItemList.Tool_DataStick.isStackEqual(stack, false, true);
    }

    /**
     * 把存储器的 UUID 写进闪存（新协议，2026-11-14 芷若拍板：绑 UUID 而不是坐标，
     * 这样把存储器拆走换个地方放，仓会自动重新连上）。同时清掉旧的坐标键，避免两套协议打架。
     */
    public static boolean writeUuid(ItemStack stick, UUID storageId) {
        if (!isDataStick(stick) || storageId == null) return false;
        NBTTagCompound tag = stick.stackTagCompound;
        if (tag == null) {
            tag = new NBTTagCompound();
            stick.stackTagCompound = tag;
        }
        final NBTTagCompound link = new NBTTagCompound();
        link.setString(K_UUID, storageId.toString());
        tag.setTag(KEY, link);
        return true;
    }

    /** 读闪存里的存储器 UUID；没有则 null（旧版闪存只有坐标）。 */
    public static UUID readUuid(ItemStack stick) {
        if (!isDataStick(stick)) return null;
        final NBTTagCompound tag = stick.stackTagCompound;
        if (tag == null || !tag.hasKey(KEY)) return null;
        final String raw = tag.getCompoundTag(KEY)
            .getString(K_UUID);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** 把存储器坐标写进闪存；返回 false 表示手里不是闪存。 */
    public static boolean write(ItemStack stick, int dim, int x, int y, int z) {
        if (!isDataStick(stick)) return false;
        NBTTagCompound tag = stick.stackTagCompound;
        if (tag == null) {
            tag = new NBTTagCompound();
            stick.stackTagCompound = tag;
        }
        NBTTagCompound link = new NBTTagCompound();
        link.setInteger(K_DIM, dim);
        link.setInteger(K_X, x);
        link.setInteger(K_Y, y);
        link.setInteger(K_Z, z);
        tag.setTag(KEY, link);
        return true;
    }

    /** 读闪存里的绑定坐标；返回 {dim,x,y,z}，没有则 null。 */
    public static int[] read(ItemStack stick) {
        if (!isDataStick(stick)) return null;
        NBTTagCompound tag = stick.stackTagCompound;
        if (tag == null || !tag.hasKey(KEY)) return null;
        NBTTagCompound link = tag.getCompoundTag(KEY);
        return new int[] { link.getInteger(K_DIM), link.getInteger(K_X), link.getInteger(K_Y), link.getInteger(K_Z) };
    }

    public static void clear(ItemStack stick) {
        if (!isDataStick(stick)) return;
        NBTTagCompound tag = stick.stackTagCompound;
        if (tag != null) tag.removeTag(KEY);
    }

    /**
     * 按坐标找存储器。区块未加载 / 方块已被拆 / 换成别的东西 → null。
     *
     * @param context 调用方所在世界（同维度时直接用它，省一次 server 查找）
     */
    public static MTEBeamVoidStorage find(World context, int dim, int x, int y, int z) {
        if (context == null) return null;
        World world = context;
        if (dim != context.provider.dimensionId) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null) return null;
            world = server.worldServerForDimension(dim);
        }
        if (world == null) return null;
        if (!world.blockExists(x, y, z)) return null;
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity)) return null;
        IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
        return mte instanceof MTEBeamVoidStorage ? (MTEBeamVoidStorage) mte : null;
    }

    /** 三维坐标压成一个 long（x/z 各 26 位、y 12 位），用于存储器里记"绑了哪些仓"。 */
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (long) (z & 0x3FFFFFF);
    }

    public static int unpackX(long packed) {
        return (int) (packed >> 38);
    }

    public static int unpackY(long packed) {
        return (int) ((packed >> 26) & 0xFFF);
    }

    public static int unpackZ(long packed) {
        return (int) (packed & 0x3FFFFFF);
    }
}
