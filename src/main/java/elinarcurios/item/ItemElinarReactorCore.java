package elinarcurios.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;

/**
 * 天枢·恒温核心 —— 反应堆隔板 + 冷却单元 + 热交换器的三合一反应堆元件。
 *
 * <p>
 * 一个物品同时承担 IC2 传统核电里三种元件的职责:
 *
 * <pre>
 * 自身散热(selfVent)   1,000,000 Hu/s   —— 散热片:先排在自身上,再排进空气/HU 缓冲
 * 主动吸热(switchSide) 1,000,000 Hu/s   —— 热交换器:与四邻可储热元件双向换热
 * 核心吸热(switchReactor) 1,000,000 Hu/s —— 热交换器:与堆芯 hull 双向换热
 * 热容(heatStorage)    1,000,000,000 Hu —— 冷却单元:自身可储热上限
 * 隔板加成(maxHeatAdd) 20,000,000 Hu/s  —— 反应堆隔板:抬高堆芯爆点
 * </pre>
 *
 * <p>
 * <b>口径(2026-09-13 用运行版 jar 的字节码核实):</b>IC2 堆芯每秒只结算一次
 * ({@code getTickRate() == 20}),每次结算里只有 heatrun 那一趟会走到这里,所以
 * <b>"每次热趟施加量"就等于"每秒值"</b>,不需要再乘 2(旧版注释里的 ×2 是把"两趟"误当成
 * "每秒两次")。五项数值由芷若 2026-09-13 拍板:散热/主动吸热/核心吸热各 100 万,
 * 热容保持 10 亿,隔板加成 2000 万(原提案 10 亿被否,理由见下方上限说明)。
 *
 * <p>
 * <b>数值上限(踩过的坑,勿动):</b>
 *
 * <ul>
 * <li>IC2 侧所有热值都是 int32(IReactor.getHeat/getMaxHeat、TE 的 {@code int maxHeat}),
 * 所以隔板加 <b>绝不能让堆芯 maxHeat 越过 2,147,483,647</b>。本元件 2000 万/颗,按该上限
 * 要叠到 <b>107 颗</b>才越界,而 IC2 反应堆满配只有 54 格 → 实际不可能溢出。</li>
 * <li>更硬的限制是 GUI:IC2 的 {@code gaugeHeatScaled(i) { return i * heat / (maxHeat / 100 * 85); }}
 * 与百分比显示的 {@code 100 * heat / maxHeat} 都是整数运算,<b>堆芯热量 heat 超过
 * 21,474,836(INT_MAX/100,百分比)或 13,421,772(INT_MAX/160,温度条)时分子溢出</b>
 * → 温度条/百分比错乱。单颗 2000 万时堆芯上限 20,010,000 &lt; 21,474,836,
 * <b>堆里只有一颗时 heat 永远碰不到溢出线</b>;多颗叠加会抬高上限,若堆芯真烧到千万级热量
 * 才会看到仪表失真 —— 而本元件每秒抽热/排热各 100 万,正常堆的热量被压在低位,碰不到。</li>
 * <li>堆芯每 tick 先 {@code maxHeat = 10000; hem = 1.0} 再遍历元件重算,所以隔板效果
 * 不需要注册/反注册,拔掉立即失效;本元件只在热淌(pass 0,heatrun=true)施加加成,
 * 避免 IC2 每次结算调两趟 processChamber 导致的双倍生效。</li>
 * </ul>
 *
 * <p>
 * <b>依赖:</b>只依赖 IC2 的 reactor API(ic2.api.reactor.*),而 IC2 是 GTNH 必装 ——
 * 注册点 {@code CommonProxy} 有 isModLoaded 守卫,无 IC2 时本类不会被加载
 * (同 {@link ItemWirelessBattery} / ThaumcraftCompat 的防御模式)。
 */
public class ItemElinarReactorCore extends Item implements IReactorComponent {

    public static ItemElinarReactorCore instance;

    /** 自身散热上限(Hu/s)。 */
    public static final int SELF_VENT = 1000000;
    /** 与相邻元件换热上限(Hu/s)。 */
    public static final int SWITCH_SIDE = 1000000;
    /** 与堆芯换热上限(Hu/s)。 */
    public static final int SWITCH_REACTOR = 1000000;
    /** 自身热容(Hu),同时也是冷却单元式的储热上限。 */
    public static final int HEAT_STORAGE = 1000000000;
    /**
     * 隔板加成:每次热趟(反应堆每秒只结算一次)施加到堆芯 maxHeat 的量。
     *
     * <p>
     * 20,000,000/颗 → 单颗堆芯上限 20,010,000,仍在 IC2 仪表溢出线(21,474,836)以内;
     * 要叠 107 颗才撞 int32 上限,而反应堆满配只有 54 格,故实际不会溢出。
     */
    public static final int PLATING_HEAT_PER_HEAT_PASS = 20000000;
    /** 隔板对爆炸威力/热效应修正的乘子(与 IC2 反应堆隔板一致)。 */
    public static final float PLATING_EFFECT_MODIFIER = 0.95F;

    /**
     * tooltip 里展示的隔板速率。
     *
     * <p>
     * IC2 堆芯每秒只结算一次({@code getTickRate() == 20}),本元件每次结算只施加一次,
     * 所以<b>每秒值就等于每次值,不要再乘 2</b>。
     */
    public static final int PLATING_HEAT_PER_SECOND = PLATING_HEAT_PER_HEAT_PASS;

    /** NBT 键:自身已储热量(不用 damage,damage 只作进度条显示)。 */
    public static final String NBT_HEAT = "heat";

    /** damage 满值(进度条粒度)。 */
    private static final int DAMAGE_BAR_MAX = 100;

    @SideOnly(Side.CLIENT)
    private IIcon icon;
    @SideOnly(Side.CLIENT)
    private IIcon iconHot;

    public ItemElinarReactorCore() {
        setUnlocalizedName("elinarReactorCore");
        setTextureName("elinarcurios:reactorCore");
        setCreativeTab(ElinarCurios.creativeTab);
        // 带热状态的元件绝不能被堆叠:否则 NBT 会在合并时互相覆盖。
        setMaxStackSize(1);
        // damage 只用于显示进度条,不是耐久,禁止修复。
        setMaxDamage(DAMAGE_BAR_MAX);
        setNoRepair();
    }

    // ==================================================================
    // 图标:空冷 / 高热两张(静态贴图,不做动画渐变)
    // ==================================================================

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icon = reg.registerIcon("elinarcurios:reactorCore");
        iconHot = reg.registerIcon("elinarcurios:reactorCoreHot");
        // 兜底:覆写 registerIcons 后基类 itemIcon 从未被赋值,而 getIconFromDamage(int)
        // 返回的正是它 —— 第三方(NEI/其它渲染器)走那条路径会拿到 null。
        itemIcon = icon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return damage / (double) DAMAGE_BAR_MAX >= 0.5D ? iconHot : icon;
    }

    /** 按 NBT 热量取图标(1.7.10 主渲染链走 getIcon(ItemStack, pass))。 */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        if (icon == null) return null;
        return heatRatio(stack) >= 0.5D ? iconHot : icon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return getIcon(stack, 0);
    }

    private static double heatRatio(ItemStack stack) {
        int heat = getStoredHeat(stack);
        return heat <= 0 ? 0.0D : Math.min(1.0D, heat / (double) HEAT_STORAGE);
    }

    // ==================================================================
    // IReactorComponent —— 元件协议
    // ==================================================================

    /**
     * IC2 每次结算调两趟:pass 0 是 heatrun=true(热趟)、pass 1 是 heatrun=false。
     * 隔板/散热/换热只在热趟做,避免双倍生效;而结算本身每秒只有一次(getTickRate()==20)。
     */
    @Override
    public void processChamber(IReactor reactor, ItemStack stack, int x, int y, boolean heatrun) {
        if (!heatrun) return;

        // ---- 反应堆隔板:抬堆芯爆点 + 压热效应(与 IC2 原版隔板同机制) ----
        reactor.setMaxHeat(reactor.getMaxHeat() + PLATING_HEAT_PER_HEAT_PASS);
        reactor.setHeatEffectModifier(reactor.getHeatEffectModifier() * PLATING_EFFECT_MODIFIER);

        // ---- 核心吸热:从堆芯 hull 抽热 ----
        drawFromHull(reactor, stack);

        // ---- 主动吸热:与四邻元件换热 ----
        exchangeWithNeighbours(reactor, stack, x, y);

        // ---- 自身散热:把自己身上的热排进空气(流体堆里成为 HU 产出) ----
        ventSelf(reactor, stack);
    }

    /** 未装满时即可储热(装满后燃料棒会改把热丢给堆芯,这是 IC2 既有行为)。 */
    @Override
    public boolean canStoreHeat(IReactor reactor, ItemStack stack, int x, int y) {
        return getStoredHeat(stack) < HEAT_STORAGE;
    }

    @Override
    public int getMaxHeat(IReactor reactor, ItemStack stack, int x, int y) {
        return HEAT_STORAGE;
    }

    @Override
    public int getCurrentHeat(IReactor reactor, ItemStack stack, int x, int y) {
        return getStoredHeat(stack);
    }

    /**
     * 增减自身热量。
     *
     * @return <b>拒收的热量</b>(IC2 协议:0 = 全部接受;正数 = 这部分没吃下,退回调用方)
     */
    @Override
    public int alterHeat(IReactor reactor, ItemStack stack, int x, int y, int heat) {
        int current = getStoredHeat(stack);
        long next = (long) current + heat;
        int rejected = 0;
        if (next > HEAT_STORAGE) {
            rejected = (int) (next - HEAT_STORAGE);
            next = HEAT_STORAGE;
        } else if (next < 0) {
            rejected = (int) next; // 负 = 想取走的热比实际拥有的多,退回差额
            next = 0;
        }
        setStoredHeat(stack, (int) next);
        return rejected;
    }

    /** 中子脉冲照常透传(纯被动元件,不产 EU)。 */
    @Override
    public boolean acceptUraniumPulse(IReactor reactor, ItemStack stack, ItemStack pulsingStack, int youX, int youY,
        int pulseX, int pulseY, boolean heatrun) {
        return false;
    }

    /** 与 IC2 隔板同:返回 0.95 → 爆炸计算里当作倍乘因子(压爆炸)。 */
    @Override
    public float influenceExplosion(IReactor reactor, ItemStack stack) {
        return PLATING_EFFECT_MODIFIER;
    }

    // ==================================================================
    // 换热 / 散热实现(照 IC2 ItemReactorHeatSwitch / ItemReactorVent 的行为)
    // ==================================================================

    private void drawFromHull(IReactor reactor, ItemStack stack) {
        if (SWITCH_REACTOR <= 0) return;
        int current = getStoredHeat(stack);
        int room = HEAT_STORAGE - current;
        if (room <= 0) return;
        int hull = reactor.getHeat();
        if (hull <= 0) return;
        int move = Math.min(Math.min(SWITCH_REACTOR, hull), room);
        if (move <= 0) return;

        int rejected = alterHeat(reactor, stack, -1, -1, move);
        int accepted = move - rejected;
        if (accepted > 0) {
            int newHull = hull - accepted;
            if (newHull < 0) newHull = 0;
            reactor.setHeat(newHull);
        }
    }

    private void exchangeWithNeighbours(IReactor reactor, ItemStack stack, int x, int y) {
        if (SWITCH_SIDE <= 0) return;
        int[][] offsets = { { x - 1, y }, { x + 1, y }, { x, y - 1 }, { x, y + 1 } };
        for (int[] offset : offsets) {
            exchangeWith(reactor, stack, x, y, offset[0], offset[1]);
        }
    }

    private void exchangeWith(IReactor reactor, ItemStack selfStack, int x, int y, int nx, int ny) {
        ItemStack neighbour = reactor.getItemAt(nx, ny);
        if (neighbour == null || neighbour.getItem() == null || !(neighbour.getItem() instanceof IReactorComponent)) {
            return;
        }
        IReactorComponent comp = (IReactorComponent) neighbour.getItem();
        if (!comp.canStoreHeat(reactor, neighbour, nx, ny)) return;

        int maxSelf = HEAT_STORAGE;
        int self = getStoredHeat(selfStack);
        int roomSelf = maxSelf - self;
        int maxNeighbour = comp.getMaxHeat(reactor, neighbour, nx, ny);
        int neighbourRoom = maxNeighbour - comp.getCurrentHeat(reactor, neighbour, nx, ny);
        if (neighbourRoom < 0) neighbourRoom = 0;
        if (roomSelf <= 0 && neighbourRoom <= 0) return;

        if (self > 0 && neighbourRoom > 0) {
            // 本元件更热:热流向邻居
            int move = Math.min(Math.min(SWITCH_SIDE, self), neighbourRoom);
            int rejected = comp.alterHeat(reactor, neighbour, nx, ny, move);
            int accepted = move - rejected;
            if (accepted > 0) alterHeat(reactor, selfStack, x, y, -accepted);
        } else if (neighbourRoom == 0 || (self == 0 && roomSelf > 0)) {
            // 邻居更热:热流向本元件
            int neighbourHeat = comp.getCurrentHeat(reactor, neighbour, nx, ny);
            int move = Math.min(Math.min(SWITCH_SIDE, neighbourHeat), roomSelf);
            if (move <= 0) return;
            int rejected = alterHeat(reactor, selfStack, x, y, move);
            int accepted = move - rejected;
            if (accepted > 0) comp.alterHeat(reactor, neighbour, nx, ny, -accepted);
        }
    }

    private void ventSelf(IReactor reactor, ItemStack stack) {
        if (SELF_VENT <= 0) return;
        int heat = getStoredHeat(stack);
        if (heat <= 0) return;
        int move = Math.min(SELF_VENT, heat);
        if (move <= 0) return;
        int rejected = alterHeat(reactor, stack, -1, -1, -move);
        int vented = move - rejected;
        if (vented > 0) reactor.addEmitHeat(vented);
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

    /** 已储热;没有 NBT 的(刚合成/NEI 预览)一律按 0 处理。 */
    public static int getStoredHeat(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) return 0;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_HEAT)) return 0;
        int heat = tag.getInteger(NBT_HEAT);
        return heat < 0 ? 0 : Math.min(heat, HEAT_STORAGE);
    }

    public static void setStoredHeat(ItemStack stack, int heat) {
        if (stack == null) return;
        int clamped = Math.max(0, Math.min(heat, HEAT_STORAGE));
        ensureTag(stack).setInteger(NBT_HEAT, clamped);
        // damage 仅作显示:0(空)…DAMAGE_BAR_MAX(满),每格 = HEAT_STORAGE / 100 = 1000 万 Hu。
        // 中间量用 long:clamped × 100 最大 1e11,直接 int 乘会溢出。
        stack.setItemDamage((int) (clamped * (long) DAMAGE_BAR_MAX / HEAT_STORAGE));
    }

    // ==================================================================
    // 显示:进度条 / 创造页 / tooltip
    // ==================================================================

    @Override
    @SideOnly(Side.CLIENT)
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0D - stack.getItemDamage() / (double) DAMAGE_BAR_MAX;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, 0));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        int heat = getStoredHeat(stack);
        int percent = (int) (heat * 100L / HEAT_STORAGE);
        EnumChatFormatting heatColor = percent >= 75 ? EnumChatFormatting.RED
            : percent >= 40 ? EnumChatFormatting.GOLD
                : percent > 0 ? EnumChatFormatting.YELLOW : EnumChatFormatting.AQUA;
        final String key = "item.elinarReactorCore.tooltip.";

        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(key + "storage")
                + " "
                + heatColor
                + String.format("%,d", heat)
                + EnumChatFormatting.GRAY
                + " / "
                + String.format("%,d", HEAT_STORAGE)
                + " Hu ("
                + percent
                + "%)");
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                key + "vent") + " " + EnumChatFormatting.YELLOW + String.format("%,d", SELF_VENT) + " Hu/s");
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(
                key + "switchSide") + " " + EnumChatFormatting.YELLOW + String.format("%,d", SWITCH_SIDE) + " Hu/s");
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(key + "switchReactor")
                + " "
                + EnumChatFormatting.YELLOW
                + String.format("%,d", SWITCH_REACTOR)
                + " Hu/s");
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(key + "plating")
                + " "
                + EnumChatFormatting.YELLOW
                + String.format("%,d", PLATING_HEAT_PER_SECOND)
                + " Hu/s");
        lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(key + "note1"));
        lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(key + "note2"));
        lines.add(
            EnumChatFormatting.GOLD + "" + EnumChatFormatting.ITALIC + StatCollector.translateToLocal(key + "flavor"));
    }
}
