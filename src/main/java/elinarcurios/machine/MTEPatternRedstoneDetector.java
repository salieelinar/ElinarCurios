package elinarcurios.machine;

import static gregtech.api.enums.Textures.BlockIcons.MACHINE_CASINGS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_REDSTONE_TRANSMITTER;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import elinarcurios.gui.PatternRedstoneDetectorGui;
import elinarcurios.mirror.PatternMirrorSource;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IHatchWatcher;
import gregtech.common.tileentities.machines.ISmartInputHatch;
import gregtech.common.tileentities.machines.RecipeCheckReason;

/**
 * 样板总成侦测器（芷若 2026-11-07 拍板）—— 1×1 工具方块，绑定一台样板输入总成（CRIB / GTNL 超级总成）后，
 * 按总成里「有没有料」发红石信号。
 *
 * <p>
 * 用途（她的原话）：「某台机器开机时候会一直消耗耗材，那这个方块检测到总成发送物品时候在发出红石信号让机器开机工作，
 * 如果方块不发出信号机器就不开机，所以其实不需要考虑合成过程中的状态」⇒ 只看「总成材料袋非空」这一个布尔量：
 * <ul>
 * <li>有料 → 六面弱充能 15；</li>
 * <li>料被清空 → 继续亮 {@link #getHoldSeconds()} 秒后熄灭（期间再来料则重新计时）。</li>
 * </ul>
 * 秒数由玩家在 GUI 里自行设置，范围 1 ~ {@link Integer#MAX_VALUE} 秒（她拍板的口径）。
 *
 * <p>
 * <b>读总成</b>：复用镜像那套 —— 坐标 {@code unwrapMaster} 出真 MTE，{@code instanceof IDualInputHatch} 后调
 * {@code getFirstNonEmptyInventory().isPresent()}。原版 CRIB 与 GTNL 的 {@code SuperCraftingInputHatchME}
 * 都实现了这个接口（后者用 javap 反编译确认过语义一致），所以不编译期引用 GTNL 类。
 *
 * <p>
 * <b>发红石</b>：GT 机器方块自带的链路 —— 覆写 {@link #allowGeneralRedstoneOutput()} 返回 true，逐面
 * {@code setOutputRedstoneSignal}（{@code BlockMachines.isProvidingWeakPower} 读取）。GT 自家的
 * {@code MTEHeatSensor} 就是同一套做法。
 *
 * <p>
 * <b>★ 区块加载坑</b>：1.7.10 的 {@code World.getTileEntity} 会经 {@code getChunkFromChunkCoords} 把区块
 * 拉起来（生成/读盘）。所以探测前必须先 {@code chunkExists} 判断；总成所在区块没加载时**不探测、也不减倒计时**，
 * 保持当前信号，避免每 5 tick 把远处总成的区块拉起来、也避免信号闪断。
 *
 * <p>
 * <b>★ 事件驱动（2026-11-07 补，修「料刚到就被机器吞掉」的漏检）</b>：只靠轮询时，材料在总成里可能只停留
 * 1 个 tick（AE 推料 → 下一 tick 机器 checkRecipe 直接扣走），5 tick 一次的采样有概率看不见。所以本方块把
 * 自己注册成总成的 {@link IHatchWatcher}：CRIB 在 {@code justHadNewItems} 时、GTNL 超级总成在
 * {@code pushPattern} 成功时都会回调 watcher（{@code scheduleRecipeCheckImmediate()}），**推料那一刻直接重置
 * 倒计时**，与轮询相位无关。轮询仍然保留：负责「手动放料」这类不触发事件的路径，以及判断总成是否还在。
 */
public class MTEPatternRedstoneDetector extends MTETieredMachineBlock implements IHatchWatcher {

    /** 默认保持 5 秒（她只定了范围，没指定默认值）。 */
    public static final int DEFAULT_HOLD_SECONDS = 5;

    /** 倒计时精度：每 5 tick 探测一次总成（0.25 秒，足够秒级手感）。 */
    private static final int PROBE_INTERVAL_TICKS = 5;

    private static final String NBT_MASTER = "elinar.detector.master";

    private static final String NBT_HOLD_SECONDS = "elinar.detector.holdSeconds";

    private static final String NBT_COUNTDOWN = "elinar.detector.countdown";

    /** 绑定的总成坐标。 */
    private int masterX;

    private int masterY;

    private int masterZ;

    private boolean masterSet = false;

    /** 清空后信号保持的秒数（1 ~ Integer.MAX_VALUE）。 */
    private int holdSeconds = DEFAULT_HOLD_SECONDS;

    /** 剩余保持 tick 数。用 long：holdSeconds 可到 Integer.MAX_VALUE，×20 用 int 会溢出。 */
    private long countdownTicks = 0L;

    /** 上一次探测结果：总成材料袋是否非空（区块未加载时保持旧值）。 */
    private boolean hasMaterial = false;

    /** 上一次探测时，绑定坐标处的方块是否还是样板总成（仅用于界面文案）。 */
    private boolean masterIsPatternHatch = false;

    /** 已注册为 watcher 的总成 MTE 实例；换绑 / 总成拆除 / 区块卸载时注销。 */
    private Object watcherHost = null;

    public MTEPatternRedstoneDetector(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0,
            new String[] { EnumChatFormatting.GRAY + "用闪存先左键【样板输入总成】、再右键本方块即可绑定。",
                EnumChatFormatting.GRAY + "总成里有料时向六个面发出红石信号 15。",
                EnumChatFormatting.GRAY + "料被清空后继续亮「信号保持秒数」再熄灭，期间再来料则重新计时。",
                EnumChatFormatting.GRAY + "右键打开界面设置信号保持秒数（1 ~ 2147483647 秒）。",
                EnumChatFormatting.DARK_GRAY + "本方块不存物品、不接管道。" });
    }

    /** 反序列化 / 客户端重建用。 */
    public MTEPatternRedstoneDetector(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEPatternRedstoneDetector(mName, mTier, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 绑定
    // ------------------------------------------------------------------

    public boolean isBound() {
        return masterSet;
    }

    public int[] getBoundCoords() {
        return masterSet ? new int[] { masterX, masterY, masterZ } : null;
    }

    /** 设置信号保持秒数（自动压到 1 ~ Integer.MAX_VALUE）。 */
    public void setHoldSeconds(int seconds) {
        this.holdSeconds = clampHoldSeconds(seconds);
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    public static int clampHoldSeconds(int seconds) {
        return Math.max(1, seconds);
    }

    public int getHoldSeconds() {
        return holdSeconds;
    }

    /** 当前是否正在发红石（界面 / 扫描仪显示用）。 */
    public boolean isEmitting() {
        return countdownTicks > 0L;
    }

    public long getCountdownTicks() {
        return countdownTicks;
    }

    public boolean isMasterChunkLoaded() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        World world = base == null ? null : base.getWorld();
        return masterSet && world != null
            && world.getChunkProvider()
                .chunkExists(masterX >> 4, masterZ >> 4);
    }

    /** 绑定成功后由闪存交互调用。 */
    public void bindMaster(int x, int y, int z) {
        detachWatcher();
        this.masterX = x;
        this.masterY = y;
        this.masterZ = z;
        this.masterSet = true;
        this.masterIsPatternHatch = false;
        this.hasMaterial = false;
        this.countdownTicks = 0L;
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    // ------------------------------------------------------------------
    // 探测 + 计时 + 红石输出（服务端每 tick）
    // ------------------------------------------------------------------

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;

        if (!masterSet) {
            detachWatcher();
            hasMaterial = false;
            masterIsPatternHatch = false;
            countdownTicks = 0L;
        } else if (!isMasterChunkLoaded()) {
            // 总成区块未加载：不探测（否则会把区块拉起来）、倒计时暂停，保持当前信号。
            // 同时注销 watcher：持有一个已卸载区块的 MTE 引用会阻止它被回收。
            detachWatcher();
        } else {
            if ((aTick % PROBE_INTERVAL_TICKS) == 0L) {
                hasMaterial = probeMaster(aBaseMetaTileEntity);
            }
            if (hasMaterial) {
                countdownTicks = (long) holdSeconds * 20L;
            } else if (countdownTicks > 0L) {
                countdownTicks--;
            }
        }

        applyRedstone(aBaseMetaTileEntity);
    }

    /** 读一次总成：任意样板槽的材料袋非空即算「有料」；顺便维护 watcher 注册。 */
    private boolean probeMaster(IGregTechTileEntity base) {
        World world = base.getWorld();
        if (world == null) {
            detachWatcher();
            return false;
        }
        Object target = PatternMirrorSource.unwrapMaster(world, masterX, masterY, masterZ);
        if (!(target instanceof IDualInputHatch)) {
            detachWatcher();
            masterIsPatternHatch = false;
            return false;
        }
        IDualInputHatch hatch = (IDualInputHatch) target;
        masterIsPatternHatch = true;
        ensureWatcher(hatch);
        try {
            return hatch.getFirstNonEmptyInventory()
                .isPresent();
        } catch (Throwable ignored) {
            // 总成可能正被拆除/刷新：当作无料，下一轮再试。
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 事件驱动：把自己注册成总成的配方检查 watcher
    // ------------------------------------------------------------------

    /**
     * 材料到达事件。
     *
     * <p>
     * 原版 CRIB 在 {@code justHadNewItems} 时（{@code MTEHatchCraftingInputME.onPostTick} 第 545-552 行）回调
     * 所有 watcher；GTNL 的超级总成在 {@code pushPattern} 成功后调 {@code notifyWatchers()}。这正是
     * 「料刚到就被机器吞掉」也能捕捉到的关键 —— 事件发生在推料那一刻，不依赖轮询采样。
     */
    @Override
    public void scheduleRecipeCheck(RecipeCheckReason reason) {
        if (!masterSet) return;
        onMaterialArrived();
    }

    /** 有料到达：直接把保持时间充满（随后即使被机器瞬间扣走，也会按这个时间继续亮）。 */
    private void onMaterialArrived() {
        countdownTicks = (long) holdSeconds * 20L;
        hasMaterial = true;
    }

    /** 保证当前总成实例上注册着我们；实例变了（区块重载 / 换绑 / 总成重建）就重新注册。 */
    private void ensureWatcher(IDualInputHatch hatch) {
        if (watcherHost == hatch) return;
        detachWatcher();
        if (!(hatch instanceof ISmartInputHatch)) return;
        try {
            ((ISmartInputHatch) hatch).addWatcher(this);
            watcherHost = hatch;
        } catch (Throwable ignored) {
            watcherHost = null;
        }
    }

    private void detachWatcher() {
        Object host = watcherHost;
        watcherHost = null;
        if (host instanceof ISmartInputHatch) {
            try {
                ((ISmartInputHatch) host).removeWatcher(this);
            } catch (Throwable ignored) {
                // 总成可能已经被拆：忽略即可。
            }
        }
    }

    @Override
    public void onBlockDestroyed() {
        detachWatcher();
        super.onBlockDestroyed();
    }

    @Override
    public void onRemoval() {
        detachWatcher();
        super.onRemoval();
    }

    /** 六面全发（她拍板：红石信号直接 6 个面都发送）；值不变时 GT 内部不会触发方块更新。 */
    private void applyRedstone(IGregTechTileEntity base) {
        byte strength = countdownTicks > 0L ? (byte) 15 : (byte) 0;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            base.setOutputRedstoneSignal(side, strength);
        }
    }

    /** GT 机器方块默认不发红石；打开这个开关后 {@code getGeneralRS} 才会返回逐面强度。 */
    @Override
    public boolean allowGeneralRedstoneOutput() {
        return true;
    }

    // ------------------------------------------------------------------
    // 贴图：机器外壳 + 红石发射器覆盖层，亮起时发光
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
        ITexture casing = MACHINE_CASINGS[mTier][Math.min(colorIndex + 1, 16)];
        ITexture overlay;
        if (redstoneLevel) {
            overlay = TextureFactory.builder()
                .addIcon(OVERLAY_REDSTONE_TRANSMITTER)
                .glow()
                .build();
        } else {
            overlay = TextureFactory.of(OVERLAY_REDSTONE_TRANSMITTER);
        }
        return new ITexture[] { casing, overlay };
    }

    // ------------------------------------------------------------------
    // 交互：闪存绑定 / 右键开界面
    // ------------------------------------------------------------------

    /**
     * 绑定协议与镜像完全一致：原版 CRIB（{@code type=CraftingInputBuffer + x/y/z}）与 GTNL 超级总成
     * （{@code typeSuper=SuperCraftingInputBuffer + xSuper/ySuper/zSuper}）两种闪存都认。
     */
    private boolean tryLinkDataStick(EntityPlayer aPlayer) {
        ItemStack dataStick = aPlayer.inventory.getCurrentItem();
        if (dataStick == null || !ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) return false;
        NBTTagCompound nbt = dataStick.stackTagCompound;
        if (nbt == null) return false;

        int x;
        int y;
        int z;
        if ("CraftingInputBuffer".equals(nbt.getString("type"))) {
            x = nbt.getInteger("x");
            y = nbt.getInteger("y");
            z = nbt.getInteger("z");
        } else if ("SuperCraftingInputBuffer".equals(nbt.getString("typeSuper"))) {
            x = nbt.getInteger("xSuper");
            y = nbt.getInteger("ySuper");
            z = nbt.getInteger("zSuper");
        } else {
            return false;
        }

        bindMaster(x, y, z);
        GTUtility.sendChatTrans(aPlayer, "elinardetector.chat.linked");
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer, ForgeDirection side,
        float aX, float aY, float aZ) {
        if (tryLinkDataStick(aPlayer)) return true;
        if (aBaseMetaTileEntity.isServerSide()) openGui(aPlayer);
        return true;
    }

    // ------------------------------------------------------------------
    // 不存物品、不接管道
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
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return false;
        if (base.getWorld()
            .getTileEntity(base.getXCoord(), base.getYCoord(), base.getZCoord()) != base) return false;
        return player.getDistanceSq(base.getXCoord() + 0.5D, base.getYCoord() + 0.5D, base.getZCoord() + 0.5D) <= 64.0D;
    }

    // ------------------------------------------------------------------
    // 持久化与信息显示
    // ------------------------------------------------------------------

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        // 注意：父链上 saveNBTData/loadNBTData 是抽象方法，不能调 super（会报「无法直接访问抽象方法」），
        // 与虚空节点同款处理。方块朝向等基座状态由 BaseMetaTileEntity 自己持久化。
        aNBT.setInteger(NBT_HOLD_SECONDS, holdSeconds);
        aNBT.setLong(NBT_COUNTDOWN, countdownTicks);
        if (masterSet) {
            NBTTagCompound master = new NBTTagCompound();
            master.setInteger("x", masterX);
            master.setInteger("y", masterY);
            master.setInteger("z", masterZ);
            aNBT.setTag(NBT_MASTER, master);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        if (aNBT.hasKey(NBT_HOLD_SECONDS)) {
            holdSeconds = clampHoldSeconds(aNBT.getInteger(NBT_HOLD_SECONDS));
        }
        countdownTicks = Math.max(0L, aNBT.getLong(NBT_COUNTDOWN));
        if (aNBT.hasKey(NBT_MASTER)) {
            NBTTagCompound master = aNBT.getCompoundTag(NBT_MASTER);
            masterX = master.getInteger("x");
            masterY = master.getInteger("y");
            masterZ = master.getInteger("z");
            masterSet = true;
        }
        if (countdownTicks > (long) holdSeconds * 20L) {
            countdownTicks = (long) holdSeconds * 20L;
        }
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        return new String[] { EnumChatFormatting.BLUE + getLocalName() + EnumChatFormatting.RESET,
            "绑定：" + describeBinding(), "信号保持：" + holdSeconds + " 秒", "状态：" + describeStatus() };
    }

    /** GUI / 扫描仪共用：绑定描述。 */
    public String describeBinding() {
        if (!masterSet) return "未绑定：闪存左键总成 → 右键本方块";
        return "已绑定总成 @ " + masterX + ", " + masterY + ", " + masterZ;
    }

    /** GUI / 扫描仪共用：当前状态一句话。 */
    public String describeStatus() {
        if (!masterSet) return "未绑定，不输出信号";
        if (!isMasterChunkLoaded()) return "总成区块未加载（保持当前信号）";
        if (!masterIsPatternHatch) return "绑定目标不是样板总成";
        if (hasMaterial) return "总成有料，信号保持";
        if (countdownTicks > 0L) {
            long seconds = (countdownTicks + 19L) / 20L;
            return "无料，剩余 " + seconds + " 秒后熄灭";
        }
        return "无料，已熄灭";
    }

    // ------------------------------------------------------------------
    // GUI
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
        return new PatternRedstoneDetectorGui(this).build(guiData, syncManager, uiSettings);
    }
}
