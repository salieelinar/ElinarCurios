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
import elinarcurios.gui.BeamVoidInputGui;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEHatchAdvancedOutputBeamline;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.beamline.Particle;

/**
 * 束流虚空输入仓 —— 装在多方块的<b>束流输出仓</b>位置（"输入"是相对存储器而言：把束流输入到存储）。
 *
 * <p>
 * <b>继承谁</b>：{@link MTEHatchAdvancedOutputBeamline}（"过滤型束流出束仓"）。这是必须的 ——
 * GTNH 2.9 的 LHC 只认 {@code instanceof MTEHatchAdvancedOutputBeamline} 的出束位，而其它束流机
 * （分裂器/镜子/稳定器）的出束位只认 {@code instanceof MTEHatchOutputBeamline}；继承这个类两头都能装。
 * 结构校验走的是 adder 的 instanceof，不是方块 ID，所以自定义 ID 也能塞进原版多方块。
 *
 * <p>
 * <b>怎么收</b>：机器每轮把 {@code BeamInformation} 写进本仓的 {@code dataPacket}（每秒一轮）。
 * 绑定存储器后，本仓每 tick 检查一次：有包就<b>立刻入库并清空自己</b>（这样同一秒内不同机器/不同轮次
 * 的包不会互相覆盖），不做任何粒子过滤（19 种全收，她 2026-11-14 拍板）。
 *
 * <p>
 * <b>未绑定时</b>退化成一个原版过滤型出束仓（照常沿正面射线把束流推给下游管道），不会把料吞掉。
 */
public class MTEHatchBeamVoidInput extends MTEHatchAdvancedOutputBeamline
    implements IBeamVoidHatch, IBeamVoidCollector {

    private static final String NBT_LINK = "elinarBeamVoidLink";
    private static final String NBT_LAST = "elinarBeamVoidLast";
    private static final String NBT_TOTAL = "elinarBeamVoidTotal";

    private int linkDim;
    private int linkX;
    private int linkY;
    private int linkZ;
    private boolean bound;

    /**
     * 绑定的存储器身份号（新协议）。为 null 时退回旧坐标绑定（{@link #coordFallback}），
     * 一旦用坐标找到存储就自动迁移成 UUID（2026-11-14 起的方案）。
     */
    private UUID storageId;

    private boolean coordFallback;

    /** 界面读数：最近收到的一包。 */
    private int lastParticleId = -1;

    private int lastRate;

    private float lastEnergy;

    private float lastFocus;

    private long lastReceiveTick = -1L;

    /** 累计入库数量（含饱和加法的实际值）。 */
    private long totalReceived;

    public MTEHatchBeamVoidInput(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
        fillAllAccepted();
    }

    /** 反序列化 / 客户端重建用。 */
    public MTEHatchBeamVoidInput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        fillAllAccepted();
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchBeamVoidInput(mName, mTier, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 固定 19 种全收（不随 LHC 的力模块过滤）
    // ------------------------------------------------------------------

    private void fillAllAccepted() {
        for (Particle particle : Particle.VALUES) {
            acceptedInputMap.put(particle, Boolean.TRUE);
        }
    }

    /**
     * LHC 结构检查时会给过滤型出束仓塞"这个力模块允许的粒子集"。本仓固定全收，所以直接无视 ——
     * 注意这里不能改成"部分允许"，否则 LHC 的掷粒子权重会跟着变。
     */
    @Override
    public void setInitialParticleList(List<Particle> initialParticleList) {
        fillAllAccepted();
    }

    // ------------------------------------------------------------------
    // 束流接收：收到即入库
    // ------------------------------------------------------------------

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (!bound) {
            // 未绑定 = 原版过滤型出束仓（会沿射线推下游管道）
            super.onPostTick(aBaseMetaTileEntity, aTick);
            return;
        }

        final World world = aBaseMetaTileEntity.getWorld();
        if (world == null) return;

        // 每 5 秒补登记一次（防止"绑定那一刻存储器区块没加载"导致存储器统计漏掉本仓）
        if (aTick % 100L == 0L) {
            final MTEBeamVoidStorage storage = findStorage();
            if (storage != null) {
                storage.registerHatch(
                    true,
                    BeamVoidLink.pack(
                        aBaseMetaTileEntity.getXCoord(),
                        aBaseMetaTileEntity.getYCoord(),
                        aBaseMetaTileEntity.getZCoord()));
            }
        }

        final BeamLinePacket packet = this.dataPacket;
        if (packet != null) {
            final BeamInformation info = packet.getContent();
            if (info == null || info.getRate() <= 0) {
                this.dataPacket = null;
            } else {
                final MTEBeamVoidStorage storage = findStorage();
                if (storage != null) {
                    final long added = storage.addParticles(info.getParticleId(), info.getRate());
                    storage.registerHatch(
                        true,
                        BeamVoidLink.pack(
                            aBaseMetaTileEntity.getXCoord(),
                            aBaseMetaTileEntity.getYCoord(),
                            aBaseMetaTileEntity.getZCoord()));
                    lastParticleId = info.getParticleId();
                    lastRate = info.getRate();
                    lastEnergy = info.getEnergy();
                    lastFocus = info.getFocus();
                    lastReceiveTick = world.getTotalWorldTime();
                    totalReceived += added;
                    this.dataPacket = null;
                }
            }
        }

        final boolean active = lastReceiveTick >= 0L && world.getTotalWorldTime() - lastReceiveTick <= 20L;
        aBaseMetaTileEntity.setActive(active);
    }

    /** 绑定后不再往下游推包（包被我方吃掉了）；未绑定走原版逻辑。 */
    @Override
    public void moveAround(IGregTechTileEntity aBaseMetaTileEntity) {
        if (bound) return;
        super.moveAround(aBaseMetaTileEntity);
    }

    /**
     * 找绑定的存储器：优先按 UUID 查注册表；没有 UUID 时退回旧坐标查找，找到后一次性迁移成 UUID。
     * 这样 0.1.x 测试存档里的旧绑定在升级后会自己续上。
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

    // ------------------------------------------------------------------
    // 绑定（闪存左键存储器 → 右键本仓）
    // ------------------------------------------------------------------

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (tryLinkDataStick(aPlayer)) return true;
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
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

        // 旧协议：闪存里只有坐标（升级兼容）
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
                storage.registerHatch(true, BeamVoidLink.pack(base.getXCoord(), base.getYCoord(), base.getZCoord()));
            }
            base.markDirty();
        }
    }

    /** 旧协议：绑坐标（升级兼容；跑到一次成功的查找就会自动迁移成 UUID）。 */
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
                storage.registerHatch(true, BeamVoidLink.pack(base.getXCoord(), base.getYCoord(), base.getZCoord()));
            }
            base.markDirty();
        }
    }

    private void clearLink() {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            final MTEBeamVoidStorage storage = findStorage();
            if (storage != null) {
                storage.unregisterHatch(true, BeamVoidLink.pack(base.getXCoord(), base.getYCoord(), base.getZCoord()));
            }
            base.markDirty();
        }
        bound = false;
        storageId = null;
        coordFallback = false;
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
        return storage.getBaseMetaTileEntity()
            .getWorld().provider.dimensionId == linkDim && base.getXCoord() == linkX
            && base.getYCoord() == linkY
            && base.getZCoord() == linkZ;
    }

    @Override
    public boolean isVoidInputHatch() {
        return true;
    }

    @Override
    public String getGuiTitle() {
        return getLocalName();
    }

    // ------------------------------------------------------------------
    // 读数（界面 / 扫描仪）
    // ------------------------------------------------------------------

    public boolean isBound() {
        return bound;
    }

    public int getLastParticleId() {
        return lastParticleId;
    }

    public int getLastRate() {
        return lastRate;
    }

    public float getLastEnergy() {
        return lastEnergy;
    }

    public float getLastFocus() {
        return lastFocus;
    }

    public long getTotalReceived() {
        return totalReceived;
    }

    public String describeLink() {
        if (!bound) return "未绑定：闪存左键存储器 → 右键本仓";
        if (storageId != null) {
            return "已绑定存储器 #" + storageId.toString()
                .substring(0, 8) + "（UUID，可随存储器搬家）";
        }
        return "已绑定 @ " + linkX + ", " + linkY + ", " + linkZ + "（旧坐标绑定，会自动迁移）";
    }

    public String describeStatus() {
        if (!bound) return "未绑定（按原版出束仓工作，会往管道推）";
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return "无方块实体";
        if (findStorage() == null) {
            return "存储器未加载 / 已被拆";
        }
        if (lastParticleId < 0) return "已绑定，等待束流";
        if (lastReceiveTick >= 0L && base.getWorld()
            .getTotalWorldTime() - lastReceiveTick > 40L) {
            return "已绑定，最近 2 秒没有新束流";
        }
        return "收束中";
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
        final NBTTagCompound last = new NBTTagCompound();
        last.setInteger("particle", lastParticleId);
        last.setInteger("rate", lastRate);
        last.setFloat("energy", lastEnergy);
        last.setFloat("focus", lastFocus);
        last.setLong("tick", lastReceiveTick);
        aNBT.setTag(NBT_LAST, last);
        aNBT.setLong(NBT_TOTAL, totalReceived);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        fillAllAccepted();
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
        if (aNBT.hasKey(NBT_LAST)) {
            final NBTTagCompound last = aNBT.getCompoundTag(NBT_LAST);
            lastParticleId = last.getInteger("particle");
            lastRate = last.getInteger("rate");
            lastEnergy = last.getFloat("energy");
            lastFocus = last.getFloat("focus");
            lastReceiveTick = last.getLong("tick");
        }
        totalReceived = aNBT.getLong(NBT_TOTAL);
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
        return new BeamVoidInputGui(this).build(guiData, syncManager, uiSettings);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { EnumChatFormatting.BLUE + getLocalName() + EnumChatFormatting.RESET, describeLink(),
            "最近粒子：" + (lastParticleId < 0 ? "-" : MTEBeamVoidStorage.particleName(lastParticleId)),
            String.format(Locale.ROOT, "最近通量：%,d /s", lastRate), String.format(Locale.ROOT, "累计入库：%,d", totalReceived),
            "状态：" + describeStatus() };
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.input.0"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.input.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.input.2"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.input.3"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.input.4") };
    }

    /** 不接普通物品/流体管道。 */
    @Override
    public boolean isFluidInputAllowed(net.minecraftforge.fluids.FluidStack aFluid) {
        return false;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }

    /** 管道连接：绑定后不连（本仓不再当管道终点用）。 */
    @Override
    public boolean canConnect(ForgeDirection side) {
        return !bound && super.canConnect(side);
    }
}
