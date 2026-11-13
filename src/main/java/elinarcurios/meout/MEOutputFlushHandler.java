package elinarcurios.meout;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.PlayerSource;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import elinarcurios.Config;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputME;
import gregtech.common.tileentities.machines.outputme.base.MTEHatchOutputMEBase;

/**
 * 「玩家下单期间的 ME 输出当 tick 刷盘」。
 *
 * <p>
 * GT5U 的 {@code Output Bus (ME)} / {@code Output Hatch (ME)} 共用 {@code MTEHatchOutputMEBase}，它每
 * {@code refreshTime}（默认 40 tick = 2 秒）才把缓存整包 {@code injectItems} 进 AE；2 秒是写死的下限
 * （{@code setRefreshTime} 里 {@code Math.max(40, x)}），游戏内既没有配置也没有命令能改。
 *
 * <p>
 * 本处理器不改那个字段，而是<b>在玩家下单期间代替那个定时器</b>：每 tick 找一遍"正在干活"的多方块
 * （{@code getTotalRuntimeInTicks() - getLastWorkingTick() <= holdTicks}），把它的 ME 输出仓/总线里
 * 有货的缓存直接 {@code flushCachedStack()} 掉。机器停手满 {@code holdTicks} 后不再插手，GT 自己的
 * 2 秒定时器照旧兜底，所以平时的 AE 事务量和原版完全一样。
 *
 * <p>
 * 「玩家下单」的判据来自 AE2 自己记录的下单来源：{@code ICraftingCPU#getSourcePlayer()} 只有
 * {@code PlayerSource} 提交的任务才有值（{@code CraftingCPUCluster#submitJob} 里写入）。
 * <ul>
 * <li>玩家在终端 / ECO 合成确认框 / GTNL 量子计算机界面下单 → {@code PlayerSource}，有值 → 快刷；</li>
 * <li>AE2FC 的库存维持器（{@code TileLevelMaintainer}）走 {@code MachineSource}，没值 → 不快刷。</li>
 * </ul>
 * 另外 {@code ECO 的 NativeECOCpu} 与 {@code GTNL 的 ECraftingCPUCluster} 都继承 AE2 的
 * {@code CraftingCPUCluster} 并走同一个 {@code submitJob}，所以它们下的玩家单同样能命中。
 */
public final class MEOutputFlushHandler {

    /** 多方块列表的刷新间隔：机器是静态的，没必要每 tick 遍历整张 TE 表。 */
    private static final int MACHINE_CACHE_REFRESH_TICKS = 20;

    /** 每张 AE 网本 tick 的"有没有玩家单"缓存（按 IGrid 身份区分；每 tick 清一次）。 */
    private final Map<IGrid, Boolean> gridCache = new IdentityHashMap<>();

    /** 已加载的多方块：按世界缓存，每 {@link #MACHINE_CACHE_REFRESH_TICKS} tick 重建一次。 */
    private final Map<World, List<MTEMultiBlockBase>> machinesByWorld = new IdentityHashMap<>();

    /** 上面那份缓存的构建时刻（{@code world.getTotalWorldTime()}）。 */
    private final Map<World, Long> machinesRefreshTick = new IdentityHashMap<>();

    /** 本 tick 命中的"有玩家单"网格数（给 /Elinar meflush 看）。 */
    private int tickPlayerJobGrids;

    /** 本 tick 正在干活的多方块数。 */
    private int tickWorkingMachines;

    /** 本 tick 真正刷了货的仓数。 */
    private int tickFlushedHatches;

    /** 上次统计值（命令读取）。 */
    public static volatile int lastTickPlayerJobGrids;
    public static volatile int lastTickWorkingMachines;
    public static volatile int lastTickFlushedHatches;
    public static volatile long totalFlushedHatches;

    @SubscribeEvent
    public void onWorldTick(final TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        final World world = event.world;
        if (world == null || world.isRemote) return;
        if (!Config.meOutFastFlushEnabled) return;

        this.gridCache.clear();
        this.tickPlayerJobGrids = 0;
        this.tickWorkingMachines = 0;
        this.tickFlushedHatches = 0;

        final List<MTEMultiBlockBase> machines = this.machinesOf(world);
        for (int i = 0; i < machines.size(); i++) {
            final MTEMultiBlockBase machine = machines.get(i);
            try {
                final long idleTicks = machine.getTotalRuntimeInTicks() - machine.getLastWorkingTick();
                if (idleTicks > Config.meOutFastFlushHoldTicks) continue;
                this.tickWorkingMachines++;
                this.flushMachine(machine);
            } catch (final Throwable ignored) {
                // 机器被拆/结构正在重算时偶发异常：跳过它，别影响其它机器和这一 tick 的 tick 循环
            }
        }

        lastTickPlayerJobGrids = this.tickPlayerJobGrids;
        lastTickWorkingMachines = this.tickWorkingMachines;
        lastTickFlushedHatches = this.tickFlushedHatches;
        if (this.tickFlushedHatches > 0) totalFlushedHatches += this.tickFlushedHatches;
    }

    /** 世界卸载时丢掉缓存，免得跨存档/跨维度积累。 */
    @SubscribeEvent
    public void onWorldUnload(final WorldEvent.Unload event) {
        if (event.world == null) return;
        this.machinesByWorld.remove(event.world);
        this.machinesRefreshTick.remove(event.world);
    }

    /** 取这个世界里所有已加载的多方块（最多 {@link #MACHINE_CACHE_REFRESH_TICKS} tick 之前的快照）。 */
    private List<MTEMultiBlockBase> machinesOf(final World world) {
        final long now = world.getTotalWorldTime();
        final List<MTEMultiBlockBase> cached = this.machinesByWorld.get(world);
        final Long refreshedAt = this.machinesRefreshTick.get(world);
        if (cached != null && refreshedAt != null
            && now >= refreshedAt
            && now - refreshedAt < MACHINE_CACHE_REFRESH_TICKS) {
            return cached;
        }
        final List<MTEMultiBlockBase> machines = new ArrayList<>();
        final List<TileEntity> tiles = world.loadedTileEntityList;
        for (int i = 0; i < tiles.size(); i++) {
            final TileEntity tile = tiles.get(i);
            if (!(tile instanceof IGregTechTileEntity)) continue;
            final IMetaTileEntity mte = ((IGregTechTileEntity) tile).getMetaTileEntity();
            if (mte instanceof MTEMultiBlockBase) {
                machines.add((MTEMultiBlockBase) mte);
            }
        }
        this.machinesByWorld.put(world, machines);
        this.machinesRefreshTick.put(world, now);
        return machines;
    }

    /** 刷一台多方块的所有 ME 输出仓/总线。 */
    private void flushMachine(final MTEMultiBlockBase machine) {
        if (machine.mOutputBusses != null) {
            for (final MTEHatchOutputBus bus : machine.mOutputBusses) {
                if (bus instanceof MTEHatchOutputBusME) {
                    this.flushProvider(((MTEHatchOutputBusME) bus).getProvider());
                }
            }
        }
        if (machine.mOutputHatches != null) {
            for (final MTEHatchOutput hatch : machine.mOutputHatches) {
                if (hatch instanceof MTEHatchOutputME) {
                    this.flushProvider(((MTEHatchOutputME) hatch).getProvider());
                }
            }
        }
    }

    /** 刷一个 ME 输出仓/总线：空缓存、没网、这张网没有玩家单都直接跳过。 */
    private void flushProvider(final MTEHatchOutputMEBase<?> provider) {
        if (provider == null) return;
        // 空缓存连 flush 都不用调：GT 的 flushCachedStack() 在空缓存时会顺手改 lastOutputTick，
        // 白白把 GT 自己的 2 秒定时器往后推，反而拖慢停机后的最后一次刷盘。
        if (provider.getCachedAmount() <= 0) return;
        final AENetworkProxy proxy = provider.getProxy();
        if (proxy == null || !proxy.isActive()) return;
        final IGrid grid;
        try {
            grid = proxy.getGrid();
        } catch (final GridAccessException e) {
            return;
        }
        if (grid == null || !this.gridHasPlayerJob(grid)) return;
        try {
            provider.flushCachedStack();
        } catch (final Throwable ignored) {
            // 仓被拆/网络正在重算时偶发异常：这一 tick 不刷，下 tick 再来
            return;
        }
        this.tickFlushedHatches++;
    }

    /** 这张网里是否有"玩家下的"合成任务在跑（含 ECO 的 NativeECOCpu 与 GTNL 的量子 CPU）。 */
    private boolean gridHasPlayerJob(final IGrid grid) {
        final Boolean known = this.gridCache.get(grid);
        if (known != null) return known;
        boolean has = false;
        try {
            final ICraftingGrid crafting = grid.getCache(ICraftingGrid.class);
            if (crafting != null) {
                for (final ICraftingCPU cpu : crafting.getCpus()) {
                    if (cpu == null || !cpu.isBusy()) continue;
                    if (cpu.getSourcePlayer() != null) {
                        has = true;
                        break;
                    }
                    final BaseActionSource source = cpu.getCurrentJobSource();
                    if (source instanceof PlayerSource) {
                        has = true;
                        break;
                    }
                }
            }
        } catch (final Throwable ignored) {
            has = false;
        }
        this.gridCache.put(grid, has);
        if (has) this.tickPlayerJobGrids++;
        return has;
    }
}
