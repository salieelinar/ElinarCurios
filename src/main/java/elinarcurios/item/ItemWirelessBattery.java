package elinarcurios.item;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import gregtech.api.enums.GTValues;
import gregtech.common.misc.WirelessNetworkManager;
import ic2.api.item.IElectricItemManager;
import ic2.api.item.ISpecialElectricItem;

/**
 * 天玄·虚空电池系列(ULV ~ MAX,15 档;原 EnergyCeph 独立 mod,已并入
 * ElinarCurios)。Item damage 0..14 = 电压等级 tier:
 *
 * <ul>
 * <li>电压 V = GTValues.V[tier](ULV 8V ~ MAX 2,147,483,640V);</li>
 * <li>放电/充电上限 2A(transferLimit = 2×V EU/t);</li>
 * <li>容量 C = V × 15625(HV 档 8,000,000 原型验证),30% 保底 ≥ 2A 满放
 * 2000 tick(极端不断电条件全档成立);</li>
 * <li>玩家手持右键绑定队伍无线账户;每次真实充/放触碰时检查电量,
 * ≤30% 即从无线电网一次性补满(只在补电失败时退避 2000 tick)。</li>
 * </ul>
 *
 * <p>
 * 依赖 GregTech(WirelessNetworkManager)与 IC2(IElectricItem 体系),
 * 均为 GTNH 必装 —— 注册点(CommonProxy)有 isModLoaded 守卫,无 GT/IC2
 * 环境下本类不会被加载(同 ThaumcraftCompat 的防御模式)。
 */
public class ItemWirelessBattery extends Item implements ISpecialElectricItem, IElectricItemManager {

    public static ItemWirelessBattery instance;

    /** 电压档数:GTValues.V[0..14],ULV ~ MAX。 */
    public static final int TIER_COUNT = 15;
    /** 电量低于该比例触发无线补电。 */
    public static final int REFILL_THRESHOLD_PERCENT = 30;
    /** 补电失败后的退避时长(tick):余额不足时避免每 tick 骚扰 GT 的无线电网。 */
    public static final long REFILL_COOLDOWN_TICKS = 2000L;
    /** 容量系数:V×15625(HV 档 = 8,000,000,原型已验证)。 */
    public static final long CHARGE_PER_VOLT = 15625L;

    /** 显示用档位缩写(与 GTNH 惯例一致)。 */
    private static final String[] TIER_NAMES = { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV",
        "UIV", "UMV", "UXV", "MAX" };

    // NBT 键
    private static final String NBT_CHARGE = "EC.Charge";
    private static final String NBT_OWNER_UUID = "EC.OwnerUUID";
    private static final String NBT_OWNER_NAME = "EC.OwnerName";
    private static final String NBT_LAST_REFILL = "EC.LastRefill";

    @SideOnly(Side.CLIENT)
    private IIcon[][] icons; // [tier][0空 1低 2中 3满]

    public ItemWirelessBattery() {
        setUnlocalizedName("wirelessBattery");
        setCreativeTab(ElinarCurios.creativeTab);
        setMaxStackSize(1);
        setHasSubtypes(true);
    }

    // ==================================================================
    // 档位参数
    // ==================================================================

    private static int tierOf(ItemStack stack) {
        int dmg = stack.getItemDamage();
        if (dmg < 0) return 0;
        return Math.min(dmg, TIER_COUNT - 1);
    }

    private static long voltageOf(int tier) {
        return GTValues.V[tier];
    }

    private static long maxChargeOf(int tier) {
        return voltageOf(tier) * CHARGE_PER_VOLT;
    }

    private static double transferLimitOf(int tier) {
        return 2.0D * voltageOf(tier); // 2A
    }

    // ==================================================================
    // 物品渲染:icons[tier][电量档](电量芯颜色=tier 色,高度=电量)
    // ==================================================================

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icons = new IIcon[TIER_COUNT][4];
        for (int t = 0; t < TIER_COUNT; t++) {
            for (int s = 0; s < 4; s++) {
                icons[t][s] = reg.registerIcon("elinarcurios:wb_" + t + "_" + s);
            }
        }
        // 兜底:覆写 registerIcons 后基类的 itemIcon 永远没被赋值,而
        // getIconFromDamage(int) 返回的正是它 —— 第三方(NEI/其它渲染器)
        // 走那条路径会拿到 null。给它挂一张有效图标(ULV 满电档)。
        itemIcon = icons[0][3];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        // 1.7.10 渲染链(RenderItem)实际调用的是 getIcon(ItemStack, pass),
        // 不是 getIconIndex —— 电量档位图标必须挂在这里。
        if (icons == null) return null;
        int t = tierOf(stack);
        double ratio = getCharge(stack) / getMaxCharge(stack);
        if (ratio >= 0.75D) return icons[t][3];
        if (ratio >= 0.40D) return icons[t][2];
        if (ratio >= 0.10D) return icons[t][1];
        return icons[t][0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        // 兼容其它走 getIconIndex 的调用方
        return getIcon(stack, 0);
    }

    // ==================================================================
    // 显示名 / 创造页
    // ==================================================================

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        return StatCollector.translateToLocalFormatted("item.wirelessBattery.name", TIER_NAMES[tierOf(stack)]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int t = 0; t < TIER_COUNT; t++) {
            ItemStack stack = new ItemStack(item, 1, t);
            setCharge(stack, maxChargeOf(t));
            list.add(stack);
        }
    }

    // ==================================================================
    // 右键绑定无线账户
    // ==================================================================

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) return stack;
        NBTTagCompound tag = ensureTag(stack);
        UUID uuid = player.getUniqueID();
        String uuidStr = uuid.toString();
        if (uuidStr.equals(tag.getString(NBT_OWNER_UUID))) {
            sendPlayerMessage(
                player,
                EnumChatFormatting.LIGHT_PURPLE
                    + StatCollector.translateToLocal("elinarcurios:messages.battery.alreadyBound"));
            return stack;
        }
        WirelessNetworkManager.strongCheckOrAddUser(uuid);
        tag.setString(NBT_OWNER_UUID, uuidStr);
        tag.setString(NBT_OWNER_NAME, player.getDisplayName());
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP pmp = (EntityPlayerMP) player;
            pmp.playerNetServerHandler.sendPacket(new S2FPacketSetSlot(0, 36 + pmp.inventory.currentItem, stack));
        }
        sendPlayerMessage(
            player,
            EnumChatFormatting.LIGHT_PURPLE + StatCollector.translateToLocal("elinarcurios:messages.battery.bound")
                .replace("%s", tag.getString(NBT_OWNER_NAME)));
        return stack;
    }

    private static void sendPlayerMessage(EntityPlayer player, String message) {
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).addChatMessage(new ChatComponentText(message));
        }
    }

    // ==================================================================
    // Tooltip
    // ==================================================================

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        int tier = tierOf(stack);
        double charge = getCharge(stack);
        double max = getMaxCharge(stack);
        int percent = max <= 0 ? 0 : (int) Math.round(charge * 100D / max);
        EnumChatFormatting color = percent >= 75 ? EnumChatFormatting.LIGHT_PURPLE
            : percent >= 40 ? EnumChatFormatting.DARK_PURPLE
                : percent >= 10 ? EnumChatFormatting.GRAY : EnumChatFormatting.DARK_GRAY;
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.wirelessBattery.tooltip.storage")
                + " "
                + color
                + String.format("%,d", (long) charge)
                + " EU ("
                + percent
                + "%)");
        NBTTagCompound tag = stack.getTagCompound();
        String boundName = tag != null && tag.hasKey(NBT_OWNER_NAME) ? tag.getString(NBT_OWNER_NAME) : null;
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.wirelessBattery.tooltip.bound")
                + " "
                + (boundName == null
                    ? EnumChatFormatting.RED + StatCollector.translateToLocal("item.wirelessBattery.tooltip.unbound")
                    : EnumChatFormatting.AQUA + boundName));
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.wirelessBattery.tooltip.voltage")
                + " "
                + EnumChatFormatting.YELLOW
                + voltageOf(tier)
                + "V / 2A ("
                + TIER_NAMES[tier]
                + ")");
        lines.add(
            EnumChatFormatting.DARK_GRAY + ""
                + EnumChatFormatting.ITALIC
                + StatCollector.translateToLocal("item.wirelessBattery.tooltip.refillHint"));
        lines.add(
            EnumChatFormatting.GOLD + ""
                + EnumChatFormatting.ITALIC
                + StatCollector.translateToLocal("item.wirelessBattery.tooltip.flavor"));
    }

    // ==================================================================
    // 无线补电核心:挂在每次"真实充/放触碰"上
    // ==================================================================

    private void tryWirelessRefill(ItemStack stack) {
        int tier = tierOf(stack);
        long max = maxChargeOf(tier);
        long real = getRealCharge(stack);
        if (real >= max * REFILL_THRESHOLD_PERCENT / 100L) return;

        // 纯客户端(多人游戏)没有 MinecraftServer,读 tick 计数会 NPE。
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return;

        NBTTagCompound tag = ensureTag(stack);
        if (tag.hasKey(NBT_LAST_REFILL)) {
            // 退避判定(只在补电失败时盖章)。last 持久化在 NBT,服务器/存档重启后
            // tick 归零,last 可能 > now(负差)→ 视为退避已过,否则会永久卡住不补电。
            long elapsed = server.getTickCounter() - tag.getLong(NBT_LAST_REFILL);
            if (elapsed >= 0 && elapsed < REFILL_COOLDOWN_TICKS) return;
        }

        String uuidStr = tag.getString(NBT_OWNER_UUID);
        if (uuidStr.isEmpty()) return; // 未绑定:不盖章,绑定后第一次触碰即可补
        UUID owner;
        try {
            owner = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException ignored) {
            return;
        }
        long need = max - real;
        if (need <= 0) return;
        // 补电成功立即生效(不盖冷却章)——放进机器的那一刻就补满,不必等下一个
        // 2000 tick 窗口;只有失败(无线账户余额不足等)才退避,避免每 tick 反复
        // 操作 GT 的 BigInteger 表。
        if (WirelessNetworkManager.addEUToGlobalEnergyMap(owner, -need)) {
            setCharge(stack, max);
        } else {
            tag.setLong(NBT_LAST_REFILL, server.getTickCounter());
        }
    }

    // ==================================================================
    // NBT 存取
    // ==================================================================

    private static NBTTagCompound ensureTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
    }

    private static long getRealCharge(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_CHARGE)) {
            // 新电池/被重建(创造页、NEI)的电池默认满电 —— GT/IC2 电池惯例,
            // 否则无 NBT 的 stack 会被当成 0 电(图标空、无法放电)。
            return maxChargeOf(tierOf(stack));
        }
        return tag.getLong(NBT_CHARGE);
    }

    private static void setCharge(ItemStack stack, long charge) {
        long max = maxChargeOf(tierOf(stack));
        ensureTag(stack).setLong(NBT_CHARGE, Math.min(max, Math.max(0L, charge)));
    }

    /**
     * 造一块"空"的电池(显式写入 EC.Charge = 0)给配方当产物。
     *
     * <p>
     * 旧版产物无 NBT,而 {@link #getRealCharge} 把无 NBT 当成满电 ——
     * "4 个空电池 → 1 个满电池"就能白刷 EU。现在产物显式写 0,既堵住白刷,
     * 也让 NEI 显示的产物与实际合成结果一致(都显示 0%)。
     */
    public static ItemStack newEmptyBattery(int tier) {
        ItemStack stack = new ItemStack(instance, 1, Math.max(0, Math.min(tier, TIER_COUNT - 1)));
        setCharge(stack, 0L);
        return stack;
    }

    /**
     * 电池是否已充满(供 {@link elinarcurios.recipe.CrossBatteryRecipe} 匹配用)。
     *
     * <p>
     * 注意:无 NBT 的电池按 {@link #getRealCharge} 的约定视为满电,所以刚出炉、
     * 从创造页/NEI 拿到的电池天然满足条件;只有被用过(EC.Charge 有值)且没充满
     * 的电池会被拒。
     */
    public static boolean isFullyCharged(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemWirelessBattery
            && getRealCharge(stack) >= maxChargeOf(tierOf(stack));
    }

    // ==================================================================
    // IElectricItem(getters —— 容器只认这套)
    // ==================================================================

    @Override
    public boolean canProvideEnergy(ItemStack stack) {
        return true;
    }

    @Override
    public int getTier(ItemStack stack) {
        return tierOf(stack);
    }

    @Override
    public double getMaxCharge(ItemStack stack) {
        return maxChargeOf(tierOf(stack));
    }

    @Override
    public double getTransferLimit(ItemStack stack) {
        return transferLimitOf(tierOf(stack));
    }

    @Override
    public Item getChargedItem(ItemStack stack) {
        return this;
    }

    @Override
    public Item getEmptyItem(ItemStack stack) {
        return this;
    }

    // ==================================================================
    // ISpecialElectricItem —— 容器经 IC2 ElectricItem.manager 委托到本类
    // ==================================================================

    @Override
    public IElectricItemManager getManager(ItemStack stack) {
        return this;
    }

    @Override
    public boolean getShareTag() {
        return true;
    }

    // ==================================================================
    // IElectricItemManager
    // ==================================================================

    @Override
    public double charge(ItemStack stack, double amount, int tier, boolean ignoreTransferLimit, boolean simulate) {
        int myTier = tierOf(stack);
        if (myTier > tier) return 0; // 充电设备电压等级不够,拒绝
        // ★ 无线补电挂在"任何真实触碰"上(充或放):
        // 电池箱 buffer 满时走的是充电路径而非放电路径,若只挂 discharge,
        // 满 buffer 箱子里的空电池将永远不被触碰、永不补电。
        if (!simulate) tryWirelessRefill(stack);
        long max = maxChargeOf(myTier);
        double before = getCharge(stack);
        double transfer = ignoreTransferLimit ? amount : Math.min(transferLimitOf(myTier), amount);
        double after = Math.min(max, before + transfer);
        if (!simulate && after != before) setCharge(stack, (long) after);
        return after - before;
    }

    @Override
    public double discharge(ItemStack stack, double amount, int tier, boolean ignoreTransferLimit, boolean batteryAlike,
        boolean simulate) {
        int myTier = tierOf(stack);
        if (myTier > tier) return 0; // 电池等级高于用电设备,拒绝(防高压串低压)
        if (batteryAlike && !canProvideEnergy(stack)) return 0;
        // ★ 无线补电只在真实放电时触发;simulate(查询)不产生副作用
        if (!simulate) tryWirelessRefill(stack);
        double before = getCharge(stack);
        double transfer = ignoreTransferLimit ? amount : Math.min(transferLimitOf(myTier), amount);
        double after = Math.max(0D, before - transfer);
        if (!simulate && after != before) setCharge(stack, (long) after);
        return before - after;
    }

    @Override
    public double getCharge(ItemStack stack) {
        return getRealCharge(stack);
    }

    @Override
    public boolean canUse(ItemStack stack, double amount) {
        return getRealCharge(stack) >= amount;
    }

    @Override
    public boolean use(ItemStack stack, double amount, EntityLivingBase player) {
        if (player instanceof EntityPlayer && ((EntityPlayer) player).capabilities.isCreativeMode) return true;
        // ★ 补电必须在 simulate 之前完成:simulate 不产生副作用,若补电只发生在后面的
        // 真实放电里,就会出现"模拟说电量不够 → 返回 false,但电其实已被补满并抽走"。
        // (GT 原版 use() 也这么写,它安全是因为它的 discharge 没有副作用。)
        tryWirelessRefill(stack);
        double transferred = discharge(stack, amount, Integer.MAX_VALUE, true, false, true);
        if (Math.abs(transferred - amount) < 0.0000001D) {
            discharge(stack, amount, Integer.MAX_VALUE, true, false, false);
            return true;
        }
        discharge(stack, amount, Integer.MAX_VALUE, true, false, false);
        return false;
    }

    @Override
    public void chargeFromArmor(ItemStack stack, EntityLivingBase player) {
        // 装甲回充对本电池无意义,留空
    }

    @Override
    public String getToolTip(ItemStack stack) {
        return null;
    }
}
