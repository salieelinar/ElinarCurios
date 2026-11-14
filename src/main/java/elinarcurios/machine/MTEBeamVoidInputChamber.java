package elinarcurios.machine;

import java.util.Locale;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import elinarcurios.beam.BeamVoidLink;
import elinarcurios.gui.BeamVoidInputGui;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;

/**
 * 束流虚空输入室 —— 1×1 单方块，<b>正面接束流管</b>，把管道送来的束流倒进束流虚空存储器
 * （芷若 2026-11-14 追加：「还缺了一个方块，就是单方块可工作的束流输入仓，应该叫 束流虚空输入室，
 * 直接正面接束流管，粒子收入束流虚空存储器」）。
 *
 * <p>
 * <b>为什么继承 {@link MTEHatchInputBeamline}</b>：GTNH 的束流管射线终点只认
 * {@code instanceof MTEHatchInputBeamline} 的方块（普通单方块挡在线上会被直接 return），
 * 所以这个"室"必须伪装成束流输入仓才能接住管道送来的包（和之前的束流倍流器同一套路）。
 *
 * <p>
 * 收到包后每 tick 检查一次：绑定存储器就<b>立刻入库并清空自己</b>；未绑定则把包留在自己身上只做显示
 * （这样绑定的那一刻还能把这包收进去）。19 种粒子全收、不过滤。
 */
public class MTEBeamVoidInputChamber extends MTEHatchInputBeamline implements IBeamVoidCollector, IBeamVoidHatch {

    private static final String NBT_LINK = "elinarBeamVoidLink";
    private static final String NBT_LAST = "elinarBeamVoidLast";
    private static final String NBT_TOTAL = "elinarBeamVoidTotal";

    private int linkDim;
    private int linkX;
    private int linkY;
    private int linkZ;
    private boolean bound;

    /** 绑定的存储器 UUID（新协议）；为 null 时退回旧坐标绑定。 */
    private UUID storageId;

    private boolean coordFallback;

    private int lastParticleId = -1;

    private int lastRate;

    private float lastEnergy;

    private float lastFocus;

    private long lastReceiveTick = -1L;

    private long totalReceived;

    public MTEBeamVoidInputChamber(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier);
    }

    /** 反序列化 / 客户端重建用。 */
    public MTEBeamVoidInputChamber(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEBeamVoidInputChamber(mName, mTier, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 收束：管道把包推进 dataPacket，这里收到即入库
    // ------------------------------------------------------------------

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (!aBaseMetaTileEntity.isServerSide()) return;

        final World world = aBaseMetaTileEntity.getWorld();
        if (world == null) return;

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
            } else if (bound) {
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

    /** 本方块自己管包，不参与原版输入仓的"自动清空"节奏；也不往下游推。 */
    @Override
    public void moveAround(IGregTechTileEntity aBaseMetaTileEntity) {}

    // ------------------------------------------------------------------
    // 绑定（闪存左键存储器 → 右键本方块）
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

        final UUID id = BeamVoidLink.readUuid(held);
        if (id != null) {
            setLinkUuid(id);
            GTUtility.sendChatTrans(player, "elinarcurios.beamvoid.chat.linked");
            return true;
        }

        final int[] link = BeamVoidLink.read(held);
        if (link == null) {
            GTUtility.sendChatTrans(player, "elinarcurios.beamvoid.chat.no_link");
            return true;
        }
        setLink(link[0], link[1], link[2], link[3]);
        GTUtility.sendChatTrans(player, "elinarcurios.beamvoid.chat.linked");
        return true;
    }

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
    // IBeamVoidHatch / IBeamVoidCollector
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
        return true;
    }

    @Override
    public String getGuiTitle() {
        return getLocalName();
    }

    @Override
    public boolean isBound() {
        return bound;
    }

    @Override
    public int getLastParticleId() {
        return lastParticleId;
    }

    @Override
    public int getLastRate() {
        return lastRate;
    }

    @Override
    public float getLastEnergy() {
        return lastEnergy;
    }

    @Override
    public float getLastFocus() {
        return lastFocus;
    }

    @Override
    public long getTotalReceived() {
        return totalReceived;
    }

    @Override
    public String describeLink() {
        if (!bound) return "未绑定：闪存左键存储器 → 右键本方块";
        if (storageId != null) {
            return "已绑定存储器 #" + storageId.toString()
                .substring(0, 8) + "（UUID，可随存储器搬家）";
        }
        return "已绑定 @ " + linkX + ", " + linkY + ", " + linkZ + "（旧坐标绑定，会自动迁移）";
    }

    @Override
    public String describeStatus() {
        if (!bound) return "未绑定（只显示收到的束流，不入库）";
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return "无方块实体";
        if (findStorage() == null) return "存储器未加载 / 已被拆";
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
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.chamber.0"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.chamber.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.chamber.2"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.chamber.3"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.chamber.4") };
    }

    @Override
    public boolean isFluidInputAllowed(FluidStack aFluid) {
        return false;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return false;
    }
}
