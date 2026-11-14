package elinarcurios.machine;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import elinarcurios.beam.BeamVoidLink;
import elinarcurios.gui.BeamVoidStorageGui;
import gregtech.api.enums.Dyes;
import gregtech.api.enums.Mods;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gtnhlanth.common.beamline.Particle;

/**
 * 束流虚空存储器 —— 本方案的核心方块（芷若 2026-11-14 定案）。
 *
 * <p>
 * 单方块，存 19 种粒子的<b>数量</b>（每种上限 {@link Long#MAX_VALUE}）。它自己<b>不产</b>粒子，
 * 只能靠"束流虚空输入仓"把束流倒进来；"束流虚空输出仓"从这里抽。
 *
 * <p>
 * <b>不存能量/焦点</b>（她明确要求）：束流本来带 energy(keV)/focus，但存储只留数量，
 * 能量/焦点由输出仓上的输入框决定（聚焦固定 99）。所以"什么能量进、什么能量出"由玩家自己配。
 *
 * <p>
 * <b>绑定</b>：闪存（GT 数据棒）左键本方块写坐标 → 右键输入仓/输出仓完成绑定；本方块只做统计
 * （把已绑仓的坐标记在自身 NBT 里，统计时验证"还在不在、绑的还是不是我"）。
 *
 * <p>
 * <b>拆除保留库存</b>（她选的方案）：{@link #setItemNBT} 把数量写进掉落物，重新放下时 GT 会把
 * 这份 NBT 灌回新方块（{@code BaseMetaTileEntity#loadMetaTileNBT}），库存不丢。
 */
public class MTEBeamVoidStorage extends MTETieredMachineBlock implements IBeamVoidHatch {

    /** 粒子种类数（GT5U 的 Particle 固定 19 种，id 0~18）。 */
    public static final int PARTICLE_COUNT = 19;

    private static final String NBT_COUNTS = "elinarBeamVoidCounts";
    private static final String NBT_INPUTS = "elinarBeamVoidInputs";
    private static final String NBT_OUTPUTS = "elinarBeamVoidOutputs";
    private static final String NBT_ID = "elinarBeamVoidStorageId";

    private static final String NBT_COUNT_ID = "id";
    private static final String NBT_COUNT_VALUE = "v";

    private static final IIconContainer BEAM_OVERLAY = Textures.BlockIcons
        .custom(Mods.GregTech.resourceDomain, "iconsets/OVERLAY_BI_ACTIVE");

    /** 19 种粒子的库存。 */
    private final long[] counts = new long[PARTICLE_COUNT];

    /** 已绑定的输入仓 / 输出仓坐标（{@link BeamVoidLink#pack}）。 */
    private final Set<Long> boundInputs = new HashSet<>();

    private final Set<Long> boundOutputs = new HashSet<>();

    /**
     * 存储器身份号：摆下时生成、拆了带在掉落物上、换地方放也不变。
     * 仓绑 UUID 而不是坐标，所以移动存储器后仓会自动重新连上（芷若 2026-11-14 的方案）。
     */
    private UUID storageId;

    public MTEBeamVoidStorage(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0,
            new String[] { EnumChatFormatting.GRAY + "存 19 种束流粒子，每种上限 9223372036854775807。",
                EnumChatFormatting.GRAY + "闪存左键本方块 → 右键输入仓 / 输出仓即可绑定。", EnumChatFormatting.GRAY + "本身不产粒子，只做收 / 存 / 发。",
                EnumChatFormatting.GRAY + "拆掉时库存会跟着掉落物走，重新放下不丢。", EnumChatFormatting.DARK_GRAY + "不存能量/焦点：输出仓上单独设。" });
    }

    /** 反序列化 / 客户端重建用。 */
    public MTEBeamVoidStorage(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEBeamVoidStorage(mName, mTier, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 身份号（UUID）与注册表
    // ------------------------------------------------------------------

    /** 取身份号；没有就现生成一个（ThreadLocalRandom，不走 SecureRandom，避免熵池卡顿）。 */
    public UUID getStorageId() {
        if (storageId == null) {
            storageId = randomId();
            markStorageDirty();
        }
        return storageId;
    }

    /** 撞号（NBT 被复制）时换一个新身份号。 */
    public UUID regenerateStorageId() {
        storageId = randomId();
        markStorageDirty();
        return storageId;
    }

    private static UUID randomId() {
        return new UUID(
            ThreadLocalRandom.current()
                .nextLong(),
            ThreadLocalRandom.current()
                .nextLong());
    }

    /** 界面 / tooltip 用的短身份号（前 8 位）。 */
    public String describeStorageId() {
        final UUID id = getStorageId();
        return id.toString()
            .substring(0, 8);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            getStorageId();
            BeamVoidStorageRegistry.register(this);
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) return;
        // 兜底自愈：万一被误注销（卸载/重建时序），每 5 秒补注册一次
        if (aTick % 100L == 0L && BeamVoidStorageRegistry.owner(getStorageId()) != this) {
            BeamVoidStorageRegistry.register(this);
        }
    }

    @Override
    public void onUnload() {
        BeamVoidStorageRegistry.unregister(this);
    }

    @Override
    public void onRemoval() {
        BeamVoidStorageRegistry.unregister(this);
        super.onRemoval();
    }

    @Override
    public void onBlockDestroyed() {
        BeamVoidStorageRegistry.unregister(this);
        super.onBlockDestroyed();
    }

    // ------------------------------------------------------------------
    // 库存读写（供两个仓调用）
    // ------------------------------------------------------------------

    public long getCount(int particleId) {
        return particleId < 0 || particleId >= PARTICLE_COUNT ? 0L : counts[particleId];
    }

    public long getTotalCount() {
        long total = 0L;
        for (long c : counts) {
            total += c;
        }
        return total;
    }

    /** 入库（饱和加法），返回实际收下的数量。 */
    public long addParticles(int particleId, long amount) {
        if (particleId < 0 || particleId >= PARTICLE_COUNT || amount <= 0L) return 0L;
        final long current = counts[particleId];
        final long sum = current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
        final long added = sum - current;
        if (added <= 0L) return 0L;
        counts[particleId] = sum;
        markStorageDirty();
        return added;
    }

    /** 扣库存；不够就一点不扣，返回 false。 */
    public boolean tryConsume(int particleId, long amount) {
        if (particleId < 0 || particleId >= PARTICLE_COUNT || amount <= 0L) return false;
        if (counts[particleId] < amount) return false;
        counts[particleId] -= amount;
        markStorageDirty();
        return true;
    }

    /** 仅供命令 / 调试：直接设定某粒子数量。 */
    public void setCount(int particleId, long value) {
        if (particleId < 0 || particleId >= PARTICLE_COUNT) return;
        counts[particleId] = Math.max(0L, value);
        markStorageDirty();
    }

    private void markStorageDirty() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    // ------------------------------------------------------------------
    // 绑定登记（仓那边每秒登记一次，幂等）
    // ------------------------------------------------------------------

    public void registerHatch(boolean input, long packedPos) {
        final Set<Long> set = input ? boundInputs : boundOutputs;
        if (set.add(packedPos)) markStorageDirty();
    }

    public void unregisterHatch(boolean input, long packedPos) {
        final Set<Long> set = input ? boundInputs : boundOutputs;
        if (set.remove(packedPos)) markStorageDirty();
    }

    /**
     * 统计绑定数：区块没加载的算"还在"（不清），加载了但已拆 / 已换方块 / 绑到别人身上的剔除。
     */
    public int countBoundHatches(boolean input) {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return 0;
        final World world = base.getWorld();
        if (world == null) return 0;

        final Set<Long> set = input ? boundInputs : boundOutputs;
        int alive = 0;
        boolean changed = false;
        for (Iterator<Long> it = set.iterator(); it.hasNext();) {
            final long packed = it.next();
            final int x = BeamVoidLink.unpackX(packed);
            final int y = BeamVoidLink.unpackY(packed);
            final int z = BeamVoidLink.unpackZ(packed);
            if (!world.blockExists(x, y, z)) {
                // 区块没加载：不能证明它没了，计入"还在"（等两个区块都加载时再剔除）
                alive++;
                continue;
            }
            final TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof IGregTechTileEntity) {
                final IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                if (mte instanceof IBeamVoidHatch) {
                    final IBeamVoidHatch hatch = (IBeamVoidHatch) mte;
                    if (hatch.isVoidInputHatch() == input && hatch.isLinkedTo(this)) {
                        alive++;
                        continue;
                    }
                }
            }
            it.remove();
            changed = true;
        }
        if (changed) markStorageDirty();
        return alive;
    }

    // ------------------------------------------------------------------
    // IBeamVoidHatch（让仓反查时能确认"绑的是我"）
    // ------------------------------------------------------------------

    @Override
    public boolean isLinkedTo(MTEBeamVoidStorage storage) {
        return storage == this;
    }

    /** 存储器是核心方块，不是仓。 */
    @Override
    public boolean isVoidInputHatch() {
        return false;
    }

    // ------------------------------------------------------------------
    // 交互：闪存左键绑定 / 右键开界面
    // ------------------------------------------------------------------

    /** 左键 + 闪存 = 把本方块的 UUID 写进闪存（芷若 2026-11-14 的绑定协议）。 */
    @Override
    public void onLeftclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        final ItemStack held = aPlayer == null ? null : aPlayer.inventory.getCurrentItem();
        if (!BeamVoidLink.isDataStick(held)) return;
        BeamVoidLink.writeUuid(held, getStorageId());
        GTUtility.sendChatTrans(aPlayer, "elinarcurios.beamvoid.chat.storage_saved");
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        return onRightclick(aBaseMetaTileEntity, aPlayer, ForgeDirection.UNKNOWN, 0.5F, 0.5F, 0.5F);
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        // 右键一律开界面（手里拿着闪存也开）：绑定只走左键，否则玩家拿着闪存就看不到库存了。
        if (aBaseMetaTileEntity.isServerSide()) openGui(aPlayer);
        return true;
    }

    // ------------------------------------------------------------------
    // 贴图：机壳 + 束流图标（有库存时发光）
    // ------------------------------------------------------------------

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        ITexture[][][] textures = new ITexture[ForgeDirection.VALID_DIRECTIONS.length][1][];
        for (int side = 0; side < textures.length; side++) {
            textures[side][0] = new ITexture[] { MACHINE_CASINGS[mTier][0] };
        }
        return textures;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        final int color = Math.max(0, Math.min(15, colorIndex));
        final ITexture casing = MACHINE_CASINGS[mTier][color + 1];
        if (aFacing == null || side != aFacing) {
            return new ITexture[] { casing };
        }
        final ITexture overlay = getTotalCount() > 0L ? TextureFactory.builder()
            .addIcon(BEAM_OVERLAY)
            .glow()
            .build() : TextureFactory.of(BEAM_OVERLAY, Dyes.getModulation(color, Dyes.MACHINE_METAL.getRGBA()));
        return new ITexture[] { casing, overlay };
    }

    // ------------------------------------------------------------------
    // 持久化（方块 NBT + 掉落物 NBT）
    // ------------------------------------------------------------------

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        writeCounts(aNBT);
        aNBT.setTag(NBT_INPUTS, writePositions(boundInputs));
        aNBT.setTag(NBT_OUTPUTS, writePositions(boundOutputs));
        if (storageId != null) aNBT.setString(NBT_ID, storageId.toString());
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        readCounts(aNBT);
        readPositions(aNBT.getTagList(NBT_INPUTS, 10), boundInputs);
        readPositions(aNBT.getTagList(NBT_OUTPUTS, 10), boundOutputs);
        storageId = parseId(aNBT.getString(NBT_ID));
    }

    /** 掉落物 NBT：GT 的 {@code BaseMetaTileEntity#getDrops} 会调这个，重新放置时再原样灌回来。 */
    @Override
    public void setItemNBT(NBTTagCompound aNBT) {
        writeCounts(aNBT);
        aNBT.setTag(NBT_INPUTS, writePositions(boundInputs));
        aNBT.setTag(NBT_OUTPUTS, writePositions(boundOutputs));
        // 身份号必须跟着掉落物走，否则换地方放之后仓就找不到它了
        aNBT.setString(NBT_ID, getStorageId().toString());
    }

    private static UUID parseId(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void writeCounts(NBTTagCompound aNBT) {
        final NBTTagList list = new NBTTagList();
        for (int id = 0; id < PARTICLE_COUNT; id++) {
            if (counts[id] <= 0L) continue;
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger(NBT_COUNT_ID, id);
            entry.setLong(NBT_COUNT_VALUE, counts[id]);
            list.appendTag(entry);
        }
        aNBT.setTag(NBT_COUNTS, list);
    }

    private void readCounts(NBTTagCompound aNBT) {
        for (int id = 0; id < PARTICLE_COUNT; id++) counts[id] = 0L;
        final NBTTagList list = aNBT.getTagList(NBT_COUNTS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound entry = list.getCompoundTagAt(i);
            final int id = entry.getInteger(NBT_COUNT_ID);
            if (id < 0 || id >= PARTICLE_COUNT) continue;
            counts[id] = Math.max(0L, entry.getLong(NBT_COUNT_VALUE));
        }
    }

    private static NBTTagList writePositions(Set<Long> positions) {
        final NBTTagList list = new NBTTagList();
        for (Long packed : positions) {
            if (packed == null) continue;
            final NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("p", packed);
            list.appendTag(entry);
        }
        return list;
    }

    private static void readPositions(NBTTagList list, Set<Long> out) {
        out.clear();
        for (int i = 0; i < list.tagCount(); i++) {
            out.add(
                list.getCompoundTagAt(i)
                    .getLong("p"));
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
        return new BeamVoidStorageGui(this).build(guiData, syncManager, uiSettings);
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { EnumChatFormatting.BLUE + getLocalName() + EnumChatFormatting.RESET,
            "总库存：" + String.format(Locale.ROOT, "%,d", getTotalCount()), "输入仓：" + countBoundHatches(true) + " 个",
            "输出仓：" + countBoundHatches(false) + " 个" };
    }

    @Override
    public String[] getDescription() {
        return new String[] {
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.storage.0"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.storage.1"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.storage.2"),
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.storage.3"),
            EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal("elinarcurios.beamvoid.desc.storage.4") };
    }

    // ------------------------------------------------------------------
    // 不存物品、不接管道（与样板总成侦测器同款）
    // ------------------------------------------------------------------

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return new int[0];
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
    public boolean isItemValidForSlot(int index, ItemStack itemStack) {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public String getInventoryName() {
        return getLocalName();
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getSizeInventory() {
        return 0;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
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
    public void markDirty() {
        markStorageDirty();
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        final IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return false;
        if (base.getWorld()
            .getTileEntity(base.getXCoord(), base.getYCoord(), base.getZCoord()) != base) return false;
        return player.getDistanceSq(base.getXCoord() + 0.5D, base.getYCoord() + 0.5D, base.getZCoord() + 0.5D) <= 64.0D;
    }

    /** 界面用：粒子名（走本 mod 的中文名表）。 */
    public static String particleName(int particleId) {
        if (particleId < 0 || particleId >= PARTICLE_COUNT) return "-";
        final Particle particle = Particle.getParticleFromId(particleId);
        return StatCollector.translateToLocal("elinarcurios.beamvoid.particle." + particle.getName());
    }
}
