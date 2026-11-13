package elinarcurios.machine;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_INPUT_FLUID_HATCH;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_ME_INPUT_FLUID_HATCH_ACTIVE;

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
import net.minecraftforge.fluids.FluidStack;

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
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IHatchWatcher;
import gregtech.common.tileentities.machines.ISmartInputHatch;

/**
 * 装配线样板分配接口（流体）—— 贴在装配线的<b>输入仓位</b>（薄片底层两侧的 {@code b} 位）。
 *
 * <p>
 * 与物品版完全对称：不实现 {@code IDualInputHatch}（否则会被 {@code addInputHatchToMachineList}
 * 分流进 {@code mDualInputHatches}，而装配线只从 {@code mInputHatches} 读流体），自身不存流体，
 * 由 {@link #getFillableStack()} 直接把总成第 {@code slotIndex} 格流体缓冲的引用交出去
 * （装配线消耗时执行 {@code fluid.amount -= n}，改的就是总成里那个对象）。
 *
 * <p>
 * 装配线流体侧同样是"第 m 个输入仓 ↔ {@code recipe.mFluidInputs[m]}"
 * （见 {@code GTRecipe.RecipeAssemblyLine#maxParallelCalculatedByInputFluids} / {@code consumeInputFluids}）。
 */
public class MTEHatchPatternMirrorFluid extends MTEHatchInput implements IPatternMirror, ISmartInputHatch {

    /** 格号上限（真正有几格由样板决定，界面上显示）。 */
    public static final int MAX_SLOT = 16;

    private static final String NBT_MASTER = "alap.master";
    private static final String NBT_SLOT = "alap.slotIndex";

    private int masterX;
    private int masterY;
    private int masterZ;
    private boolean masterSet = false;
    /** 绑定的格号（1-based）：对应 {@code recipe.mFluidInputs[slotIndex - 1]}。 */
    private int slotIndex = 1;

    public MTEHatchPatternMirrorFluid(int aID, String aName, String aNameRegional, int aTier) {
        super(
            aID,
            aName,
            aNameRegional,
            aTier,
            new String[] { EnumChatFormatting.GRAY + "装配线自动化三件套之一：占装配线的一个输入仓位。",
                EnumChatFormatting.GRAY + "用闪存先左键【装配线样板总成】、再右键本方块即可绑定。",
                EnumChatFormatting.GRAY + "绑定后本接口只对外提供总成里「流体第 M 格」的材料。",
                EnumChatFormatting.DARK_GRAY + "本接口不存流体（零搬运视图），不接管道。" });
        // MTEHatchInput 这条链上没有 disableSort（那是 MTEHatchInputBus 的字段），流体侧无需设置。
    }

    /** 反序列化 / 客户端重建用。 */
    public MTEHatchPatternMirrorFluid(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, aDescription, aTextures);
        // MTEHatchInput 这条链上没有 disableSort（那是 MTEHatchInputBus 的字段），流体侧无需设置。
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchPatternMirrorFluid(mName, mTier, mDescriptionArray, mTextures);
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

    // ------------------------------------------------------------------
    // 零搬运视图
    // ------------------------------------------------------------------

    /**
     * 装配线读流体仓走 {@code inputHatch.getFillableStack()}（{@code GTRecipe:1415}），
     * 消耗时直接对返回的 FluidStack 做 {@code amount -= n}。返回总成内部缓冲的引用即可全链路打通。
     */
    /**
     * ★ 外观：照抄 ME 输入仓（`MTEHatchInputME`）的覆盖层。
     */
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

    /** 上次看到的"总成里第 m 格流体"那一堆（引用 + 数量），用于判断有没有实质变化。 */
    private FluidStack lastSeenFluid;

    private int lastSeenFluidAmount = -1;

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
            .resolve(world, masterX, masterY, masterZ, true, slotIndex, true, recipeFor(world.getTotalWorldTime()));
        boolean changed;
        if (view.heldFluid == null) {
            changed = lastSeenFluid != null;
            lastSeenFluid = null;
            lastSeenFluidAmount = -1;
        } else {
            changed = view.heldFluid != lastSeenFluid || view.heldFluid.amount != lastSeenFluidAmount;
            lastSeenFluid = view.heldFluid;
            lastSeenFluidAmount = view.heldFluid.amount;
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
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_ME_INPUT_FLUID_HATCH_ACTIVE) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_ME_INPUT_FLUID_HATCH) };
    }

    /**
     * ★ 零搬运供料：把总成样板槽里"第 slotIndex 格流体"那一堆的**引用**交出去。
     * 装配线消耗时执行 {@code amount -= n}，改的就是总成材料袋里那一堆。
     */
    @Override
    public FluidStack getFillableStack() {
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
            true,
            slotIndex,
            true,
            recipeFor(
                base.getWorld()
                    .getTotalWorldTime()));
        lastResolvedSlot = view.slotOrdinal;
        return view.heldFluid;
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
            "绑定：" + binding + EnumChatFormatting.RESET, "格位：流体第 " + slotIndex + " 格（上限 " + MAX_SLOT + "）", slotInfo,
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
     * 数据全部来自 {@link PatternMirrorSource}：它指出"该格在样板里是什么"（requiredFluid）与
     * "总成材料袋里现在有多少"（heldFluid，即真正会交给装配线的那一堆）。
     */
    /**
     * 当前这一格"镜像"到了什么 —— GUI 与扫描仪共用（GUI 侧走同步值，服务端算好推给客户端）。
     *
     * <p>
     * 数据全部来自 {@link PatternMirrorSource}：它指出"该格在样板里是什么"（requiredFluid）与
     * "总成材料袋里现在有多少"（heldFluid，即真正会交给装配线的那一堆）。
     */
    /**
     * 界面 / 扫描仪上显示的一行：**只报总成实际发配到这一格的数量**。
     *
     * <p>
     * ★ 2026-10-13 按芷若的实机反馈改：不再显示"这一格应该是哪个流体"（那是从样板读出来的
     * **期望值**，样板还没被 AE 供料时显示它会误导）。现在只报实际数量。
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
            true,
            slotIndex,
            false,
            recipeFor(
                base.getWorld()
                    .getTotalWorldTime()));
        if (view.heldFluid == null) {
            if (view.note != null && view.note.startsWith("绑定目标")) return view.note;
            if (!view.hasRequirement()) return "无（样板没有第 " + slotIndex + " 个流体）";
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(view.heldFluid.getLocalizedName())
            .append(" ")
            .append(view.heldFluid.amount)
            .append(" mB");
        return sb.toString();
    }

}
