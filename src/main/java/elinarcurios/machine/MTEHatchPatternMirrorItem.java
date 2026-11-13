package elinarcurios.machine;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_INPUT_HATCH;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_INPUT_HATCH_ACTIVE;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

import elinarcurios.gui.MirrorGui;
import elinarcurios.mirror.PatternMirrorSource;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IHatchWatcher;
import gregtech.common.tileentities.machines.ISmartInputHatch;

/**
 * 装配线样板分配接口（物品）—— 贴在装配线的<b>输入总线位</b>（每薄片底部中央）。
 *
 * <p>
 * <b>★ 为什么不实现 {@code IDualInputHatch}</b>：{@code MTEMultiBlockBase.addInputBusToMachineList}
 * 会先判 {@code instanceof IDualInputHatch} 并把它塞进 {@code mDualInputHatches}，而装配线的取料逻辑
 * 只遍历 {@code mInputBusses}（见 {@code GTRecipe.RecipeAssemblyLine#getItemConsumptionAmountArray}）
 * ——那样这个接口就永远读不到了（现成 CRIB 挂在装配线上正是这个症状）。本类只继承
 * {@link MTEHatchInputBus}，于是会走 {@code mInputBusses.add(hatch)} 分支。
 *
 * <p>
 * <b>★ 零搬运</b>：本接口自身不存材料（库存 0 格）。{@link #getFirstStack()} 直接把<b>总成内部</b>
 * 第 {@code slotIndex} 格缓冲的堆交出去；装配线消耗时执行 {@code item.stackSize -= n}，
 * 改的就是总成里的那个对象 —— 不需要任何物品搬运，也不存在"投递到一半失败"的事务问题。
 */
public class MTEHatchPatternMirrorItem extends MTEHatchInputBus implements IPatternMirror, ISmartInputHatch {

    /** 格号上限：镜像方案下不按装配线 16 格写死，留足余量（真正有几格由样板决定，界面上显示）。 */
    public static final int MAX_SLOT = 64;

    private static final String NBT_MASTER = "alap.master";
    private static final String NBT_SLOT = "alap.slotIndex";

    private int masterX;
    private int masterY;
    private int masterZ;
    private boolean masterSet = false;
    /** 绑定的格号（1-based）：对应 {@code recipe.mInputs[slotIndex - 1]}。 */
    private int slotIndex = 1;

    public MTEHatchPatternMirrorItem(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            0,
            new String[] { EnumChatFormatting.GRAY + "装配线自动化三件套之一：占装配线的一条输入总线位。",
                EnumChatFormatting.GRAY + "用闪存先左键【装配线样板总成】、再右键本方块即可绑定。",
                EnumChatFormatting.GRAY + "绑定后本接口只对外提供总成里「物品第 N 格」的材料。",
                EnumChatFormatting.DARK_GRAY + "本接口不存材料（零搬运视图），不接管道。" });
        disableSort = true;
    }

    /** 反序列化 / 客户端重建用。 */
    public MTEHatchPatternMirrorItem(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
        disableSort = true;
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchPatternMirrorItem(mName, mTier, mDescriptionArray, mTextures);
    }

    // ------------------------------------------------------------------
    // 绑定
    // ------------------------------------------------------------------

    /**
     * 转发给方块自身的 {@code BaseMetaTileEntity#markDirty()} —— 真正干活的是它
     * （{@code MetaTileEntity#markDirty()} 在运行时 jar 上不可直接调用）。
     */
    private void markTileDirty() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null) base.markDirty();
    }

    public boolean isBound() {
        return masterSet;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int index) {
        this.slotIndex = Math.max(1, Math.min(MAX_SLOT, index));
        markTileDirty();
    }

    public void bindMaster(int x, int y, int z) {
        this.masterX = x;
        this.masterY = y;
        this.masterZ = z;
        this.masterSet = true;
        markTileDirty();
    }

    /** 每 tick / 每次取用都按坐标现查，总成被拆掉时自然失效（无需额外的失效通知）。 */

    // ------------------------------------------------------------------
    // 零搬运视图
    // ------------------------------------------------------------------

    /**
     * 装配线读"这条总线里的第一个非空堆"（{@code CommonMetaTileEntity#getFirstStack}），
     * 消耗时直接对该堆做 {@code stackSize -= n}。所以这里返回总成内部缓冲的<b>引用</b>即可全链路打通。
     */
    /**
     * ★ 外观：直接照抄 ME 输入总线（`MTEHatchInputBusME`）——"伪装成 ME 输入总线"就是覆写这两个方法，
     * 基座贴图由 GT 基类按机壳等级给，我们只负责挂上那两个 ME 覆盖层。
     */
    /**
     * Report ONE virtual slot so aggregating multiblocks can see the material too.
     *
     * <p>
     * The assembly line reads each bus one by one via getFirstStack(); but ordinary multiblocks
     * (MTEMultiBlockBase#getStoredInputsForColor) iterate
     * getBaseMetaTileEntity().getSizeInventory()/getStackInSlot(i) to aggregate every bus -- a 0-slot
     * mirror would be completely invisible to them. Both paths must hand out the SAME reference inside
     * the master, so whichever path the machine takes, consuming it mutates the master's stack.
     */
    @Override
    public int getSizeInventory() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int aIndex) {
        if (aIndex != 0) return null;
        return getFirstStack();
    }

    // ------------------------------------------------------------------
    // 变化通知：GT5U 的配方检查是"纯事件驱动"的（靠 scheduleRecipeCheck push，
    // doPeriodicChecks 默认 false），而本镜像的库存是虚拟的、GT 看不到它的变化 ——
    // 所以必须自己在料到位的瞬间戳一次控制器，否则装配线不会启动。
    // ------------------------------------------------------------------

    /** 控制器在结构检查时通过 {@code MTEMultiBlockBase.addIfSmartInput} 注册进来。 */
    private final List<IHatchWatcher> watchers = new ArrayList<>();

    @Override
    public void addWatcher(IHatchWatcher watcher) {
        if (watcher != null && !watchers.contains(watcher)) watchers.add(watcher);
    }

    @Override
    public void removeWatcher(IHatchWatcher watcher) {
        watchers.remove(watcher);
    }

    /** 兜底：万一某次变化没 push 到，控制器最多 100 tick（5 秒）也会自查一次。 */
    @Override
    public boolean needsPeriodicChecks() {
        return true;
    }

    /** 上次看到的"总成里第 k 格"那一堆（引用 + 数量），用于判断有没有实质变化。 */
    private ItemStack lastSeenItem;

    private int lastSeenItemSize = -1;

    /** 最近一次供料命中的样板槽序号（0 = 未命中），只用于扫描仪信息显示。 */
    private int lastResolvedSlot;

    /**
     * AAL 的批次间隙里 currentRecipe 会被清空（checkProcessing 开头 clearCurrentRecipe），
     * 而她的 AAL 大电流超频后一个配方不到 1 秒、批次接连不断，所以检查阶段占比很高。
     * 缓存最近一次真实配方并短暂沿用，让批次边界的检查也按“配方对应的槽”读，而不是退回“第一个有料槽”。
     */
    private static final int RECIPE_GRACE_TICKS = 20;

    private Object lastKnownRecipe;

    private long lastKnownRecipeTick = -1;

    private Object recipeFor(long now) {
        Object recipe = PatternMirrorSource.currentRecipeOf(watchers);
        if (recipe != null) {
            lastKnownRecipe = recipe;
            lastKnownRecipeTick = now;
            return recipe;
        }
        if (lastKnownRecipe != null && now - lastKnownRecipeTick <= RECIPE_GRACE_TICKS) {
            return lastKnownRecipe;
        }
        return null;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        // 低频轮询就够：AE 推料是成批到达的，5 tick 的粒度感觉不到差别。
        if ((aTick % 5) != 0) return;
        if (!masterSet || watchers.isEmpty()) return;
        World world = aBaseMetaTileEntity.getWorld();
        if (world == null) return;

        PatternMirrorSource.SlotView view = PatternMirrorSource
            .resolve(world, masterX, masterY, masterZ, false, slotIndex, true, recipeFor(world.getTotalWorldTime()));
        boolean changed;
        if (view.heldItem == null) {
            changed = lastSeenItem != null;
            lastSeenItem = null;
            lastSeenItemSize = -1;
        } else {
            // 零搬运下引用通常不变、数量会变，两者都要比。
            changed = view.heldItem != lastSeenItem || view.heldItem.stackSize != lastSeenItemSize;
            lastSeenItem = view.heldItem;
            lastSeenItemSize = view.heldItem.stackSize;
        }
        if (!changed) return;
        for (IHatchWatcher watcher : watchers) {
            try {
                watcher.scheduleRecipeCheckImmediate();
            } catch (Throwable ignored) {
                // 控制器可能已被拆掉：忽略即可，等下次结构检查重新注册。
            }
        }
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_ME_INPUT_HATCH_ACTIVE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_ME_INPUT_HATCH) };
    }

    /**
     * ★ 零搬运供料：把总成样板槽里"第 slotIndex 格物品"那一堆的**引用**交出去。
     * 装配线消耗时执行 {@code stackSize -= n}，改的就是总成材料袋里那一堆，全程不搬运、不拷贝。
     */
    @Override
    public ItemStack getFirstStack() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return null;
        lastReadTick = base.getWorld()
            .getTotalWorldTime();
        if (!masterSet) return null;
        PatternMirrorSource.SlotView view = PatternMirrorSource.resolve(
            base.getWorld(),
            masterX,
            masterY,
            masterZ,
            false,
            slotIndex,
            true,
            recipeFor(
                base.getWorld()
                    .getTotalWorldTime()));
        lastResolvedSlot = view.slotOrdinal;
        return view.heldItem;
    }

    // ------------------------------------------------------------------
    // 不存材料、不接管道（与 GT 的 CRAFTING INPUT PROXY 同款约束）
    // ------------------------------------------------------------------

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    // ------------------------------------------------------------------
    // 持久化与信息显示
    // ------------------------------------------------------------------

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger(NBT_SLOT, slotIndex);
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
        super.loadNBTData(aNBT);
        slotIndex = Math.max(1, aNBT.getInteger(NBT_SLOT));
        if (aNBT.hasKey(NBT_MASTER)) {
            NBTTagCompound master = aNBT.getCompoundTag(NBT_MASTER);
            masterX = master.getInteger("x");
            masterY = master.getInteger("y");
            masterZ = master.getInteger("z");
            masterSet = true;
        }
        if (slotIndex > MAX_SLOT) {
            slotIndex = MAX_SLOT;
        }
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }

    @Override
    public String[] getInfoData() {
        String binding = masterSet ? EnumChatFormatting.GREEN + "已绑定总成 @ " + masterX + ", " + masterY + ", " + masterZ
            : EnumChatFormatting.RED + "未绑定总成";
        String readState = lastReadTick < 0 ? EnumChatFormatting.DARK_GRAY + "装配线尚未读取本接口"
            : EnumChatFormatting.GREEN + "装配线正在读取本接口";
        boolean byRecipe = PatternMirrorSource.currentRecipeOf(watchers) != null;
        String slotInfo = lastResolvedSlot > 0
            ? EnumChatFormatting.GREEN + "样板槽：第 "
                + lastResolvedSlot
                + " 号"
                + (byRecipe ? "（按当前配方匹配）" : "")
                + EnumChatFormatting.RESET
            : EnumChatFormatting.DARK_GRAY + "样板槽：未命中" + EnumChatFormatting.RESET;
        return new String[] { EnumChatFormatting.BLUE + getLocalName() + EnumChatFormatting.RESET,
            "绑定：" + binding + EnumChatFormatting.RESET, "格位：物品第 " + slotIndex + " 格（上限 " + MAX_SLOT + "）", slotInfo,
            "供料：" + describeProvided(), "读取状态：" + readState + EnumChatFormatting.RESET };
    }

    // ==================================================================
    // 闪存绑定：先左键【装配线样板总成】把坐标写进闪存，再右键本接口完成绑定。
    // 交互与校验完全照 GT5U 自家 CRIB 的"镜像代理"（MTEHatchCraftingInputSlave#tryLinkDataStick）。
    // ==================================================================

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (tryLinkDataStick(aPlayer)) return true;
        if (aBaseMetaTileEntity.isServerSide()) openGui(aPlayer);
        return true;
    }

    /** 自动探测时的扫描半径：装配线最长 16 片，控制器在第 1 片第 3 层，20 格足够覆盖。 */
    private static final int SCAN_RADIUS = 20;

    /**
     * 自动识别"本接口在所属装配线里是第几条总线 / 第几个输入仓"，让格号不必靠玩家数格子。
     * （芷若原话：「我就想是否可以实现分配接口自己去识别」）
     *
     * <p>
     * 做法：以自身为中心扫一个包围盒找装配线控制器，再读它的 {@code mInputBusses} /
     * {@code mInputHatches} 取 {@code indexOf(this)}。只在**绑定时跑一次**（不是每 tick），
     * 41×41×9 次 {@code getTileEntity} 约 1~2 ms，开销可忽略。
     *
     * @return 0-based 下标；-1 = 没探到（装配线未成型 / 不在范围内 / 本接口没被采纳）
     */
    private int detectBusIndex() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return -1;
        World world = base.getWorld();
        int ox = base.getXCoord();
        int oy = base.getYCoord();
        int oz = base.getZCoord();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    TileEntity te = world.getTileEntity(ox + dx, oy + dy, oz + dz);
                    if (!(te instanceof IGregTechTileEntity)) continue;
                    IMetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
                    if (!(mte instanceof MTEMultiBlockBase)) continue;
                    MTEMultiBlockBase machine = (MTEMultiBlockBase) mte;
                    int itemIdx = machine.mInputBusses.indexOf(this);
                    if (itemIdx >= 0) return itemIdx;
                    int fluidIdx = machine.mInputHatches.indexOf(this);
                    if (fluidIdx >= 0) return fluidIdx;
                }
            }
        }
        return -1;
    }

    private boolean tryLinkDataStick(EntityPlayer aPlayer) {
        ItemStack dataStick = aPlayer.inventory.getCurrentItem();
        if (dataStick == null || !ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) return false;
        NBTTagCompound nbt = dataStick.stackTagCompound;
        if (nbt == null) return false;

        // ★ 两种样板总成的闪存协议不同，都必须认（坐标由总成自己写，本方块只负责读）：
        // 原版 CRIB（36 格） → type = "CraftingInputBuffer" + x / y / z
        // GTNL 的 360 格总成 → typeSuper = "SuperCraftingInputBuffer" + xSuper / ySuper / zSuper
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
        boolean ok = masterSet;
        if (ok) {
            // 绑定成功后顺便自动识别格号：装配线已成型且本接口已贴上去时，玩家就不用再数格子了。
            int index = detectBusIndex();
            if (index >= 0) {
                setSlotIndex(index + 1);
                aPlayer.addChatMessage(new ChatComponentTranslation("alap.chat.auto_index", index + 1));
            }
        }
        GTUtility.sendChatTrans(aPlayer, ok ? "alap.chat.linked" : "alap.chat.link_failed");
        return true;
    }

    /** 该接口支持的格位上限（物品 16 / 流体 4，对应装配线容器数量的上限）。 */
    public int getMaxSlot() {
        return MAX_SLOT;
    }

    /**
     * 扳手右键循环切换格位：第 1 格 → … → 第 N 格 → 回到第 1 格。
     * 用聊天栏反馈（与 GT 自家部件的"不弹界面、就地反馈"习惯一致）。
     */
    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (!getBaseMetaTileEntity().isServerSide()) return;
        int next = getSlotIndex() + 1;
        if (next > getMaxSlot()) next = 1;
        setSlotIndex(next);
        aPlayer.addChatMessage(new ChatComponentTranslation("alap.chat.slot_set", next));
    }

    // ---- IPatternMirror 契约 + 配置界面（物品版与流体版共用 MirrorGui）----

    @Override
    public int[] getBoundCoords() {
        if (!masterSet) return null;
        return new int[] { masterX, masterY, masterZ };
    }

    // ---- 诊断：装配线最近一次读取本接口的时间（世界总时间；-1 = 从未被读）----
    // 联调时一眼判断"接口到底有没有被装配线采纳"：被读过 = 结构位置与格号都对上了。
    // 每 tick 装配线做配方检查时都会调 getFirstStack()/getFillableStack()，所以这个值很新鲜。

    private long lastReadTick = -1L;

    public long getLastReadTick() {
        return lastReadTick;
    }

    @Override
    public String getProvidedText() {
        String base = describeProvided();
        if (base == null || base.isEmpty()) return "";
        // 只在"装配线还没读过本接口"时才附加这句 —— 正常读取时不显示，减少噪音。
        if (lastReadTick < 0) {
            return base + "（装配线尚未读取本接口）";
        }
        return base;
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
        return new MirrorGui(this, this).build(guiData, syncManager, uiSettings);
    }

    /**
     * 当前这一格"镜像"到了什么 —— GUI 与扫描仪共用（GUI 侧走同步值，服务端算好推给客户端）。
     *
     * <p>
     * 数据全部来自 {@link PatternMirrorSource}：它指出"该格在样板里是什么"（requiredItem）与
     * "总成材料袋里现在有多少"（heldItem，即真正会交给装配线的那一堆）。
     */
    /**
     * 当前这一格"镜像"到了什么 —— GUI 与扫描仪共用（GUI 侧走同步值，服务端算好推给客户端）。
     *
     * <p>
     * 数据全部来自 {@link PatternMirrorSource}：它指出"该格在样板里是什么"（requiredItem）与
     * "总成材料袋里现在有多少"（heldItem，即真正会交给装配线的那一堆）。
     *
     * <p>
     * 末尾还会追加一条**格位映射自检**（只在"产物确实是装配线配方、但第 k 个物品与配方第 k 格不符"
     * 时才出现）—— 这样不搭装配线也能判断格号该填几。
     */
    /**
     * 界面 / 扫描仪上显示的一行：**只报总成实际发配到这一格的数量**。
     *
     * <p>
     * ★ 2026-10-13 按芷若的实机反馈改：不再显示"这一格应该是哪个物品"。那个值是从**样板**读出来的
     * **期望值**，样板还没被 AE 供料时显示它，会让人以为已经在供料了（她的原话：「样板都还没生效
     * 就读取物品了，我觉得有点误导」）。现在只报实际数量：有料就报"名字 ×数量"，没料就报"无"。
     */
    private String describeProvided() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return "";
        if (!masterSet) return "未绑定总成";

        PatternMirrorSource.SlotView view = PatternMirrorSource.resolve(
            base.getWorld(),
            masterX,
            masterY,
            masterZ,
            false,
            slotIndex,
            false,
            recipeFor(
                base.getWorld()
                    .getTotalWorldTime()));
        if (view.heldItem == null) {
            // 绑定目标不对：把原因说出来；格号超出样板格数：也说清楚；其余就是"这一格现在没有料"。
            if (view.note != null && view.note.startsWith("绑定目标")) return view.note;
            if (!view.hasRequirement()) return "无（样板没有第 " + slotIndex + " 个物品）";
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(view.heldItem.getDisplayName())
            .append(" ×")
            .append(view.heldItem.stackSize);
        String mismatch = PatternMirrorSource.checkRecipeMapping(base.getWorld(), masterX, masterY, masterZ, slotIndex);
        if (mismatch != null && !mismatch.isEmpty()) {
            sb.append("  ⚠ ")
                .append(mismatch);
        }
        return sb.toString();
    }

    /** 供 GUI / 调试读；与 {@link ALAPMachines} 里的名字常量保持一致。 */

}
