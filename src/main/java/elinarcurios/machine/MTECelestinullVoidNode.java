package elinarcurios.machine;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAYS_ENERGY_ON_WIRELESS;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import elinarcurios.gui.CelestinullVoidNodeGui;
import elinarcurios.util.ECFormat;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.common.misc.WirelessNetworkManager;

/**
 * 天玄虚空能量节点（celestinull）—— ElinarCurios 首台单方块 GT 机器。
 *
 * <p>
 * 六面进电，把导线送来的电能攒在内部，再一次性倾泻进 GT 的全局无线电网。
 * 两条倾倒机制：①攒满即冲（主）②每 100 秒兜底（服务低压输入阶段）。
 *
 * <p>
 * 参数（2026-09-12 芷若拍板）：电压上限 {@link Integer#MAX_VALUE}、电流上限 200 万安、
 * 单档 tier 14(MAX)、容量 8.59e18 EU（= 一整周期满值）。容量必须满足
 * {@code maxEUStore() + maxEUInput() * maxAmperesIn() <= Long.MAX_VALUE} —— GT 的
 * {@code BaseMetaTileEntity.injectEnergyUnits} 用 long 做 {@code 电压 * 电流}，且带
 * {@code aIgnoreTooMuchEnergy = true} 直接累加，越界会静默翻负、机器报废。
 */
public class MTECelestinullVoidNode extends MTETieredMachineBlock {

    /** 每 tick 能吞进来的上限，同时也是水位预留的「一个整包」。 */
    public static final long MAX_EU_PER_TICK = (long) Integer.MAX_VALUE * 2_000_000L;

    /** 8.59e18 = 4.295e15 × 2000 tick，恰好是一整个 100 秒周期能灌进来的量。 */
    public static final long CAPACITY = MAX_EU_PER_TICK * 2000L;

    /** 水位：留一个整包，否则水箱差一点点满时单包塞不进、线缆里的电被卡住。 */
    public static final long WATER_MARK = CAPACITY - (long) Integer.MAX_VALUE;

    /** 兜底倾倒周期：100 秒。用 TE 自带的 aTick 取模，不自己存计数器（自带持久化、重登不重置）。 */
    public static final long PERIOD_TICKS = 2000L;

    private static final String NBT_OWNER = "celestinull.owner";
    private static final String NBT_LAST_PERIODIC = "celestinull.lastPeriodic";

    private UUID ownerUuid;
    private boolean lastFlushWasPeriodic;

    public MTECelestinullVoidNode(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0,
            new String[] { EnumChatFormatting.GRAY + "六面进电，将电能攒入内部缓存，再一次性倾泻进全局无线电网。",
                EnumChatFormatting.GRAY + "攒满即冲；若未攒满，则每 100 秒兜底倾倒一次。",
                EnumChatFormatting.DARK_PURPLE + "输入电压上限 "
                    + EnumChatFormatting.WHITE
                    + ECFormat.number(Integer.MAX_VALUE)
                    + EnumChatFormatting.DARK_PURPLE
                    + " EU/t，电流上限 "
                    + EnumChatFormatting.WHITE
                    + ECFormat.number(2_000_000L)
                    + EnumChatFormatting.DARK_PURPLE
                    + " A。",
                EnumChatFormatting.GRAY + "缓存上限 "
                    + EnumChatFormatting.WHITE
                    + ECFormat.compact(CAPACITY)
                    + EnumChatFormatting.GRAY
                    + " EU；电量计入摆放者所属队伍的无线电网。",
                EnumChatFormatting.RED + "警告：输入电压超过上限会直接爆炸；缓存满时导线里的电会被卡住。" });
    }

    /** 反序列化 / 客户端重建用，字段语义必须与上方保持一致。 */
    public MTECelestinullVoidNode(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECelestinullVoidNode(mName, mTier, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 电量契约
    // ------------------------------------------------------------------

    @Override
    public boolean isEnetInput() {
        return true;
    }

    @Override
    public boolean isEnetOutput() {
        return false;
    }

    /** 六个面全部允许进电。 */
    @Override
    public boolean isInputFacing(ForgeDirection side) {
        return true;
    }

    @Override
    public boolean isOutputFacing(ForgeDirection side) {
        return false;
    }

    @Override
    public long maxEUInput() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long maxAmperesIn() {
        return 2_000_000L;
    }

    @Override
    public long maxEUStore() {
        return CAPACITY;
    }

    @Override
    public long maxEUOutput() {
        return 0;
    }

    @Override
    public long maxAmperesOut() {
        return 0;
    }

    @Override
    public long getMinimumStoredEU() {
        return 0;
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    /**
     * 右键开 GUI。
     *
     * <p>
     * <b>踩过的坑（2026-09-12 芷若实测「右键打不开」）</b>：{@code CommonMetaTileEntity.onRightclick}
     * 的基类实现是 <b>{@code return false;}</b>（什么都不做）—— GT5U 里每个有 GUI 的机器都自己覆写它
     * 调 {@code openGui(player)}。只写 {@code useMui2()/buildUI()} 是不够的，GUI 根本不会被触发。
     */
    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (aBaseMetaTileEntity.isServerSide()) {
            openGui(aPlayer);
        }
        return true;
    }

    // ★ 注意（2026-09-13 对齐运行时 GT5U 5.09.51.482 后发现）：
    // 这两个方法在 5.09.51 的 GT5U 里**都不存在**（`supportsInventoryRow()` / `doesBindPlayerInventory()`
    // 是 5.09.54 才加进 `MTETieredMachineBlockBaseGui` 体系的）。本 mod 编译期已对齐运行时版本，
    // 覆写它们会报「方法不会覆盖或实现超类型的方法」，**已删除**。
    // 本机器的 GUI 走 `buildUI(...)` 自建面板（见 CelestinullVoidNodeGui），不依赖那套基类。

    // 无物品栏的机器：槽位一律拒绝。
    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        net.minecraft.item.ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        net.minecraft.item.ItemStack aStack) {
        return false;
    }

    // ISidedInventory 抽象方法：本机没有物品栏，一律拒绝/返回空（5.09.51 的 MTETieredMachineBlock 仍要求实现）。
    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        return new int[0];
    }

    @Override
    public boolean canInsertItem(int index, net.minecraft.item.ItemStack itemStack, int direction) {
        return false;
    }

    @Override
    public boolean canExtractItem(int index, net.minecraft.item.ItemStack itemStack, int direction) {
        return false;
    }

    @Override
    public boolean isItemValidForSlot(int index, net.minecraft.item.ItemStack itemStack) {
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

    // ↓ 以下是 5.09.51 里 MTETieredMachineBlock **未提供默认实现**的 IInventory 成员（5.09.54 由基类代劳）。
    // 本机没有任何物品栏，全部按「零槽位」实现。
    @Override
    public int getSizeInventory() {
        return 0;
    }

    @Override
    public net.minecraft.item.ItemStack getStackInSlot(int slot) {
        return null;
    }

    @Override
    public net.minecraft.item.ItemStack decrStackSize(int index, int count) {
        return null;
    }

    @Override
    public net.minecraft.item.ItemStack getStackInSlotOnClosing(int index) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int index, net.minecraft.item.ItemStack stack) {
        // 零槽位，忽略。
    }

    /**
     * {@code markDirty()} 在运行时 GT5U 5.09.51 里是**抽象方法**（5.09.54 由基类代劳了，所以先前不用写）。
     * 这里转发给方块自身的 {@code BaseMetaTileEntity#markDirty()} —— 真正干活的是它
     * （标脏 chunk、触发存档），MTE 侧只是 IInventory 的协议入口。
     */
    @Override
    public void markDirty() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) {
            base.markDirty();
        }
    }

    // IInventory 余下的抽象方法（5.09.51 的 MTETieredMachineBlock 未提供默认实现，必须自己写）。
    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    /** 标准距离判定（与 GT 其它机器一致）：玩家离方块不超过 8 格且可交互。 */
    @Override
    public boolean isUseableByPlayer(net.minecraft.entity.player.EntityPlayer player) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return false;
        if (base.getWorld() == null) return false;
        if (base.getWorld()
            .getTileEntity(base.getXCoord(), base.getYCoord(), base.getZCoord()) != base) return false;
        return player.getDistanceSq(base.getXCoord() + 0.5D, base.getYCoord() + 0.5D, base.getZCoord() + 0.5D) <= 64.0D;
    }

    // ------------------------------------------------------------------
    // NBT
    //
    // GT5U 本体的机器靠它自己的注解处理器自动生成 save/loadNBTData，那个处理器不随发布 jar 提供，
    // 所以 addon 必须手写。这里只需要保住归属 UUID 与「上一次是哪条机制触发的」。
    // 电量本身由 BaseMetaTileEntity 的 mStoredEnergy 自动持久化，不用在这里存。
    // ------------------------------------------------------------------

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        // 注意：父链上这两个方法是抽象的，不能调 super.saveNBTData()（会编译报错
        // 「无法直接访问抽象方法」）。电量由 BaseMetaTileEntity.mStoredEnergy 自行持久化。
        if (ownerUuid != null) {
            aNBT.setString(NBT_OWNER, ownerUuid.toString());
        }
        aNBT.setBoolean(NBT_LAST_PERIODIC, lastFlushWasPeriodic);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        ownerUuid = parseUuid(aNBT.getString(NBT_OWNER));
        lastFlushWasPeriodic = aNBT.getBoolean(NBT_LAST_PERIODIC);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 贴图：MAX 档机器外壳 + 无线能量覆盖层。colorIndex 需 +1 才是外壳数组的索引。
    // ------------------------------------------------------------------

    @Override
    public ITexture[][][] getTextureSet(ITexture[] aTextures) {
        // 实际渲染完全走下面的 getTexture(colorIndex)，这里只需给出合法的非空数组。
        ITexture[][][] textures = new ITexture[ForgeDirection.VALID_DIRECTIONS.length][1][];
        for (int side = 0; side < textures.length; side++) {
            textures[side][0] = new ITexture[] { MACHINE_CASINGS[mTier][0] };
        }
        return textures;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture casing = MACHINE_CASINGS[mTier][Math.min(colorIndex + 1, 16)];
        ITexture overlay = OVERLAYS_ENERGY_ON_WIRELESS[mTier + 1];
        return new ITexture[] { casing, overlay };
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        ownerUuid = resolveOwner(aBaseMetaTileEntity);
        flushToNetwork();
    }

    @Override
    public void onPreTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPreTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        long stored = getEUVar();
        if (stored <= 0) return;

        // 攒满即冲优先；未满则每 100 秒兜底一次（兜底是为了低压阶段也能用）。
        if (stored >= WATER_MARK) {
            lastFlushWasPeriodic = false;
            flushToNetwork();
        } else if (aTick % PERIOD_TICKS == 0L) {
            lastFlushWasPeriodic = true;
            flushToNetwork();
        }
    }

    /**
     * 把内部缓存整体灌进无线电网。
     *
     * <p>
     * 顺序不能反：先让网络记账，成功了才清空本地。{@code addEUToGlobalEnergyMap} 的语义是
     * 「会让余额变负就整体拒绝并返回 false」，所以不存在半扣；失败时电量留在本地，下个 tick 重试。
     */
    public void flushToNetwork() {
        if (ownerUuid == null) return;
        long stored = getEUVar();
        if (stored <= 0) return;
        if (WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUuid, stored)) {
            setEUVar(0);
        }
    }

    /** 归属：方块记录摆放者 UUID，网络按队伍归集（同队共用一张网）。 */
    private UUID resolveOwner(IGregTechTileEntity tile) {
        UUID uuid = tile.getOwnerUuid();
        if (uuid == null) {
            uuid = WirelessNetworkManager.processInitialSettings(tile);
        }
        if (uuid != null) {
            WirelessNetworkManager.strongCheckOrAddUser(uuid);
        }
        return uuid;
    }

    // ------------------------------------------------------------------
    // 信息显示（WAILA / 扫描仪都吃这一份）
    // ------------------------------------------------------------------

    public boolean wasLastFlushPeriodic() {
        return lastFlushWasPeriodic;
    }

    /** 距离下一次兜底倾倒还剩多少 tick（仅用于 GUI 倒计时显示）。 */
    public long getTicksUntilPeriodicFlush(long aTick) {
        long remainder = aTick % PERIOD_TICKS;
        return remainder == 0L ? PERIOD_TICKS : PERIOD_TICKS - remainder;
    }

    @Override
    public String[] getInfoData() {
        long stored = getEUVar();
        return new String[] { EnumChatFormatting.BLUE + getLocalName() + EnumChatFormatting.RESET,
            "缓存电量: " + EnumChatFormatting.GREEN
                + ECFormat.compact(stored)
                + EnumChatFormatting.RESET
                + " / "
                + EnumChatFormatting.YELLOW
                + ECFormat.compact(CAPACITY)
                + EnumChatFormatting.RESET
                + " EU",
            "输入上限: " + ECFormat.number(maxEUInput()) + " V × " + ECFormat.number(maxAmperesIn()) + " A",
            "倾倒机制: 攒满即冲 + 每 100 秒兜底" };
    }

    /** 供 GUI 判空用。 */
    public boolean hasOwner() {
        return ownerUuid != null;
    }

    // ------------------------------------------------------------------
    // GUI
    // ------------------------------------------------------------------

    /**
     * ★★ 必须同时覆写这两个方法，否则 {@code buildUI} 永远不会被调用（2026-09-13 用 2.8.4 版本锁定源码核实）。
     *
     * <p>
     * {@code CommonMetaTileEntity.openGui} 的判定是：
     * {@code if ((GTGuis.GLOBAL_SWITCH_MUI2 && useMui2()) || forceUseMui2())} → 走 MUI2；
     * {@code else} → {@code GTUIInfos.openGTTileEntityUI(...)} 走 MUI1 老界面。
     *
     * <p>
     * 而运行版 GT5U <b>5.09.51.482</b> 里 {@code GTGuis.GLOBAL_SWITCH_MUI2} 的初值是 <b>false</b>，
     * 且整个 GTNH 2.8.4 源码集里<b>没有任何地方给它赋 true</b>（MUI2 迁移未完成，GT 自家机器也全走 MUI1）。
     * 所以只写 {@code useMui2()} 完全没用 —— 必须靠 {@code forceUseMui2()} 返回 true 强制走 MUI2。
     *
     * <p>
     * 症状（之前排查十几轮的真正原因）：右键走 MUI1 老路径，本机没实现 {@code addUIWidgets}，
     * 于是「GUI 能开、里面全空、连物品栏都没有、鼠标悬停无反应」，而且<i>日志里一行异常都没有</i>——
     * 因为根本不是异常，是<b>分支走错</b>。之前所有改布局/改背景/加探针的尝试都是在改一段死代码。
     */
    @Override
    protected boolean useMui2() {
        return true;
    }

    /** 绕过 {@code GLOBAL_SWITCH_MUI2} 强制启用 MUI2（这正是该钩子的用途）。 */
    @Override
    protected boolean forceUseMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        return new CelestinullVoidNodeGui(this).build(guiData, syncManager, uiSettings);
    }
}
