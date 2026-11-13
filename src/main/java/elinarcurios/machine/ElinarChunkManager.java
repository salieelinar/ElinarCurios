package elinarcurios.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.OrderedLoadingCallback;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import elinarcurios.ElinarCurios;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 综合采掘工厂的 1×1 区块加载器（世界锚语义）。
 *
 * <p>
 * 为什么不用 GT 自带的 {@code GTChunkManager}：GT 在 2.9.0-RC-1 的默认配置
 * {@code alwaysReloadChunkloaders=false} 下，把机器票据当作“被动锚”——服务器重启时
 * {@code GTChunkManager.ticketsLoaded} 会丢弃机器票据，等机器再次激活时再申请。
 * 但机器自己的区块没被加载时它根本不会 tick，也就永远无法“再次激活”，形成鸡生蛋死锁
 * （只有玩家跑到旁边才会恢复）。Railcraft 的世界锚走的是另一条路：票据由 Forge 持久化，
 * 世界加载时回调里拿 {@code modData} 中的坐标反查方块，确认还在就重新 force 并保留票据。
 *
 * <p>
 * 这里复刻 Railcraft 的做法：票据写 {@code OwnerX/Y/Z}，世界加载时先确认该坐标仍是本机器，
 * 再在简单回调里 force 区块。机器存在期间票据一直保留（即使因断电/软锤停机），
 * 只有控制器被拆掉（{@link MTEComprehensiveMiningPlant#onBlockDestroyed()}）才释放。
 * 这样服务器重启后机器区块会在世界加载阶段被拉起，机器能自己恢复运行。
 */
public final class ElinarChunkManager implements OrderedLoadingCallback {

    public static final ElinarChunkManager INSTANCE = new ElinarChunkManager();

    private static final Logger LOGGER = LogManager.getLogger("ElinarCurios");

    /** world -> (机器方块坐标打包 -> 票据)。 */
    private static final Map<World, Map<Long, Ticket>> TICKETS = new HashMap<>();

    private ElinarChunkManager() {}

    /** 在 preInit 调用：注册 Forge 回调，并让本类接收世界卸载事件。 */
    public static void init() {
        ForgeChunkManager.setForcedChunkLoadingCallback(ElinarCurios.instance, INSTANCE);
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    /** 26 位 x、12 位 y、26 位 z；方块坐标范围远超 MC 实际世界。 */
    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    /** 请求/复用本机器所在区块的票据。返回 false 表示票据配额已满或世界无效。 */
    public static boolean requestChunk(TileEntity owner) {
        if (owner == null) return false;
        World world = owner.getWorldObj();
        if (world == null || world.isRemote) return false;

        Map<Long, Ticket> tickets = TICKETS.computeIfAbsent(world, ignored -> new HashMap<>());
        long key = key(owner.xCoord, owner.yCoord, owner.zCoord);
        Ticket ticket = tickets.get(key);
        if (ticket == null) {
            try {
                ticket = ForgeChunkManager.requestTicket(ElinarCurios.instance, world, ForgeChunkManager.Type.NORMAL);
            } catch (RuntimeException e) {
                // 例如 Forge 回调未注册成功：不要让机器 ticking 崩掉，必要时退回普通（无区块加载）运行。
                LOGGER.error("ElinarChunkManager: requestTicket failed", e);
                return false;
            }
            if (ticket == null) return false;
            NBTTagCompound data = ticket.getModData();
            data.setInteger("OwnerX", owner.xCoord);
            data.setInteger("OwnerY", owner.yCoord);
            data.setInteger("OwnerZ", owner.zCoord);
            tickets.put(key, ticket);
            LOGGER.info(
                "ElinarChunkManager: requested chunk ticket at {} {} {} (dim {})",
                owner.xCoord,
                owner.yCoord,
                owner.zCoord,
                world.provider.dimensionId);
        }
        ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(owner.xCoord >> 4, owner.zCoord >> 4));
        return true;
    }

    /** 释放本机器所在区块的票据（只在控制器被移除时调用）。 */
    public static void releaseChunk(TileEntity owner) {
        if (owner == null) return;
        World world = owner.getWorldObj();
        if (world == null || world.isRemote) return;
        Map<Long, Ticket> tickets = TICKETS.get(world);
        if (tickets == null) return;
        Ticket ticket = tickets.remove(key(owner.xCoord, owner.yCoord, owner.zCoord));
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
            LOGGER.info(
                "ElinarChunkManager: released chunk ticket at {} {} {} (dim {})",
                owner.xCoord,
                owner.yCoord,
                owner.zCoord,
                world.provider.dimensionId);
        }
    }

    /**
     * 世界加载阶段：Forge 把磁盘里的票据递过来，这里只保留“坐标上仍是本机器”的票据。
     * 不能在此处 forceChunk（Forge 规定），force 放在下面的简单回调里做。
     */
    @Override
    public List<Ticket> ticketsLoaded(List<Ticket> tickets, World world, int maxTicketCount) {
        List<Ticket> accepted = new ArrayList<>();
        Map<Long, Ticket> registered = TICKETS.computeIfAbsent(world, ignored -> new HashMap<>());
        for (Ticket ticket : tickets) {
            NBTTagCompound data = ticket.getModData();
            if (!data.hasKey("OwnerX") || !data.hasKey("OwnerZ")) continue;
            int x = data.getInteger("OwnerX");
            int y = data.getInteger("OwnerY");
            int z = data.getInteger("OwnerZ");
            if (y < 0) continue;
            long key = key(x, y, z);
            // 同一台机器被旧版本重复申请过票据时只保留一张
            if (registered.containsKey(key)) continue;
            TileEntity tile = world.getTileEntity(x, y, z);
            if (!isMiningPlant(tile)) continue;
            registered.put(key, ticket);
            accepted.add(ticket);
            LOGGER.info(
                "ElinarChunkManager: restoring chunk ticket at {} {} {} (dim {})",
                x,
                y,
                z,
                world.provider.dimensionId);
        }
        return accepted;
    }

    /** 简单回调：Forge 已把票据放回世界票据表，此时可以 force 区块。 */
    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        for (Ticket ticket : tickets) {
            NBTTagCompound data = ticket.getModData();
            int x = data.getInteger("OwnerX");
            int z = data.getInteger("OwnerZ");
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(x >> 4, z >> 4));
        }
    }

    private static boolean isMiningPlant(TileEntity tile) {
        if (!(tile instanceof IGregTechTileEntity)) return false;
        IMetaTileEntity mte = ((IGregTechTileEntity) tile).getMetaTileEntity();
        return mte instanceof MTEComprehensiveMiningPlant;
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        TICKETS.remove(event.world);
    }
}
