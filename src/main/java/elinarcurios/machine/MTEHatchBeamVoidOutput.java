package elinarcurios.machine;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import elinarcurios.beam.BeamVoidLink;
import elinarcurios.gui.BeamVoidOutputGui;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IHatchWatcher;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamCrafter;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;
import gtnhlanth.common.tileentity.MTETargetChamber;

/**
 * 束流虚空输出仓 —— 装在多方块的<b>束流输入仓</b>位置（"输出"是相对存储器而言：从存储输出粒子）。
 *
 * <p>
 * 继承 {@link MTEHatchInputBeamline}，所以 LHC / 直线加速器 / 靶室 / 束流机的进束位都认它。
 * 它每秒把一包 {@code BeamInformation} 放进 {@code dataPacket}，机器按原版节奏读走。
 *
 * <p>
 * <b>参数</b>（芷若 2026-11-14 拍板）：只输出<b>一种</b>粒子；能量在界面上按 <b>eV</b> 填（内部 /1000 换成
 * keV，GT 的束流能量单位）；通量 = 每秒粒子数（上限 {@link Integer#MAX_VALUE}）；聚焦恒定
 * {@link #OUTPUT_FOCUS}=99。
 *
 * <p>
 * <b>两个启动开关（或关系，2026-11-14 她补充）</b>：
 * <ul>
 * <li><b>随机器</b>（默认开）：普通机器要求"开机"（{@code isAllowedToWork()}）；束流合成器则要求
 * <b>有配方在跑</b>（它能无粒子开工，跑起来再按设定速度补，跑完就停，避免空闲灌满 20 亿缓存）。</li>
 * <li><b>随红石</b>（默认关）：本方块收到红石信号就发 —— 靶室这类"没粒子就不启动"的机器靠外部红石控制
 * 什么时候供束。</li>
 * </ul>
 * 两个都关 = 不发；任一满足就发。
 *
 * <p>
 * <b>扣料口径</b>（"机器缓存满后自动停止"就是靠这个）：
 * <ul>
 * <li>机器把包<b>吃掉并清空</b>（束流合成器每 tick 吸收）→ 视为消耗，扣一次，且一秒最多扣一次；</li>
 * <li>机器<b>只读不清空</b>（LHC / 加速器 / 靶室按通量算）→ 每秒检查一次控制器 {@code isActive()}，
 * 运行过就扣一次；机器闲置不扣。</li>
 * </ul>
 * 另外对束流合成器会读它的 {@code getBufferMap()} 算剩余空间，<b>缓存满就一颗粒子都不发</b>。
 */
public class MTEHatchBeamVoidOutput extends MTEHatchInputBeamline implements IBeamVoidHatch {

    /** 输出聚焦恒定值（芷若 2026-11-14 定：99）。 */
    public static final float OUTPUT_FOCUS = 99.0F;

    /** 束流合成器（{@link MTEBeamCrafter}）的单粒子缓存上限，与它内部的 MAX_BUFFER 一致。 */
    private static final long BEAM_CRAFTER_MAX_BUFFER = 2_000_000_000L;

    private static final String NBT_LINK = "elinarBeamVoidLink";
    private static final String NBT_SETTINGS = "elinarBeamVoidSettings";

    private int linkDim;
    private int linkX;
    private int linkY;
    private int linkZ;
    private boolean bound;

    /** 绑定的存储器 UUID（新协议）；为 null 时退回旧坐标绑定。 */
    private UUID storageId;

    private boolean coordFallback;

    /** 输出哪种粒子（-1 = 未设置）。 */
    private int particleId = -1;

    /** 每秒输出通量（粒子数 / 秒）。 */
    private long ratePerSecond = 0L;

    /** 输出能量（eV）。GT 内部束流能量是 keV，所以真正发出去时 /1000。 */
    private double energyEv = 0.0D;

    /** 是否随机器启动（默认开）：机器开机才发；束流合成器则要求"有配方在跑"（它自己能无粒子开工）。 */
    private boolean followMachine = true;

    /** 是否随红石启动（默认关）：本方块收到红石信号就发。与随机器启动是"或"关系。 */
    private boolean followRedstone = false;

    // 运行期状态（不持久化）
    private long pendingDeduct = 0L;

    private int pendingParticleId = -1;

    private int activeTicks = 0;

    /** 上一 tick 采到的机器进度（-1 = 还没采到）；用于靶室"按进度扣料"。 */
    private int lastProgressSample = -1;

    private boolean progressAdvanced;

    private BeamInformation lastSent = null;

    public MTEHatchBeamVoidOutput(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    /** 反序列化 / 客户端重建用。 */
    public MTEHatchBeamVoidOutput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchBeamVoidOutput(mName, mTier, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 每秒一包：发束流 + 结算扣料
    // ------------------------------------------------------------------

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isServerSide()) return;

        final MTEMultiBlockBase controller = findController();
        observeConsumption(aBaseMetaTileEntity, controller);

        if (aTick % 20L == 0L) {
            settleLastSecond(aBaseMetaTileEntity, controller);
            grant(aBaseMetaTileEntity, controller);
        }

        aBaseMetaTileEntity.setActive(this.dataPacket != null);
    }

    /**
     * 每 tick 采样：①包被清空 = 机器真吃掉了 → 立刻扣；②记录进度是否动过（靶室按进度扣料）。
     */
    private void observeConsumption(IGregTechTileEntity base, MTEMultiBlockBase controller) {
        if (controller != null) {
            // 进度采样：只有机器真在推进（说明它确实在按我们的束流干活）才计入
            final int progress = controller.getProgresstime();
            if (lastProgressSample >= 0 && progress != lastProgressSample) progressAdvanced = true;
            lastProgressSample = progress;
        }
        if (pendingDeduct <= 0L) return;
        if (this.dataPacket == null) {
            charge(base, pendingParticleId, pendingDeduct);
            pendingDeduct = 0L;
            return;
        }
        if (controller != null && !(controller instanceof MTETargetChamber)) {
            final IGregTechTileEntity ctl = controller.getBaseMetaTileEntity();
            if (ctl != null && ctl.isActive()) activeTicks++;
        }
    }

    /**
     * 秒末结算：
     * <ul>
     * <li>靶室：按「这一秒进度有没有动」扣 —— 它只读不清空，且能量/粒子不匹配时进度不走，
     * 用 isActive 会误扣（机器卡着也算在跑）；</li>
     * <li>其它（LHC/加速器/分裂器…）：按「这一秒机器是否在跑」扣，它们的运行就意味着在用通量。</li>
     * </ul>
     */
    private void settleLastSecond(IGregTechTileEntity base, MTEMultiBlockBase controller) {
        if (pendingDeduct > 0L) {
            final boolean consumed = controller instanceof MTETargetChamber ? progressAdvanced : activeTicks > 0;
            if (consumed) charge(base, pendingParticleId, pendingDeduct);
        }
        pendingDeduct = 0L;
        activeTicks = 0;
        progressAdvanced = false;
    }

    private void charge(IGregTechTileEntity base, int pid, long amount) {
        if (pid < 0 || amount <= 0L) return;
        final MTEBeamVoidStorage storage = findStorage();
        if (storage != null) storage.tryConsume(pid, amount);
    }

    /** 决定这一秒发什么；发不出去就把包清空（机器会认为没束流）。 */
    private void grant(IGregTechTileEntity base, MTEMultiBlockBase controller) {
        if (!bound) {
            clearBeam();
            return;
        }
        final MTEBeamVoidStorage storage = findStorage();
        if (storage == null) {
            clearBeam();
            return;
        }
        // 每秒补登记一次（绑定那一刻存储器区块没加载时也能补上）
        storage.registerHatch(false, BeamVoidLink.pack(base.getXCoord(), base.getYCoord(), base.getZCoord()));
        if (particleId < 0) {
            clearBeam();
            return;
        }
        // 启动条件：随机器 / 随红石，二者满足其一就发（她 2026-11-14 定的"或"关系）
        final boolean redstoneGate = followRedstone && hasRedstone(base);
        if (!redstoneGate && !isMachineGateMet(controller)) {
            clearBeam();
            return;
        }

        final long stock = storage.getCount(particleId);
        long toSend = Math.min(ratePerSecond, stock);
        if (toSend <= 0L) {
            clearBeam();
            return;
        }
        if (toSend > Integer.MAX_VALUE) toSend = Integer.MAX_VALUE;

        // 束流合成器：只按缓存余量兜底（它自带 20 亿缓存；"不在跑配方就不发"由上面的启动条件负责）
        if (controller instanceof MTEBeamCrafter) {
            final long headroom = BEAM_CRAFTER_MAX_BUFFER - beamCrafterBuffer((MTEBeamCrafter) controller, particleId);
            if (headroom < toSend) toSend = headroom;
            if (toSend <= 0L) {
                clearBeam();
                return;
            }
        }

        final BeamInformation info = new BeamInformation(
            (float) (energyEv / 1000.0D),
            (int) toSend,
            particleId,
            OUTPUT_FOCUS);
        final boolean changed = lastSent == null || !lastSent.isEqual(info);
        this.dataPacket = new BeamLinePacket(info);
        pendingDeduct = toSend;
        pendingParticleId = particleId;
        lastSent = info;
        if (changed) notifyWatchers();
    }

    private void clearBeam() {
        if (this.dataPacket != null) {
            this.dataPacket = null;
            notifyWatchers();
        }
        pendingDeduct = 0L;
        lastSent = null;
    }

    /** 找到绑定的多方块控制器（GT 在结构成型时会把控制器注册成仓的 watcher）。 */
    private MTEMultiBlockBase findController() {
        final List<IHatchWatcher> list = this.watchers;
        for (int i = 0; i < list.size(); i++) {
            final IHatchWatcher watcher = list.get(i);
            if (watcher instanceof MTEMultiBlockBase) return (MTEMultiBlockBase) watcher;
        }
        return null;
    }

    /**
     * 「随机器启动」这一路是否满足：
     * <ul>
     * <li>束流合成器：机器开机 <b>且</b> 有配方在跑 —— 它自己能无粒子开工，跑起来再按设定速度补，
     * 跑完就停，避免空闲时把 20 亿缓存灌满；</li>
     * <li>其它机器（LHC / 加速器 / 靶室…）：机器开机（{@code isAllowedToWork()}）即可 —— 它们要靠
     * 这包束流才会启动，用"正在运行"会死锁。</li>
     * </ul>
     */
    private boolean isMachineGateMet(MTEMultiBlockBase controller) {
        if (!followMachine || controller == null) return false;
        if (controller instanceof MTEBeamCrafter) {
            return controller.isAllowedToWork() && controller.mMaxProgresstime > 0;
        }
        return controller.isAllowedToWork();
    }

    /** 本方块是否收到红石信号（弱/强充能都算）。 */
    private static boolean hasRedstone(IGregTechTileEntity base) {
        if (base == null) return false;
        final World world = base.getWorld();
        if (world == null) return false;
        return world.isBlockIndirectlyGettingPowered(base.getXCoord(), base.getYCoord(), base.getZCoord());
    }

    /**
     * 读束流合成器里某种粒子已用的缓存。
     *
     * <p>
     * {@code getBufferMap()} 的返回类型是 fastutil 的 {@code Int2IntOpenHashMap}，而本工程只把 GT5U 当
     * compileOnly、类路径上没有 fastutil，所以这里走反射取 {@code get(int)} —— 失败就当作缓存是空的
     * （最坏退化成"不知道余量"，不会崩）。
     */
    private static long beamCrafterBuffer(MTEBeamCrafter crafter, int particleId) {
        try {
            final Object map = MTEBeamCrafter.class.getMethod("getBufferMap")
                .invoke(crafter);
            if (map == null) return 0L;
            final Object value = map.getClass()
                .getMethod("get", int.class)
                .invoke(map, particleId);
            return value instanceof Integer ? ((Integer) value).longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    // ------------------------------------------------------------------
    // 不接受外部推入的束流（本仓是"源"）
    // ------------------------------------------------------------------

    /**
     * 束流合成器（{@link MTEBeamCrafter}）读走一包后会调 {@code setContents(null)} 来"清空"；
     * 本仓必须照做，否则它会每 tick 都读到同一包（20 倍白嫖）。外部推入的包（束流管射线）则直接丢弃。
     */
    @Override
    public void setContents(BeamLinePacket in) {
        if (in == null) {
            this.dataPacket = null;
        }
    }

    /** 本仓不参与"收到后自动清空"的原版节奏，全部自己管理。 */
    @Override
    public void moveAround(IGregTechTileEntity aBaseMetaTileEntity) {}

    /** 管道不再连本仓（避免别的出束仓把包推进来）。 */
    @Override
    public boolean canConnect(ForgeDirection side) {
        return false;
    }

    // ------------------------------------------------------------------
    // 绑定（闪存左键存储器 → 右键本仓）
    // ------------------------------------------------------------------

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (tryLinkDataStick(aPlayer)) return true;
        super.onRightclick(aBaseMetaTileEntity, aPlayer);
        if (aBaseMetaTileEntity.isServerSide()) openGui(aPlayer);
        return true;
    }

    private boolean tryLinkDataStick(EntityPlayer player) {
        final ItemStack held = player == null ? null : player.inventory.getCurrentItem();
        if (!BeamVoidLink.isDataStick(held)) return false;

        if (player.isSneaking()) {
            clearLink();
            GTUtility.sendChatTrans(player, "elinarcurios.beamvoid.chat.unlinked");
            return true;
        }

        // 新协议：闪存里是存储器 UUID
        final UUID id = BeamVoidLink.readUuid(held);
        if (id != null) {
            setLinkUuid(id);
            GTUtility.sendChatTrans(player, "elinarcurios.beamvoid.chat.linked");
            return true;
        }

        // 旧协议：只有坐标（升级兼容）
        final int[] link = BeamVoidLink.read(held);
        if (link == null) {
            GTUtility.sendChatTrans(player, "elinarcurios.beamvoid.chat.no_link");
            return true;
        }
        setLink(link[0], link[1], link[2], link[3]);
        GTUtility.sendChatTrans(player, "elinarcurios.beamvoid.chat.linked");
        return true;
    }

    /** 新协议：绑 UUID —— 存储器搬走也能自动找回来。 */
    private void setLinkUuid(UUID id) {
        storageId = id;
        coordFallback = false;
        linkDim = 0;
        linkX = 0;
        linkY = 0;
        linkZ = 0;
        bound = true;
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            final MTEBeamVoidStorage storage = findStorage();
            if (storage != null) {
                storage.registerHatch(false, BeamVoidLink.pack(base.getXCoord(), base.getYCoord(), base.getZCoord()));
            }
            base.markDirty();
        }
    }

    /** 旧协议：绑坐标（升级兼容；找到一次就自动迁移成 UUID）。 */
    private void setLink(int dim, int x, int y, int z) {
        linkDim = dim;
        linkX = x;
        linkY = y;
        linkZ = z;
        coordFallback = true;
        bound = true;
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            final MTEBeamVoidStorage storage = findStorage();
            if (storage != null) {
                storage.registerHatch(false, BeamVoidLink.pack(base.getXCoord(), base.getYCoord(), base.getZCoord()));
            }
            base.markDirty();
        }
    }

    /**
     * 找绑定的存储器：优先按 UUID 查注册表；旧坐标绑定时退回坐标查找并在成功后迁移成 UUID。
     */
    private MTEBeamVoidStorage findStorage() {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return null;
        if (storageId != null) {
            final MTEBeamVoidStorage storage = BeamVoidStorageRegistry.find(storageId);
            if (storage != null) return storage;
        }
        if (coordFallback) {
            final MTEBeamVoidStorage storage = BeamVoidLink.find(base.getWorld(), linkDim, linkX, linkY, linkZ);
            if (storage != null) {
                storageId = storage.getStorageId();
                coordFallback = false;
                base.markDirty();
            }
            return storage;
        }
        return null;
    }

    private void clearLink() {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            final MTEBeamVoidStorage storage = findStorage();
            if (storage != null) {
                storage.unregisterHatch(false, BeamVoidLink.pack(base.getXCoord(), base.getYCoord(), base.getZCoord()));
            }
            base.markDirty();
        }
        bound = false;
        storageId = null;
        coordFallback = false;
        clearBeam();
    }

    @Override
    public void onBlockDestroyed() {
        clearLink();
        super.onBlockDestroyed();
    }

    @Override
    public void onRemoval() {
        clearLink();
        super.onRemoval();
    }

    // ------------------------------------------------------------------
    // IBeamVoidHatch
    // ------------------------------------------------------------------

    @Override
    public boolean isLinkedTo(MTEBeamVoidStorage storage) {
        if (!bound || storage == null) return false;
        if (storageId != null) return storageId.equals(storage.getStorageId());
        if (!coordFallback) return false;
        final IGregTechTileEntity base = storage.getBaseMetaTileEntity();
        if (base == null) return false;
        return base.getWorld().provider.dimensionId == linkDim && base.getXCoord() == linkX
            && base.getYCoord() == linkY
            && base.getZCoord() == linkZ;
    }

    @Override
    public boolean isVoidInputHatch() {
        return false;
    }

    // ------------------------------------------------------------------
    // 设置项（界面用）
    // ------------------------------------------------------------------

    public boolean isBound() {
        return bound;
    }

    public int getParticleId() {
        return particleId;
    }

    public void setParticleId(int value) {
        final int clamped = value < 0 || value >= MTEBeamVoidStorage.PARTICLE_COUNT ? -1 : value;
        if (clamped == particleId) return;
        particleId = clamped;
        markSettingsDirty();
    }

    public long getRatePerSecond() {
        return ratePerSecond;
    }

    public void setRatePerSecond(long value) {
        final long clamped = Math.max(0L, Math.min(Integer.MAX_VALUE, value));
        if (clamped == ratePerSecond) return;
        ratePerSecond = clamped;
        markSettingsDirty();
    }

    public double getEnergyEv() {
        return energyEv;
    }

    public void setEnergyEv(double value) {
        final double clamped = Math.max(0.0D, Math.min(1.0E18D, value));
        if (clamped == energyEv) return;
        energyEv = clamped;
        markSettingsDirty();
    }

    public boolean isFollowMachine() {
        return followMachine;
    }

    public void setFollowMachine(boolean value) {
        if (followMachine == value) return;
        followMachine = value;
        markSettingsDirty();
    }

    public boolean isFollowRedstone() {
        return followRedstone;
    }

    public void setFollowRedstone(boolean value) {
        if (followRedstone == value) return;
        followRedstone = value;
        markSettingsDirty();
    }

    private void markSettingsDirty() {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    public String describeLink() {
        if (!bound) return "未绑定：闪存左键存储器 → 右键本仓";
        if (storageId != null) {
            return "已绑定存储器 #" + storageId.toString()
                .substring(0, 8) + "（UUID，可随存储器搬家）";
        }
        return "已绑定 @ " + linkX + ", " + linkY + ", " + linkZ + "（旧坐标绑定，会自动迁移）";
    }

    /** 界面 / 扫描仪共用的一句话状态。 */
    public String describeStatus() {
        if (!bound) return "未绑定存储器";
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return "无方块实体";
        final MTEBeamVoidStorage storage = findStorage();
        if (storage == null) return "存储器未加载 / 已被拆";
        if (particleId < 0) return "未选粒子";
        final MTEMultiBlockBase controller = findController();
        final boolean redstone = followRedstone && hasRedstone(base);
        final boolean machine = isMachineGateMet(controller);
        if (!redstone && !machine) {
            if (followRedstone && !followMachine) return "未输出：没有红石信号";
            if (followMachine && !followRedstone) return "未输出：机器条件未满足";
            if (!followRedstone && !followMachine) return "未输出：两个启动开关都关着";
            return "未输出：机器条件和红石信号都没满足";
        }
        if (this.dataPacket == null) {
            return storage.getCount(particleId) <= 0L ? "存储器库存不足" : "待发（机器未读）";
        }
        final String what = MTEBeamVoidStorage.particleName(particleId) + " "
            + String.format(Locale.ROOT, "%,d", ratePerSecond)
            + "/s";
        return redstone && !machine ? "输出中（红石触发）：" + what : "输出中：" + what;
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (bound) {
            final NBTTagCompound link = new NBTTagCompound();
            if (storageId != null) link.setString("uuid", storageId.toString());
            if (coordFallback) {
                link.setInteger("dim", linkDim);
                link.setInteger("x", linkX);
                link.setInteger("y", linkY);
                link.setInteger("z", linkZ);
            }
            aNBT.setTag(NBT_LINK, link);
        }
        final NBTTagCompound settings = new NBTTagCompound();
        settings.setInteger("particle", particleId);
        settings.setLong("rate", ratePerSecond);
        settings.setDouble("energyEv", energyEv);
        settings.setBoolean("follow", followMachine);
        settings.setBoolean("followRedstone", followRedstone);
        aNBT.setTag(NBT_SETTINGS, settings);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        bound = false;
        storageId = null;
        coordFallback = false;
        if (aNBT.hasKey(NBT_LINK)) {
            final NBTTagCompound link = aNBT.getCompoundTag(NBT_LINK);
            final String uuid = link.getString("uuid");
            if (uuid != null && !uuid.isEmpty()) {
                try {
                    storageId = UUID.fromString(uuid);
                } catch (IllegalArgumentException ignored) {
                    storageId = null;
                }
            }
            if (storageId == null && link.hasKey("dim")) {
                linkDim = link.getInteger("dim");
                linkX = link.getInteger("x");
                linkY = link.getInteger("y");
                linkZ = link.getInteger("z");
                coordFallback = true;
            }
            bound = storageId != null || coordFallback;
        }
        if (aNBT.hasKey(NBT_SETTINGS)) {
            final NBTTagCompound settings = aNBT.getCompoundTag(NBT_SETTINGS);
            particleId = settings.getInteger("particle");
            if (particleId < 0 || particleId >= MTEBeamVoidStorage.PARTICLE_COUNT) particleId = -1;
            ratePerSecond = Math.max(0L, Math.min(Integer.MAX_VALUE, settings.getLong("rate")));
            energyEv = Math.max(0.0D, Math.min(1.0E18D, settings.getDouble("energyEv")));
            followMachine = !settings.hasKey("follow") || settings.getBoolean("follow");
            followRedstone = settings.getBoolean("followRedstone");
        }
    }

    // ------------------------------------------------------------------
    // 界面 / 信息
    // ------------------------------------------------------------------

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
        return new BeamVoidOutputGui(this).build(guiData, syncManager, uiSettings);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { EnumChatFormatting.BLUE + getLocalName() + EnumChatFormatting.RESET, describeLink(),
            "粒子：" + (particleId < 0 ? "-" : MTEBeamVoidStorage.particleName(particleId)),
            String.format(Locale.ROOT, "能量：%,.3f eV（= %,.6f keV）", energyEv, energyEv / 1000.0D),
            String.format(Locale.ROOT, "通量：%,d /s", ratePerSecond), "聚焦：" + (int) OUTPUT_FOCUS,
            "随机器开机：" + (followMachine ? "开" : "关"), "随红石启动：" + (followRedstone ? "开" : "关"), "状态：" + describeStatus() };
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.output.0"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.output.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.output.2"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.output.3"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.output.4") };
    }

    @Override
    public boolean isFluidInputAllowed(net.minecraftforge.fluids.FluidStack aFluid) {
        return false;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }
}
