package elinarcurios.machine;

import static gregtech.api.enums.Dyes.MACHINE_METAL;
import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;

import java.util.Locale;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import elinarcurios.gui.BeamFluxMultiplierGui;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.Mods;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.beamline.MTEBeamlinePipe;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;
import gtnhlanth.common.hatch.MTEHatchOutputBeamline;

/**
 * 束流倍流器（Beam Flux Multiplier）—— 1×1×1 单方块，把束流的通量（rate）乘以玩家设定的倍率。
 *
 * <p>
 * 芷若 2026-11-07 需求原话：「我想做一个单方块的倍流器，就是可以把束流的通量翻几倍（可自定义）
 * 该单方块机器通过扳手调整机器的正对面，正面通过束流管输入束流，而背面则通过束流管输出束流，
 * 右键方块查看束流粒子情况，并可设置通量加倍倍数（1x-32768x）」。
 *
 * <p>
 * <b>★ 为什么继承 {@link MTEHatchInputBeamline} 而不是普通单方块</b>：GTNH 2.9.0 的束流传输是
 * 「出束仓每 20 tick 沿正面做一次直线射线」，射线遇到管道只跳过，遇到终点时用
 * {@code instanceof MTEHatchInputBeamline} 认仓 —— 普通 {@code MTETieredMachineBlock} 挡在线上会被
 * 直接 {@code return} 掉，收不到任何包。所以本方块伪装成束流输入仓，才能接住上游的包。
 *
 * <p>
 * <b>★ 为什么还要自己往下游推</b>：束流管不转发、不存包（{@code MTEBeamlinePipe} 只是射线可跳过的
 * 路标）。本方块在 {@link #setContents(BeamLinePacket)} 里收到包后，沿<b>背面</b>复制一次同样的
 * 直线射线，把乘好的新包塞进下游的束流输入仓。
 *
 * <p>
 * <b>★ 正面门禁</b>：上游射线不检查输入仓朝向，{@code setContents} 也拿不到"包从哪面来"。所以收到包时
 * 先沿正面扫一遍：只有正面存在合法的来束源（束流输出仓，或另一台倍流器的背面）才接受；否则拒绝，
 * 并在 GUI 里显示"背面来束/正面无来束"。
 *
 * <p>
 * <b>数值口径</b>：只乘 rate；energy / focus / particleId 原样透传。rate 是 int，乘法用 long 再
 * clamp 到 {@link Integer#MAX_VALUE}（所以串联虽然允许，但超过上限后没有意义）。
 * 每 20 tick 才有一包，GUI 读数因此每秒刷新一次。
 */
public class MTEBeamFluxMultiplier extends MTEHatchInputBeamline {

    /** 倍率范围（芷若 2026-11-07 拍板：32768 上限）。 */
    public static final int MIN_MULTIPLIER = 1;

    public static final int MAX_MULTIPLIER = 32768;

    /** 与出束仓射线保持一致的最大距离（代码里循环到 129 格）。 */
    private static final int MAX_BEAM_DISTANCE = 129;

    /** 串联深度保护，防止极端情况下同步递归过深。 */
    private static final int MAX_CHAIN_DEPTH = 64;

    /** 超过这个 tick 数没收到新包，GUI 就显示"无束流"（束流本身约 1 秒一包）。 */
    private static final long BEAM_TIMEOUT_TICKS = 40L;

    private static final String NBT_MULTIPLIER = "elinar.beamflux.multiplier";

    private static final IIconContainer INPUT_ACTIVE = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "iconsets/OVERLAY_BI_ACTIVE");
    private static final IIconContainer INPUT_IDLE = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "iconsets/OVERLAY_BI_SIDES");
    private static final IIconContainer OUTPUT_ACTIVE = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "iconsets/OVERLAY_BO_ACTIVE");
    private static final IIconContainer OUTPUT_IDLE = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "iconsets/OVERLAY_BO_SIDES");

    private static final ThreadLocal<Integer> PUSH_DEPTH = ThreadLocal.withInitial(() -> 0);

    /** 玩家设定的通量倍率（1 ~ 32768）。 */
    private int multiplier = MIN_MULTIPLIER;

    /** 最近一次收到的束流（包括被门禁拒绝的包，仅供 GUI 显示）。 */
    private BeamInformation lastInput;

    /** 最近一次成功转发出去的束流。 */
    private BeamInformation lastOutput;

    private long lastBeamTick;

    private boolean lastRejected;

    private boolean lastBackSource;

    private boolean lastNoSink;

    private boolean lastCapped;

    public MTEBeamFluxMultiplier(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    /** 读档 / 客户端重建用。 */
    public MTEBeamFluxMultiplier(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEBeamFluxMultiplier(mName, mTier, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 束流处理
    // ------------------------------------------------------------------

    /**
     * 上游出束仓每 20 tick 会调用一次；这里同步完成"门禁 → 乘倍率 → 存本地 → 推下游"。
     * 注意：不能等到下一个 MOVE_AT 再推，否则会平白多最多 1 秒延迟，而且中间包可能被父类清掉。
     */
    @Override
    public void setContents(BeamLinePacket in) {
        if (in == null || in.getContent() == null
            || in.getContent()
                .getRate() <= 0) {
            super.setContents(null);
            return;
        }

        final int depth = PUSH_DEPTH.get();
        if (depth >= MAX_CHAIN_DEPTH) return;
        PUSH_DEPTH.set(depth + 1);
        try {
            handleBeam(in);
        } finally {
            PUSH_DEPTH.set(depth);
        }
    }

    private void handleBeam(BeamLinePacket in) {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;

        final BeamInformation src = in.getContent();
        lastInput = src;
        lastBeamTick = now(base);

        final ForgeDirection front = base.getFrontFacing();
        if (!hasSourceOn(front)) {
            // 包不是从正面来的（或者正面根本没有源）：不缓存、不转发，GUI 里给出可读原因。
            lastRejected = true;
            lastBackSource = hasSourceOn(front.getOpposite());
            lastOutput = null;
            lastNoSink = false;
            lastCapped = false;
            super.setContents(null);
            return;
        }

        lastRejected = false;
        lastBackSource = false;

        final long multiplied = (long) src.getRate() * (long) multiplier;
        lastCapped = multiplied > Integer.MAX_VALUE;
        final int outRate = (int) Math.min((long) Integer.MAX_VALUE, multiplied);

        final BeamInformation out = new BeamInformation(src.getEnergy(), outRate, src.getParticleId(), src.getFocus());
        lastOutput = out;

        final BeamLinePacket packet = new BeamLinePacket(out);
        // 让父类处理 delay / 保留窗口 / 扫描仪缓存（也会在 265 上 notifyWatchers，无 watcher 时无副作用）。
        super.setContents(packet);
        lastNoSink = !pushDownstream(packet);
    }

    /**
     * 沿背面复制一次出束仓的直线射线：跳过束流管，把包交给下游的束流输入仓。
     *
     * <p>
     * 对普通输入仓不检查朝向（与 GT 出束仓一致：多方块的输入仓正面未必朝着管道，但照样能收）；
     * 对同类倍流器则要求它的正面朝着我们，否则会把包灌进它的背面（白跑一趟）。
     */
    private boolean pushDownstream(BeamLinePacket packet) {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return false;

        final ForgeDirection back = base.getFrontFacing()
            .getOpposite();
        for (int distance = 1; distance <= MAX_BEAM_DISTANCE; distance++) {
            final IGregTechTileEntity next = teAhead(base, back, distance);
            if (next == null) return false;
            final IMetaTileEntity meta = next.getMetaTileEntity();
            if (meta == null) return false;

            if (meta instanceof MTEBeamlinePipe) {
                ((MTEBeamlinePipe) meta).markUsed();
                continue;
            }
            if (meta instanceof MTEBeamFluxMultiplier) {
                final MTEBeamFluxMultiplier device = (MTEBeamFluxMultiplier) meta;
                if (device.isInputFacing(back.getOpposite())) {
                    device.setContents(packet);
                    return true;
                }
                return false;
            }
            if (meta instanceof MTEHatchInputBeamline) {
                ((MTEHatchInputBeamline) meta).setContents(packet);
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * 沿某个方向找"这一包是谁推过来的"：管道跳过，第一个非管道方块必须是束流输出仓（或同类倍流器的
     * 背面），否则视为没有合法来束源。
     */
    private boolean hasSourceOn(ForgeDirection dir) {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || dir == null || dir == ForgeDirection.UNKNOWN) return false;

        for (int distance = 1; distance <= MAX_BEAM_DISTANCE; distance++) {
            final IGregTechTileEntity next = teAhead(base, dir, distance);
            if (next == null) return false;
            final IMetaTileEntity meta = next.getMetaTileEntity();
            if (meta == null) return false;

            if (meta instanceof MTEBeamlinePipe) continue;
            if (meta instanceof MTEHatchOutputBeamline) {
                return ((MTEHatchOutputBeamline) meta).isOutputFacing(dir.getOpposite());
            }
            if (meta instanceof MTEBeamFluxMultiplier) {
                return ((MTEBeamFluxMultiplier) meta).isOutputFacing(dir.getOpposite());
            }
            return false;
        }
        return false;
    }

    /**
     * 取方向上第 distance 格的 GT 方块。先用 {@code World.blockExists} 判一下，避免为了扫门禁/推下游
     * 把远处未加载的区块拉起来（GT 自己的出束仓射线会加载区块，本方块不重复这个副作用）。
     */
    private static IGregTechTileEntity teAhead(IGregTechTileEntity base, ForgeDirection dir, int distance) {
        final World world = base.getWorld();
        if (world == null) return null;
        final int x = base.getXCoord() + dir.offsetX * distance;
        final int y = base.getYCoord() + dir.offsetY * distance;
        final int z = base.getZCoord() + dir.offsetZ * distance;
        if (!world.blockExists(x, y, z)) return null;
        final TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof IGregTechTileEntity ? (IGregTechTileEntity) tile : null;
    }

    private static long now(IGregTechTileEntity base) {
        final World world = base.getWorld();
        return world == null ? 0L : world.getTotalWorldTime();
    }

    // ------------------------------------------------------------------
    // 朝向 / 管道连接
    // ------------------------------------------------------------------

    @Override
    public boolean isInputFacing(ForgeDirection side) {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && side == base.getFrontFacing();
    }

    @Override
    public boolean isDataInputFacing(ForgeDirection side) {
        return isInputFacing(side);
    }

    @Override
    public boolean isOutputFacing(ForgeDirection side) {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        return base != null && side == base.getFrontFacing()
            .getOpposite();
    }

    /** 管道渲染连接位：正面（进）和背面（出）都显示连接。 */
    @Override
    public boolean canConnect(ForgeDirection side) {
        return isInputFacing(side) || isOutputFacing(side);
    }

    // ------------------------------------------------------------------
    // 贴图：正面输入仓 overlay、背面输出仓 overlay
    // ------------------------------------------------------------------

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        final int color = Math.max(0, Math.min(15, colorIndex));
        final ITexture casing = MACHINE_CASINGS[mTier][color + 1];

        if (aFacing != null && side == aFacing) {
            return new ITexture[] { casing, beamOverlay(aActive ? INPUT_ACTIVE : INPUT_IDLE, color) };
        }
        if (aFacing != null && side == aFacing.getOpposite()) {
            return new ITexture[] { casing, beamOverlay(aActive ? OUTPUT_ACTIVE : OUTPUT_IDLE, color) };
        }
        return new ITexture[] { casing };
    }

    private static ITexture beamOverlay(IIconContainer icon, int color) {
        return TextureFactory.of(icon, Dyes.getModulation(color, MACHINE_METAL.getRGBA()));
    }

    // ------------------------------------------------------------------
    // 交互 / GUI
    // ------------------------------------------------------------------

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        super.onRightclick(aBaseMetaTileEntity, aPlayer);
        if (aBaseMetaTileEntity.isServerSide()) {
            openGui(aPlayer);
        }
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    protected boolean forceUseMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new BeamFluxMultiplierGui(this).build(guiData, syncManager, uiSettings);
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger(NBT_MULTIPLIER, multiplier);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey(NBT_MULTIPLIER)) {
            multiplier = clampMultiplier(aNBT.getInteger(NBT_MULTIPLIER));
        }
    }

    // ------------------------------------------------------------------
    // 状态 / 信息
    // ------------------------------------------------------------------

    public int getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(int value) {
        final int clamped = clampMultiplier(value);
        if (clamped == multiplier) return;
        multiplier = clamped;
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    public static int clampMultiplier(int value) {
        return Math.max(MIN_MULTIPLIER, Math.min(MAX_MULTIPLIER, value));
    }

    public int getLastParticleId() {
        return lastInput == null ? -1 : lastInput.getParticleId();
    }

    public double getLastEnergy() {
        return lastInput == null ? 0.0D : lastInput.getEnergy();
    }

    public double getLastFocus() {
        return lastInput == null ? 0.0D : lastInput.getFocus();
    }

    public int getLastInputRate() {
        return lastInput == null ? 0 : lastInput.getRate();
    }

    public int getLastOutputRate() {
        return lastOutput == null ? 0 : lastOutput.getRate();
    }

    /** GUI / 扫描仪共用的短状态（放在一行里，避免 MUI2 的 [SIZING] 溢出警告）。 */
    public String describeStatus() {
        if (lastInput == null) return "无束流";
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return "无束流";
        if (now(base) - lastBeamTick > BEAM_TIMEOUT_TICKS) return "无束流";
        if (lastRejected) return lastBackSource ? "背面来束" : "正面无来束";
        if (lastNoSink) return "下游无接收仓";
        if (lastCapped) return "通量已封顶";
        return "正常";
    }

    @Override
    public String[] getInfoData() {
        return new String[] { getLocalName(), "倍率：" + multiplier + "x",
            String.format(Locale.ROOT, "输入通量：%,d", getLastInputRate()),
            String.format(Locale.ROOT, "输出通量：%,d", getLastOutputRate()), "状态：" + describeStatus() };
    }

    @Override
    public String[] getDescription() {
        return new String[] { EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamflux.desc.0"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamflux.desc.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamflux.desc.2"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamflux.desc.3"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("elinarcurios.beamflux.desc.4") };
    }
}
