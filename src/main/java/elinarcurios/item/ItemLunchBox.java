package elinarcurios.item;

import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.event.ForgeEventFactory;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.common.Reference;
import elinarcurios.inventory.InventoryLunchBox;
import elinarcurios.network.LunchBoxEatSync;
import elinarcurios.network.OpenLunchBoxPacket;
import elinarcurios.util.LunchBoxNbt;
import elinarcurios.util.LunchBoxSelector;
import squeek.applecore.api.food.FoodValues;
import squeek.spiceoflife.foodtracker.FoodHistory;
import squeek.spiceoflife.inventory.FoodContainerInventory;
import squeek.spiceoflife.inventory.NBTInventory;
import squeek.spiceoflife.items.ItemFoodContainer;

/**
 * 午餐盒：手持右键进食的超级食物容器。
 *
 * <p>
 * 进食协议整套继承自 SpiceOfLife 的 {@link ItemFoodContainer}（盒盖 {@code Open} 状态、
 * {@code EnumAction.eat} + 32 tick 原版进食动画、{@link #onEaten} 智能选食、AppleCore 的
 * {@code IEdible} 食物值、丢出/关屏时的状态复位），存储层则整体换成 ElinarCurios 自己的
 * 多页 int 仓库（见 {@link InventoryLunchBox}）：{@value #PAGE_COUNT} 页 ×
 * {@value #SLOTS_PER_PAGE} 格，每格最多 {@link Integer#MAX_VALUE} 个。
 *
 * <p>
 * 因为 {@code getInventory()} 直接给出 2916 个槽位的扁平视图，SpiceOfLife 的选食协议天然
 * **跨全页**挑最优食物；但 2916 格逐槽跑一次递减公式太贵（实测一次进食会被 SpiceOfLife 从三处
 * 各调一次），实际走 {@link LunchBoxSelector} 的等价快速路径 + 本地缓存。
 *
 * <p>
 * 注册点必须包在 {@code Loader.isModLoaded("SpiceOfLife")} 里：本类在类加载阶段就需要 SpiceOfLife 的父类。
 */
public class ItemLunchBox extends ItemFoodContainer {

    public static ItemLunchBox instance;

    public static final String TAG_ROOT = "lunchbox";
    public static final String TAG_PAGES = "pages";
    public static final String TAG_INVENTORY = "inventory";
    public static final String TAG_SLOT = "Slot";
    public static final String TAG_INT_COUNT = "intCount";
    public static final String TAG_CURRENT_PAGE = "currentPage";
    public static final String TAG_USED = "usedSlots";
    public static final String TAG_VERSION = "contentVersion";

    public static final int SLOTS_PER_PAGE = 243;
    public static final int PAGE_COUNT = 12;
    public static final int TOTAL_SLOTS = SLOTS_PER_PAGE * PAGE_COUNT;

    /** {@link #getFoodValues} 的缓存窗口（毫秒）：tooltip/HUD 会在悬停期间反复查询。 */
    private static final long FOOD_VALUES_CACHE_MILLIS = 250;

    private ItemStack cachedFoodSource;
    private FoodValues cachedFoodValues;
    private long cachedFoodAt;

    // ---- 选食缓存 ------------------------------------------------------------------
    // SpiceOfLife 在一次进食里会从三处各调一次 getBestFoodForPlayerToEat：开始进食时的进食速度
    // （PlayerUseItemEvent.Start）、结算时的食物值（FoodEvent.GetPlayerFoodValues）、以及 onEaten
    // —— 每次都是全盒扫描。这里以「盒子内容版本 + 玩家食物历史指纹」为键缓存结果，于是同一口只算一次；
    // 顺带把那份 InventoryLunchBox 视图留住，onEaten 写回时不必再解析一遍整个 NBT。
    private InventoryLunchBox cachedBestInventory;
    private ItemStack cachedBestBox;
    private EntityPlayer cachedBestPlayer;
    private int cachedBestContent = -1;
    private int cachedBestHistory = Integer.MIN_VALUE;
    private int cachedBestSlot = -1;
    private ItemStack cachedBestFood;

    public ItemLunchBox() {
        super("elinarLunchBox", TOTAL_SLOTS);
        // 覆盖父类在构造器里设的 spiceoflife 命名与创造页签。
        setUnlocalizedName("elinarcurios.lunchBox");
        setTextureName(Reference.MOD_ID + ":lunchBox");
        setCreativeTab(ElinarCurios.creativeTab);
    }

    /**
     * 进食门槛：盒盖已开 + 盒内非空 + **玩家需要进食**。
     *
     * <p>
     * 这里必须比 SpiceOfLife 的原版判断（{@code player.canEat(false)}）宽松一点：原版 {@code canEat}
     * 是 {@code (ignoreHunger || needFood()) && !capabilities.disableDamage}，而创造模式的
     * {@code disableDamage} 恒为 true —— 于是创造模式下右键永远落进「打开仓库」分支，看起来就像
     * 「怎么点都不能吃」（原版 SpiceOfLife 午餐盒同样如此）。测试与创造模式日常使用都需要能吃。
     */
    @Override
    public boolean canPlayerEatFrom(final EntityPlayer player, final ItemStack stack) {
        return canBeEatenFrom(stack) && (player.canEat(false) || player.capabilities.isCreativeMode);
    }

    public static boolean isLunchBox(final ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemLunchBox;
    }

    // ------------------------------------------------------------------ NBT 根

    /** 可写根标签（不存在则创建）。 */
    public static NBTTagCompound getRootTag(final ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        final NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(TAG_ROOT, NBT.TAG_COMPOUND)) tag.setTag(TAG_ROOT, new NBTTagCompound());
        return tag.getCompoundTag(TAG_ROOT);
    }

    /** 只读根标签（不产生任何 NBT 副作用）。 */
    public static NBTTagCompound peekRootTag(final ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return null;
        final NBTTagCompound tag = stack.getTagCompound();
        return tag.hasKey(TAG_ROOT, NBT.TAG_COMPOUND) ? tag.getCompoundTag(TAG_ROOT) : null;
    }

    public static int getCurrentPage(final ItemStack stack) {
        final NBTTagCompound root = peekRootTag(stack);
        if (root == null) return 0;
        final int page = root.getInteger(TAG_CURRENT_PAGE);
        return page < 0 ? 0 : page >= PAGE_COUNT ? PAGE_COUNT - 1 : page;
    }

    public static void setCurrentPage(final ItemStack stack, final int page) {
        if (stack == null) return;
        getRootTag(stack).setInteger(TAG_CURRENT_PAGE, Math.max(0, Math.min(PAGE_COUNT - 1, page)));
    }

    // ------------------------------------------------------------------ ItemFoodContainer 覆写

    @Override
    public FoodContainerInventory getInventory(final ItemStack stack) {
        return new InventoryLunchBox(this, stack);
    }

    /** 每格 int 上限（父类默认 64，SpiceOfLife 的配置项也是 64）。 */
    @Override
    public int getInventoryStackLimit(final NBTInventory inventory) {
        return Integer.MAX_VALUE;
    }

    /** 判空走轻量 NBT 查询：父类只看它自己的 Inventory 子标签，在我们的结构下永远是「空」。 */
    @Override
    public boolean isEmpty(final ItemStack stack) {
        return LunchBoxNbt.countUsed(stack) == 0;
    }

    /**
     * 父类实现每次都会新建一个 Inventory 并对全部 2916 格做选食，用来回答「将要吃哪一块食物」；
     * 而 AppleCore 与 SpiceOfLife 的 tooltip/HUD 会在鼠标悬停期间反复查询，等于每帧解析整个多页 NBT。
     * 这里加一个极短的缓存窗口，只在真正需要时重算（进食本身不走这里，见 {@link #onEaten}）。
     */
    @Override
    public FoodValues getFoodValues(final ItemStack stack) {
        final long now = System.currentTimeMillis();
        if (cachedFoodValues != null && cachedFoodSource == stack && now - cachedFoodAt < FOOD_VALUES_CACHE_MILLIS) {
            return cachedFoodValues;
        }
        final FoodValues values = super.getFoodValues(stack);
        if (values != null) {
            cachedFoodSource = stack;
            cachedFoodValues = values;
            cachedFoodAt = now;
        }
        return values;
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack stack, final World world, final EntityPlayer player) {
        if (player.isSneaking()) {
            // 潜行右键：开/合盒盖（合盖后右键不再进食，而是打开仓库界面）。
            setIsOpen(stack, !isOpen(stack));
            return stack;
        }
        if (canPlayerEatFrom(player, stack)) {
            player.setItemInUse(stack, getMaxItemUseDuration(stack));
            return stack;
        }
        if (!isOpen(stack)) {
            // 合盖：打开仓库并掀盖（原版 SpiceOfLife 的语义）。
            if (world.isRemote) {
                ElinarCurios.networkWrapper.sendToServer(new OpenLunchBoxPacket.Message());
            }
            setIsOpen(stack, true);
            return stack;
        }
        // 开盖却吃不了（空盒 / 并不饿）：**不要打开仓库**。原版在这里什么都不做，而「一右键就弹
        // 界面」正是它被误判成「无法进食」的元凶。只在客户端补一句聊天提示说明原因。
        if (world.isRemote) {
            player.addChatMessage(
                new ChatComponentTranslation(
                    isEmpty(stack) ? "item.elinarcurios.lunchBox.msg.empty" : "item.elinarcurios.lunchBox.msg.full"));
        }
        // 这里**刻意不做** VanillaSyncGuard 快照对齐：开/合盒盖是低频动作，而它引起的
        // 「服务端把整份 NBT 推给客户端」恰恰是客户端拿到权威状态的机会 —— 抑制掉它，客户端
        // 手里的盒子 NBT 就会长期滞后。高频且昂贵的「吃」那一路仍然抑制（见 LunchBoxEatSync）。
        return stack;
    }

    /**
     * 覆盖 SpiceOfLife 的选食入口：它在一次进食里会被调用三回，每回都要求一次全盒扫描，
     * 这里靠缓存把三回并成一回，并复用同一份视图。
     */
    @Override
    public ItemStack getBestFoodForPlayerToEat(final ItemStack box, final EntityPlayer player) {
        if (box == null || player == null) return null;
        if (!bestFoodCacheHit(box, player)) refreshBestFood(box, player);
        return cachedBestFood;
    }

    /**
     * 吃一口：跨全页挑当前最优食物（食物多样性递减权重），吃掉一个后写回。
     * 与原版 {@code ItemFoodContainer.onEaten} 等价，差别在存储层、缓存选食与随后的增量同步。
     */
    @Override
    public ItemStack onEaten(final ItemStack stack, final World world, final EntityPlayer player) {
        // 客户端不参与结算：1.7.10 的 EntityPlayer.onItemUseFinish 只在服务端被调用
        // （onLivingUpdate 里的判定带 !worldObj.isRemote）。这里再挡一道，确保客户端永远不会
        // 为了进食去解析整个多页 NBT。
        if (world.isRemote) return stack;

        if (!bestFoodCacheHit(stack, player)) refreshBestFood(stack, player);
        final ItemStack food = cachedBestFood;
        if (food != null) {
            ItemStack result = food.onFoodEaten(world, player);
            result = ForgeEventFactory.onItemUseFinish(player, food, getMaxItemUseDuration(stack), result);
            if (result == null || result.stackSize <= 0) result = null;
            // 复用缓存里的视图：为一次写回再解析 2916 个槽纯属浪费。
            cachedBestInventory.setInventorySlotContents(cachedBestSlot, result);
            if (player instanceof EntityPlayerMP) {
                LunchBoxEatSync.pushAfterEat((EntityPlayerMP) player, cachedBestSlot, result);
            }
        }
        return stack;
    }

    private boolean bestFoodCacheHit(final ItemStack box, final EntityPlayer player) {
        return cachedBestInventory != null && cachedBestBox == box
            && cachedBestPlayer == player
            && cachedBestContent == LunchBoxNbt.contentVersion(box)
            && cachedBestHistory == historyStamp(player);
    }

    private void refreshBestFood(final ItemStack box, final EntityPlayer player) {
        final InventoryLunchBox inventory = new InventoryLunchBox(this, box);
        final int slot = LunchBoxSelector.findBestFoodSlot(player, inventory);
        cachedBestInventory = inventory;
        cachedBestSlot = slot;
        cachedBestFood = slot < 0 ? null : inventory.getStackInSlot(slot);
        cachedBestBox = box;
        cachedBestPlayer = player;
        cachedBestContent = LunchBoxNbt.contentVersion(box);
        cachedBestHistory = historyStamp(player);
    }

    /** 玩家食物历史的粗指纹：历史或饱食度一变就失效，避免用到过期的选择。 */
    private static int historyStamp(final EntityPlayer player) {
        final FoodHistory history = FoodHistory.get(player);
        return history.totalFoodsEatenAllTime * 31 + history.getRecentHistory()
            .size() * 7
            + player.getFoodStats()
                .getFoodLevel();
    }

    /** 容量本身极大，不需要 SpiceOfLife 的「对着箱子倒食物 / 从箱子吸食物」。 */
    @Override
    public boolean onItemUseFirst(final ItemStack stack, final EntityPlayer player, final World world, final int x,
        final int y, final int z, final int side, final float hitX, final float hitY, final float hitZ) {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    @SideOnly(Side.CLIENT)
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List tooltips,
        final boolean advanced) {
        super.addInformation(stack, player, tooltips, advanced);
        tooltips.add(
            EnumChatFormatting.GOLD.toString() + EnumChatFormatting.ITALIC.toString()
                + I18n.format("item.elinarcurios.lunchBox.tooltip.motto"));
        tooltips.add(
            I18n.format(
                "item.elinarcurios.lunchBox.tooltip.slots",
                Integer.toString(LunchBoxNbt.countUsed(stack)),
                Integer.toString(TOTAL_SLOTS)));
        // 状态行：直接说明「右键会发生什么」，省得玩家（或我自己）去猜为什么点不出进食。
        if (!isOpen(stack)) {
            tooltips.add(I18n.format("item.elinarcurios.lunchBox.state.closed"));
        } else if (isEmpty(stack)) {
            tooltips.add(I18n.format("item.elinarcurios.lunchBox.state.empty"));
        } else if (!canPlayerEatFrom(player, stack)) {
            tooltips.add(I18n.format("item.elinarcurios.lunchBox.state.full"));
        } else {
            tooltips.add(I18n.format("item.elinarcurios.lunchBox.state.ready"));
        }
        if (player.isSneaking()) {
            tooltips.add(
                I18n.format(
                    "item.elinarcurios.lunchBox.tooltip.pages",
                    Integer.toString(PAGE_COUNT),
                    Integer.toString(SLOTS_PER_PAGE)));
            tooltips.add(I18n.format("item.elinarcurios.lunchBox.tooltip.max"));
            tooltips.add(I18n.format("item.elinarcurios.lunchBox.tooltip.eat"));
            tooltips.add(I18n.format("item.elinarcurios.lunchBox.tooltip.open"));
        } else {
            tooltips.add(I18n.format("item.elinarcurios.tooltip.hint"));
        }
    }
}
