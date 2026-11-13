package elinarcurios.machine;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import bwcrossmod.galacticgreg.VoidMinerUtility;
import bwcrossmod.galacticgreg.VoidMinerUtility.DropMap;
import elinarcurios.Config;
import galacticgreg.api.ModDimensionDef;
import galacticgreg.api.enums.DimensionDef;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.WirelessNetworkManager;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

/**
 * 综合采掘工厂（1×1×1 多方块，0.2.0-alpha）。
 *
 * <p>
 * 控制器单方块即可成型，直接使用 GT 的 MTEMultiBlockBaseGui。
 */
public class MTEComprehensiveMiningPlant extends MTEEnhancedMultiBlockBase<MTEComprehensiveMiningPlant>
    implements IGridProxyable, IActionHost, ISurvivalConstructable {

    public static final int EU_PER_PARALLEL = 32768;
    public static final int ORE_PER_SECOND_PER_PARALLEL = 64;
    public static final int MAX_ORE_PARALLEL = 48000;
    public static final int MAX_FLUID_PARALLEL = 2000;

    /** 产出模式 mod1：每秒产一次、每秒直接倾倒进 AE（前期）。 */
    public static final int MODE_REALTIME = 1;
    /** 产出模式 mod2：每秒产进内部缓存，每 N 秒批量倾倒进 AE（中期，默认）。 */
    public static final int MODE_ACCUMULATE = 2;
    /** 产出模式 mod3：每 N 秒才结算一次产出并倾倒进 AE（后期）。 */
    public static final int MODE_SETTLE = 3;

    private static final int TICKS_PER_SECOND = 20;
    private static final int REFILL_INTERVAL = 1200;
    private static final int OIL_DIVIDER = 5000;
    private static final String STRUCTURE_PIECE_MAIN = "main";

    private static IStructureDefinition<MTEComprehensiveMiningPlant> STRUCTURE_DEFINITION = null;

    private int oreParallel = 1;
    private int fluidParallel = 1;
    private int configuredOre = 1;
    private int configuredFluid = 1;
    /** 产出模式：{@link #MODE_REALTIME} / {@link #MODE_ACCUMULATE} / {@link #MODE_SETTLE}。 */
    private int mode = MODE_ACCUMULATE;
    /**
     * 结算档（mod3）专用：自上次结算以来机器**真正在运行**的秒数（停机不计，避免关掉机器还能白拿产出）。
     * 攒满 {@link #getIntervalSeconds()} 秒才结算一次；切档/拆机时会把这段没结算的秒数补算掉。
     */
    private int settleSeconds;
    /** 距离上次把缓存倾倒进 AE 已经过去的秒数（{@code infMinerFlushSeconds} 的计时器）。 */
    private int flushCounter;
    private long energyBuffer;
    private long workTicks;
    private UUID owner;
    private transient boolean wasWorking;

    private final java.util.HashMap<GTUtility.ItemId, Long> itemCache = new java.util.HashMap<>();
    private final java.util.HashMap<Fluid, Long> fluidCache = new java.util.HashMap<>();
    private final java.util.HashMap<Fluid, Double> fluidRates = new java.util.HashMap<>();
    private transient final java.util.HashMap<Fluid, Double> fluidCredit = new java.util.HashMap<>();
    private transient final java.util.HashMap<GTUtility.ItemId, Long> lastOres = new java.util.HashMap<>();
    private transient GTUtility.ItemId[] oreTable;
    private transient double[] oreChance;
    private transient double[] oreCredit;

    private transient AENetworkProxy proxy;
    private transient BaseActionSource actionSource;
    private transient DropMap dropMap;
    private boolean chunkLoaded;

    public MTEComprehensiveMiningPlant(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        hasMaintenanceChecks = false;
    }

    public MTEComprehensiveMiningPlant(String aName) {
        super(aName);
        hasMaintenanceChecks = false;
    }

    @Override
    public boolean getDefaultHasMaintenanceChecks() {
        return false;
    }

    @Override
    public void checkMaintenance() {}

    @Override
    public boolean doRandomMaintenanceDamage() {
        return false;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEComprehensiveMiningPlant(mName);
    }

    @Override
    protected gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui<?> getGui() {
        return new elinarcurios.gui.InfMinerGui(this);
    }

    public int getConfiguredOre() {
        return configuredOre;
    }

    public void setConfiguredOre(int value) {
        configuredOre = Math.max(1, Math.min(MAX_ORE_PARALLEL, value));
        markMachineDirty();
    }

    public int getConfiguredFluid() {
        return configuredFluid;
    }

    public boolean isWorkEnabled() {
        return getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isAllowedToWork();
    }

    public long getPerTickEUt() {
        return (long) (oreParallel + fluidParallel) * EU_PER_PARALLEL;
    }

    public long getPerSecondEUt() {
        return (long) (oreParallel + fluidParallel) * EU_PER_PARALLEL * TICKS_PER_SECOND;
    }

    public long getEnergyPercent() {
        long cap = bufferCapacity();
        if (cap <= 0L) return 0L;
        return Math.max(0L, Math.min(100L, energyBuffer * 100L / cap));
    }

    public long getOrePerSecond() {
        return (long) ORE_PER_SECOND_PER_PARALLEL * oreParallel;
    }

    /** 当前维度既无虚空矿脉也无流体时返回提示，否则返回空串。 */
    public String getDimensionHint() {
        boolean noOre = dropMap == null || dropMap.getTotalWeight() <= 0F;
        boolean noFluid = fluidRates.isEmpty();
        return noOre && noFluid ? "该维度没有虚空矿脉/流体" : "";
    }

    public int getFluidTypeCount() {
        return fluidRates.size();
    }

    /** 黑框里的流体行（最多 10 行，超出在第 10 行标注 +N）。 */
    public java.util.List<String> getFluidRateLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        int i = 0;
        for (Map.Entry<Fluid, Double> entry : fluidRates.entrySet()) {
            if (i >= 10) break;
            String name = new FluidStack(entry.getKey(), 1).getLocalizedName();
            double rate = entry.getValue() == null ? 0.0 : entry.getValue();
            // 量纲说明：FluidStack / fluidCache / 本方法的 rate 全都是 mB（1 B = 1000 mB）。
            // v1.2.29 及以前这里标成 "B/秒"，把 80000 mB/s 显示成 "80000B/秒"（差 1000 倍），
            // 现在统一标 mB（v1.2.30）。
            String rateText = rate >= 1000.0 ? ((long) rate) + "mB/秒"
                : String.format(java.util.Locale.ROOT, "%.1fmB/秒", rate);
            String suffix = (i == 9 && fluidRates.size() > 10) ? " (+" + (fluidRates.size() - 10) + ")" : "";
            lines.add(name + " " + rateText + suffix);
            i++;
        }
        return lines;
    }

    public void setConfiguredFluid(int value) {
        configuredFluid = Math.max(0, Math.min(MAX_FLUID_PARALLEL, value));
        markMachineDirty();
    }

    // ------------------------------------------------------------------
    // 产出模式（mod1 实时 / mod2 汇集 / mod3 结算）
    // ------------------------------------------------------------------

    public int getMode() {
        return mode;
    }

    /**
     * 切换产出模式。立即生效（不用开关机）：离开结算档时先把"已经跑了、还没结算"的秒数补算进缓存，
     * 免得切档把这段产出吞掉。
     */
    public void setMode(int value) {
        int next = clampMode(value);
        if (next == mode) return;
        settlePending();
        mode = next;
        settleSeconds = 0;
        flushCounter = 0;
        updateDisplayOutputs();
        markMachineDirty();
    }

    public static int clampMode(int value) {
        return Math.max(MODE_REALTIME, Math.min(MODE_SETTLE, value));
    }

    /** 当前档位的一批间隔（秒）：实时档恒 1，其余取 {@code infMinerFlushSeconds}（0 按 1 秒处理）。 */
    public int getIntervalSeconds() {
        return intervalSecondsFor(mode);
    }

    public static int intervalSecondsFor(int mode) {
        if (mode == MODE_REALTIME) return 1;
        return Math.max(1, flushIntervalSeconds());
    }

    /** 一句话模式说明（GUI 副面板 / WAILA 用）。 */
    public String getModeSummary() {
        int seconds = getIntervalSeconds();
        switch (mode) {
            case MODE_REALTIME:
                return "实时：每秒倾倒进 AE";
            case MODE_SETTLE:
                return "结算：每 " + seconds + " 秒结算一次";
            default:
                return "汇集：每 " + seconds + " 秒倾倒一次";
        }
    }

    /**
     * 把 {@code configuredOre/configuredFluid} 立即应用到运行参数（{@code /Elinar setore} 用）。
     *
     * <p>
     * 原逻辑只在 {@link #onWorkStart()} 里读配置，改完必须软锤关机再开机才生效；这里直接换掉
     * 并行、重算矿表与流体表，不用开关机。停机中的机器不补电（补了也用不上、还会白扣电网）。
     *
     * @return 机器正在运行时无线电网是否够付新容量的缓冲（false = 只按现有缓冲继续跑）
     */
    public boolean applyConfiguredParallel() {
        oreParallel = Math.max(1, Math.min(MAX_ORE_PARALLEL, configuredOre));
        fluidParallel = Math.max(0, Math.min(MAX_FLUID_PARALLEL, configuredFluid));
        // 并行调低后缓冲上限变小：多出来的电量退回无线电网，别静默吞掉。
        long cap = bufferCapacity();
        if (energyBuffer > cap) {
            if (owner != null) WirelessNetworkManager.addEUToGlobalEnergyMap(owner, energyBuffer - cap);
            energyBuffer = cap;
        }
        computeDropMap();
        computeFluids();
        boolean energyOk = true;
        if (mMachine && isWorkEnabled()) energyOk = refillEnergy();
        markMachineDirty();
        return energyOk;
    }

    @Override
    public IStructureDefinition<MTEComprehensiveMiningPlant> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEComprehensiveMiningPlant>builder()
                .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(new String[][] { { "~" } }))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public String[] getStructureDescription(ItemStack stackSize) {
        // 结构预览（blockrenderer6343）会对返回值取 length；1×1×1 没有可提示的方块
        return new String[0];
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        // 1×1×1 只有控制器，结构预览没有可摆的方块；留空避免 StructureLib 崩溃
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        return -1;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        checkPiece(STRUCTURE_PIECE_MAIN, 0, 0, 0, errors);
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    @Override
    public int getDamageToComponent(ItemStack aStack) {
        return 0;
    }

    @Override
    public boolean explodesOnComponentBreak(ItemStack aStack) {
        return false;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        int flush = Math.max(1, flushIntervalSeconds());
        return new MultiblockTooltipBuilder().addMachineType("Miner, CMP")
            .addInfo("综合资源采集集合体")
            .addInfo("每一并行消耗32768EU/t（LuV）")
            .addInfo("矿石并行 64矿石/秒")
            .addInfo("流体并行 一权重/秒")
            .addInfo("三种产出模式（GUI 并行面板切换，或 /Elinar setore mod1~3）：")
            .addInfo(" mod1 实时：每秒结算直接倾倒进 AE")
            .addInfo(" mod2 汇集：每秒产进缓存，每 " + flush + " 秒批量倾倒")
            .addInfo(" mod3 结算：每 " + flush + " 秒才结算产出并倾倒")
            .addInfo("倾倒间隔见 config: infMinerFlushSeconds")
            .addInfo("放置连接个人无线网络，通过AE输出资源")
            .addInfo("自带区块加载，服务器重启后自动恢复")
            .toolTipFinisher("Elinar");
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        mEUt = 0;
        mMaxProgresstime = TICKS_PER_SECOND;
        mOutputItems = null;
        mOutputFluids = null;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture casing = Textures.BlockIcons.MACHINE_CASINGS[9][Math.min(colorIndex + 1, 16)];
        boolean working = aActive && getBaseMetaTileEntity() != null && getBaseMetaTileEntity().isAllowedToWork();
        if (side == aFacing) {
            return working
                ? new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL_ACTIVE) }
                : new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_FRONT_ORE_DRILL) };
        }
        return new ITexture[] { casing, TextureFactory.of(Textures.BlockIcons.OVERLAY_ME_HATCH) };
    }

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = new AENetworkProxy(this, "aeProxy", getStackForm(1), true);
            proxy.setFlags(GridFlags.REQUIRE_CHANNEL);
            proxy.setValidSides(connectableSides());
        }
        return proxy;
    }

    private EnumSet<ForgeDirection> connectableSides() {
        EnumSet<ForgeDirection> sides = EnumSet.allOf(ForgeDirection.class);
        sides.remove(getBaseMetaTileEntity().getFrontFacing());
        return sides;
    }

    private void refreshProxySides() {
        if (proxy != null) {
            proxy.setValidSides(connectableSides());
            IGridNode node = proxy.getNode();
            if (node != null) node.updateState();
        }
    }

    @Override
    public DimensionalCoord getLocation() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return new DimensionalCoord(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    @Override
    public void gridChanged() {}

    @Override
    public IGridNode getGridNode(ForgeDirection side) {
        return getProxy().getNode();
    }

    @Override
    public IGridNode getActionableNode() {
        return getProxy().getNode();
    }

    @Override
    public AECableType getCableConnectionType(ForgeDirection side) {
        return side == getBaseMetaTileEntity().getFrontFacing() ? AECableType.NONE : AECableType.SMART;
    }

    @Override
    public void onFacingChange() {
        refreshProxySides();
    }

    @Override
    public void securityBreak() {}

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        owner = aBaseMetaTileEntity.getOwnerUuid();
        if (owner == null) owner = WirelessNetworkManager.processInitialSettings(aBaseMetaTileEntity);
        if (owner != null) WirelessNetworkManager.strongCheckOrAddUser(owner);
        getProxy().onReady();
        MinerRegistry.register(this);
        chunkLoaded = ElinarChunkManager.requestChunk((TileEntity) aBaseMetaTileEntity);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        // 世界锚票据在 onFirstTick 申请；配额满等极端情况下每 5 秒重试一次。
        if (!chunkLoaded && aTick % 100L == 0L) {
            chunkLoaded = ElinarChunkManager.requestChunk((TileEntity) aBaseMetaTileEntity);
        }

        BaseMetaTileEntity base = (BaseMetaTileEntity) aBaseMetaTileEntity;
        boolean allowed = base.isAllowedToWork() && mMachine;
        if (allowed && !wasWorking) {
            wasWorking = true;
            workTicks = 0L;
            onWorkStart();
        } else if (!allowed && wasWorking) {
            wasWorking = false;
            // 停机（断电/软锤）不释放区块票据：否则区块卸载后机器无法自己恢复。
        }

        // 秒表每 TICKS_PER_SECOND 走一格：
        // mod1 实时 = 每秒产一次、每秒倒一次；mod2 汇集 = 每秒产、按间隔倒；mod3 结算 = 按间隔才算一次账。
        // 停机时也照常把已经攒下的批次（汇集档缓存 / 结算档送不进去的残留）按间隔送进 AE，
        // 免得关机器后产出卡在内部缓存里等下次开机。
        boolean tickSecond = aTick % TICKS_PER_SECOND == 0;
        if (!allowed) {
            if (tickSecond && shouldFlushToAe()) flushToAE();
            return;
        }

        workTicks++;
        long perTick = (long) (oreParallel + fluidParallel) * EU_PER_PARALLEL;
        if (energyBuffer < perTick) {
            base.disableWorking();
            return;
        }
        energyBuffer -= perTick;

        if (tickSecond) {
            boolean flushDue;
            if (mode == MODE_SETTLE) {
                // 结算档只数“机器真正在跑”的秒数：停机不计，避免关了机器还能白拿产出。
                settleSeconds++;
                flushDue = settleSeconds >= getIntervalSeconds();
                if (flushDue) {
                    int seconds = settleSeconds;
                    settleSeconds = 0;
                    generateOres(seconds);
                    generateFluid(seconds);
                }
            } else {
                flushDue = shouldFlushToAe();
                generateOres(1);
                generateFluid(1);
            }
            if (flushDue) flushToAE();
            // 结算档的产物显示跟着结算刷新；另外两档照旧每秒刷新。
            if (flushDue || mode != MODE_SETTLE) updateDisplayOutputs();
        }
        if (aTick % REFILL_INTERVAL == 0 && !refillEnergy()) {
            base.disableWorking();
        }
    }

    private void onWorkStart() {
        oreParallel = Math.max(1, Math.min(MAX_ORE_PARALLEL, configuredOre));
        fluidParallel = Math.max(0, Math.min(MAX_FLUID_PARALLEL, configuredFluid));
        computeDropMap();
        computeFluids();
        flushCounter = 0;
        energyBuffer = 0L;
        if (!refillEnergy()) {
            long oneSecond = (long) (oreParallel + fluidParallel) * EU_PER_PARALLEL * TICKS_PER_SECOND;
            if (owner == null || !WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -oneSecond)) {
                ((BaseMetaTileEntity) getBaseMetaTileEntity()).disableWorking();
                return;
            }
            energyBuffer = oneSecond;
        }
        loadChunk();
    }

    /**
     * 产矿一次，{@code seconds} = 这次要结算多少秒的量（实时/汇集档恒 1，结算档 = 攒下的秒数）。
     *
     * <p>
     * 按每种矿的“期望份额”累计：不足 1 个的小数留到下一次，避免低并行时稀有矿（期望 &lt; 1 个/秒）
     * 被整秒取整抹掉，也避免随机长尾导致几十分钟不出。
     */
    private void generateOres(int seconds) {
        if (oreTable == null || oreChance == null || oreCredit == null || seconds <= 0) return;
        lastOres.clear();
        // 用 long：48000 并行 × 86400 秒（配置上限）已经超出 int。
        long amount = (long) ORE_PER_SECOND_PER_PARALLEL * oreParallel * seconds;
        for (int i = 0; i < oreTable.length; i++) {
            double credit = oreCredit[i] + amount * oreChance[i];
            long count = (long) credit;
            if (count <= 0L) {
                oreCredit[i] = credit;
                continue;
            }
            oreCredit[i] = credit - count;
            itemCache.merge(oreTable[i], count, Long::sum);
            lastOres.merge(oreTable[i], count, Long::sum);
        }
    }

    /** 把缓存快照塞给原生 GUI 的产物区（只是显示副本，不参与实际输出）。 */
    private void updateDisplayOutputs() {
        // 显示“这一秒的循环产量”，不读缓存（缓存会进 AE 清空）
        if (!lastOres.isEmpty()) {
            ItemStack[] items = new ItemStack[lastOres.size()];
            int i = 0;
            for (Map.Entry<GTUtility.ItemId, Long> entry : lastOres.entrySet()) {
                ItemStack stack = entry.getKey()
                    .getItemStack(1);
                if (stack == null) continue;
                stack.stackSize = (int) Math.min(entry.getValue() == null ? 1L : entry.getValue(), Integer.MAX_VALUE);
                items[i++] = stack;
                if (i >= items.length) break;
            }
            mOutputItems = items;
        } else {
            mOutputItems = null;
        }
        if (!fluidRates.isEmpty()) {
            FluidStack[] fluids = new FluidStack[fluidRates.size()];
            int i = 0;
            for (Map.Entry<Fluid, Double> entry : fluidRates.entrySet()) {
                double amount = entry.getValue() == null ? 0.0 : entry.getValue();
                if (amount <= 0.0) continue;
                fluids[i++] = new FluidStack(
                    entry.getKey(),
                    (int) Math.min(Math.max(1L, Math.round(amount)), Integer.MAX_VALUE));
                if (i >= fluids.length) break;
            }
            mOutputFluids = fluids;
        } else {
            mOutputFluids = null;
        }
    }

    private void generateFluid(int seconds) {
        if (seconds <= 0) return;
        for (Map.Entry<Fluid, Double> entry : fluidRates.entrySet()) {
            Fluid fluid = entry.getKey();
            double rate = entry.getValue() == null ? 0.0 : entry.getValue();
            double credit = fluidCredit.getOrDefault(fluid, 0.0) + rate * seconds;
            long count = (long) credit;
            if (count <= 0L) {
                fluidCredit.put(fluid, credit);
                continue;
            }
            fluidCredit.put(fluid, credit - count);
            fluidCache.merge(fluid, count, Long::sum);
        }
    }

    /**
     * 结算档：把“已经运行、但还没结算”的秒数补算进内部缓存（切档 / 拆机时用）。
     *
     * <p>
     * 这些秒的电已经按 tick 扣过了，产出还在机器手里，不补算就等于白跑。非结算档下
     * {@code settleSeconds} 恒为 0，本方法直接返回。
     */
    private void settlePending() {
        int seconds = settleSeconds;
        if (seconds <= 0) return;
        settleSeconds = 0;
        generateOres(seconds);
        generateFluid(seconds);
    }

    private void flushToAE() {
        if (proxy == null || !proxy.isActive()) return;
        try {
            IStorageGrid storage = getProxy().getStorage();
            IMEMonitor<IAEItemStack> itemInv = storage.getItemInventory();
            Iterator<Map.Entry<GTUtility.ItemId, Long>> it = itemCache.entrySet()
                .iterator();
            while (it.hasNext()) {
                Map.Entry<GTUtility.ItemId, Long> entry = it.next();
                long remaining = entry.getValue();
                while (remaining > 0L) {
                    int batch = (int) Math.min(remaining, Integer.MAX_VALUE);
                    IAEItemStack rest = Platform.poweredInsert(
                        getProxy().getEnergy(),
                        itemInv,
                        AEItemStack.create(
                            entry.getKey()
                                .getItemStack(batch)),
                        getActionSource());
                    long accepted = batch - (rest == null ? 0L : rest.getStackSize());
                    if (accepted <= 0L) break;
                    remaining -= accepted;
                }
                if (remaining <= 0L) it.remove();
                else entry.setValue(remaining);
            }
            IMEMonitor<IAEFluidStack> fluidInv = storage.getFluidInventory();
            Iterator<Map.Entry<Fluid, Long>> fit = fluidCache.entrySet()
                .iterator();
            while (fit.hasNext()) {
                Map.Entry<Fluid, Long> fe = fit.next();
                long remaining = fe.getValue();
                while (remaining > 0L) {
                    int batch = (int) Math.min(remaining, Integer.MAX_VALUE);
                    IAEFluidStack rest = Platform.poweredInsert(
                        getProxy().getEnergy(),
                        fluidInv,
                        AEFluidStack.create(new FluidStack(fe.getKey(), batch)),
                        getActionSource());
                    long accepted = batch - (rest == null ? 0L : rest.getStackSize());
                    if (accepted <= 0L) break;
                    remaining -= accepted;
                }
                if (remaining <= 0L) fit.remove();
                else fe.setValue(remaining);
            }
        } catch (GridAccessException ignored) {}
    }

    /** 配置里的 AE 批量倾倒间隔（秒）；0 = 实时（每秒倒一次，旧行为）。 */
    private static int flushIntervalSeconds() {
        return Math.max(0, Math.min(86400, Config.infMinerFlushSeconds));
    }

    /** 秒表走一格；到点了返回 true（实时档间隔恒 1，等于每秒都到点）。 */
    private boolean shouldFlushToAe() {
        if (++flushCounter >= getIntervalSeconds()) {
            flushCounter = 0;
            return true;
        }
        return false;
    }

    private BaseActionSource getActionSource() {
        if (actionSource == null) actionSource = new MachineSource(this);
        return actionSource;
    }

    private long bufferCapacity() {
        return (long) (oreParallel + fluidParallel) * EU_PER_PARALLEL * TICKS_PER_SECOND * 60L * 2L;
    }

    private boolean refillEnergy() {
        if (owner == null) return false;
        long need = bufferCapacity() - energyBuffer;
        if (need <= 0L) return true;
        boolean ok = WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -need);
        if (ok) energyBuffer += need;
        return ok;
    }

    private void computeDropMap() {
        GTUtility.ItemId[] previousTable = oreTable;
        double[] previousCredit = oreCredit;
        dropMap = null;
        oreTable = null;
        oreChance = null;
        oreCredit = null;
        try {
            ModDimensionDef def = DimensionDef.getDefForWorld(getBaseMetaTileEntity().getWorld());
            if (def == null || !def.canBeVoidMined()) return;
            DropMap base = VoidMinerUtility.dropMapsByDimName.getOrDefault(def.getDimensionName(), new DropMap());
            DropMap extra = VoidMinerUtility.extraDropsByDimName.getOrDefault(def.getDimensionName(), new DropMap());
            base.isDistributionCached(extra);
            GTUtility.ItemId[] ores = base.getOres();
            float totalWeight = base.getTotalWeight();
            // 该维度没有可虚空采掘的矿：保持 dropMap=null，GUI 走“空维度”提示，也避免空表 NPE。
            if (ores == null || ores.length == 0 || totalWeight <= 0.0f) return;
            dropMap = base;
            oreTable = ores;
            oreChance = new double[ores.length];
            // 同一维度的矿表没变时保留小数累计值：反复启停/重启不会把“差一点就满 1 个”的进度清零。
            if (previousTable != null && previousCredit != null
                && previousTable.length == ores.length
                && java.util.Arrays.equals(previousTable, ores)) {
                oreCredit = previousCredit;
            } else {
                oreCredit = new double[ores.length];
            }
            Map<GTUtility.ItemId, Float> weights = base.getInternalMap();
            for (int i = 0; i < ores.length; i++) {
                Float weight = weights.get(ores[i]);
                oreChance[i] = weight == null ? 0.0 : weight.doubleValue() / totalWeight;
            }
        } catch (Throwable ignored) {
            dropMap = null;
            oreTable = null;
            oreChance = null;
            oreCredit = null;
        }
    }

    private void computeFluids() {
        fluidRates.clear();
        try {
            int dim = getBaseMetaTileEntity().getWorld().provider.dimensionId;
            gregtech.api.objects.GTUODimension dimension = gregtech.GTMod.proxy.mUndergroundOil.GetDimension(dim);
            if (dimension == null) {
                fluidCredit.clear();
                return;
            }
            for (gregtech.api.objects.GTUOFluid fluid : dimension.getFluids()
                .values()) {
                if (fluid == null || fluid.getFluid() == null) continue;
                int amount = fluid.getRandomAmount(
                    new java.util.Random(
                        fluid.getFluid()
                            .getName()
                            .hashCode()));
                double rate = (double) amount * fluidParallel / OIL_DIVIDER;
                if (rate > 0.0) fluidRates.put(fluid.getFluid(), rate);
            }
            // 保留仍在本维度流体表里的累计值，清掉换维度后遗留的旧流体。
            fluidCredit.keySet()
                .retainAll(fluidRates.keySet());
        } catch (Throwable ignored) {
            fluidRates.clear();
            fluidCredit.clear();
        }
    }

    private void markMachineDirty() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    /** 区块票据当前是否已经建立（WAILA/信息面板用）。 */
    public boolean isChunkLoaded() {
        return chunkLoaded;
    }

    private void loadChunk() {
        if (chunkLoaded) return;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;
        chunkLoaded = ElinarChunkManager.requestChunk((TileEntity) base);
    }

    private void releaseChunk() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;
        ElinarChunkManager.releaseChunk((TileEntity) base);
        chunkLoaded = false;
    }

    @Override
    public void onBlockDestroyed() {
        // 拆机器前先把还没倾倒的批次送进 AE：批量倾倒改成 120 秒一倒之后，缓存里可能压着
        // 两分钟的产出，不能让它跟着方块一起消失（送不进去就算了，AE 离线时本来也没辙）。
        // 结算档的产出还没算出来（只有 settleSeconds），先补算再去倒。
        settlePending();
        flushToAE();
        // 只有控制器真的被拆掉时才释放票据。onRemoval 在区块/世界卸载时也会被调用
        // （BaseMetaTileEntity.invalidate），在那里释放会把世界锚票据在停服保存前删掉。
        releaseChunk();
        super.onBlockDestroyed();
    }

    @Override
    public void onRemoval() {
        MinerRegistry.unregister(this);
        if (proxy != null) proxy.invalidate();
        super.onRemoval();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("oreParallel", oreParallel);
        aNBT.setInteger("fluidParallel", fluidParallel);
        aNBT.setInteger("configuredOre", configuredOre);
        aNBT.setInteger("configuredFluid", configuredFluid);
        aNBT.setInteger("miningMode", mode);
        aNBT.setInteger("settleSeconds", settleSeconds);
        aNBT.setLong("energyBuffer", energyBuffer);
        if (owner != null) aNBT.setString("owner", owner.toString());

        NBTTagList items = new NBTTagList();
        for (Map.Entry<GTUtility.ItemId, Long> entry : itemCache.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag(
                "id",
                entry.getKey()
                    .writeToNBT());
            tag.setLong("n", entry.getValue());
            items.appendTag(tag);
        }
        aNBT.setTag("items", items);

        NBTTagList fluids = new NBTTagList();
        for (Map.Entry<Fluid, Long> entry : fluidCache.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(
                "fluid",
                entry.getKey()
                    .getName());
            tag.setLong("n", entry.getValue());
            fluids.appendTag(tag);
        }
        aNBT.setTag("cacheFluids", fluids);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        oreParallel = Math.max(1, Math.min(MAX_ORE_PARALLEL, aNBT.getInteger("oreParallel")));
        fluidParallel = Math.max(0, Math.min(MAX_FLUID_PARALLEL, aNBT.getInteger("fluidParallel")));
        configuredOre = aNBT.hasKey("configuredOre") ? aNBT.getInteger("configuredOre") : oreParallel;
        configuredFluid = aNBT.hasKey("configuredFluid") ? aNBT.getInteger("configuredFluid") : fluidParallel;
        if (configuredOre < 1) configuredOre = 1;
        if (configuredOre > MAX_ORE_PARALLEL) configuredOre = MAX_ORE_PARALLEL;
        if (configuredFluid < 0) configuredFluid = 0;
        if (configuredFluid > MAX_FLUID_PARALLEL) configuredFluid = MAX_FLUID_PARALLEL;
        // 老存档没有 miningMode：按配置默认档开局（默认 mod2 汇集），行为和 v1.2.29 一致。
        mode = clampMode(aNBT.hasKey("miningMode") ? aNBT.getInteger("miningMode") : Config.infMinerMode);
        // 结算档的“未结算秒数”也存盘：重启/读档不白丢最多一个间隔的产出（clamp 到间隔以内）。
        int maxPending = Math.max(0, getIntervalSeconds() - 1);
        settleSeconds = Math.max(0, Math.min(maxPending, aNBT.getInteger("settleSeconds")));
        energyBuffer = aNBT.getLong("energyBuffer");
        String ownerRaw = aNBT.getString("owner");
        if (ownerRaw != null && !ownerRaw.isEmpty()) {
            try {
                owner = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ignored) {
                owner = null;
            }
        }
        itemCache.clear();
        NBTTagList items = aNBT.getTagList("items", 10);
        for (int i = 0; i < items.tagCount(); i++) {
            NBTTagCompound tag = items.getCompoundTagAt(i);
            GTUtility.ItemId id = GTUtility.ItemId.create(tag.getCompoundTag("id"));
            if (id != null) itemCache.put(id, tag.getLong("n"));
        }
        fluidCache.clear();
        NBTTagList fluids = aNBT.getTagList("cacheFluids", 10);
        for (int i = 0; i < fluids.tagCount(); i++) {
            NBTTagCompound tag = fluids.getCompoundTagAt(i);
            Fluid fluid = FluidRegistry.getFluid(tag.getString("fluid"));
            if (fluid != null) fluidCache.put(fluid, tag.getLong("n"));
        }
    }

    // ---- IInventory / ISidedInventory 空实现（无物品槽） ----
    @Override
    public int getSizeInventory() {
        return 0;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return null;
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int index) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {}

    @Override
    public String getInventoryName() {
        return mName;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack itemStack) {
        return false;
    }

    @Override
    public boolean canInsertItem(int index, ItemStack itemStack, int direction) {
        return false;
    }

    @Override
    public boolean canExtractItem(int index, ItemStack itemStack, int direction) {
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return new int[0];
    }

    @Override
    public void markDirty() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    // ------------------------------------------------------------------
    // WAILA / 信息
    // ------------------------------------------------------------------

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { "状态: " + (isWorkEnabled() ? "运行" : "停机"), "模式: " + getModeSummary(),
            "耗电: " + getPerTickEUt() + " EU/t (" + getPerSecondEUt() + " EU/s)", "剩余电量: " + getEnergyPercent() + "%",
            "运行: " + (workTicks / TICKS_PER_SECOND) + "s", "区块加载: " + (chunkLoaded ? "已加载" : "未加载"),
            "缓存: " + itemCache.size() + " 种矿 / " + fluidCache.size() + " 种流体" };
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x, int y,
        int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setBoolean("infRunning", isWorkEnabled());
        tag.setString("infMode", getModeSummary());
        tag.setLong("infEUt", getPerTickEUt());
        tag.setLong("infEUtSec", getPerSecondEUt());
        tag.setLong("infPct", getEnergyPercent());
        tag.setLong("infSec", workTicks / TICKS_PER_SECOND);
        tag.setInteger("infOreTypes", itemCache.size());
        tag.setInteger("infFluidTypes", fluidCache.size());
        tag.setBoolean("infChunkLoaded", chunkLoaded);
        StringBuilder names = new StringBuilder();
        int i = 0;
        for (Fluid fluid : fluidRates.keySet()) {
            if (i++ >= 3) break;
            if (names.length() > 0) names.append(", ");
            names.append(new FluidStack(fluid, 1).getLocalizedName());
        }
        tag.setString("infFluidNames", names.toString());
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currenttip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        currenttip.add("状态: " + (tag.getBoolean("infRunning") ? "运行" : "停机"));
        String modeText = tag.getString("infMode");
        if (modeText != null && !modeText.isEmpty()) currenttip.add("模式: " + modeText);
        currenttip.add("耗电: " + tag.getLong("infEUt") + " EU/t (" + tag.getLong("infEUtSec") + " EU/s)");
        currenttip.add("剩余电量: " + tag.getLong("infPct") + "%");
        currenttip.add("运行: " + tag.getLong("infSec") + "s");
        currenttip.add("区块加载: " + (tag.getBoolean("infChunkLoaded") ? "已加载" : "未加载"));
        currenttip.add("缓存: " + tag.getInteger("infOreTypes") + " 种矿 / " + tag.getInteger("infFluidTypes") + " 种流体");
        String names = tag.getString("infFluidNames");
        if (names != null && !names.isEmpty()) currenttip.add("流体: " + names);
    }
}
