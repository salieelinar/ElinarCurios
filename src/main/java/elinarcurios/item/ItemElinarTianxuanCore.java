package elinarcurios.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;

/**
 * 天玄·恒温核心 —— 在 {@link ItemElinarReactorCore 天枢·恒温核心} 之上再进一步的反应堆元件。
 *
 * <p>
 * 两个功能(数值与行为由 2026-09-21 的桌面规划器版本定稿,芷若确认后实装):
 *
 * <pre>
 * ① 99% 硬锁    堆芯温度锁死在热容上限的 99%,并把热效应修正(HEM)压成 0
 * ② 全局增益    堆里每一根燃料棒,每 tick 额外获得"自身产出 × {@link #BOOST_PERCENT}"的 EU
 * </pre>
 *
 * <p>
 * <b>这套机制为什么有效、以及哪些说法是错的(2026-09-21 用 GT5U 字节码核实,重要):</b>
 *
 * <ul>
 * <li><b>修正一个曾经的错误理解</b>:IC2 的 heat effect modifier <b>不缩放燃料棒的产热</b>。
 * GT5U {@code ItemRadioactiveCellIC.processChamber} 里对 {@code IReactor} 的调用只有
 * {@code produceEnergy()} / {@code addHeat(int)} / {@code setItemAt(...)},没有任何
 * {@code getHeatEffectModifier};而堆芯的 {@code addHeat} 是裸加
 * ({@code heat += h})。HEM 在整个 IC2 里只有 4 处引用(接口声明、反应堆 TE 的爆炸威力与
 * 岩浆/辐射概率、隔板乘法、调试工具)。</li>
 * <li><b>真正让堆不满热的是产热公式的结构</b>:燃料棒的产热是
 * {@code triangularNumber(pulses) * sHeat},而 {@code pulses = 1 + numberOfCells/2 +
 * checkPulseable(四个邻居)}。{@link #acceptUraniumPulse} 返回 {@code false},所以本元件
 * <b>不会被算作反射板</b>;本元件发出的那几百个脉冲只走能量通道(燃料棒的 {@code addOutput}),
 * <b>完全不进产热公式</b> —— 于是"产能翻几倍、产热几乎不变"。</li>
 * <li>HEM 压成 0 的作用是<b>钳制侧效应</b>:超过 50%/70%/85% 时的岩浆、辐射伤害、着火概率，
 * 以及熔毁时的爆炸威力,都是乘 {@code hem} 的,置 0 即全部归零。</li>
 * <li>堆芯温度由本元件每 tick 在能量趟直接写成 99%,热趟里各元件把热量加上来(燃料棒产热只有
 * 几十~几百 HU/tick 的量级),下个 tick 再被重新设为 99% —— 所以在 10,000 的爆点之下稳定。</li>
 * </ul>
 *
 * <p>
 * <b>全局增益的实现(不依赖任何硬编码的燃料表):</b>GT5U 的 {@code ItemRadioactiveCellIC} 与 IC2 的
 * {@code ItemReactorUranium} 都公开了"棒数"字段 {@code numberOfCells}(public final);一次
 * {@code acceptUraniumPulse} 给堆芯 {@code addOutput(sEnergy)}(MOX 还会按热比放大)。棒数通过
 * 反射读取({@link #rodCountOf}),每次脉冲的 EU 量则由燃料棒自己算,所以本元件不需要硬编码任何
 * 燃料系数 —— 脉冲数按棒数成比例给({@code round(棒数 × PULSES_PER_ROD_PER_FULL_BOOST ×
 * BOOST_PERCENT)}),与元件摆在哪一格无关。
 *
 * <p>
 * <b>实测标定(2026-09-21):</b>53 根四联钍棒的堆,裸棒 ≈13,480 EU/t,装上本元件(每格 100 脉冲)
 * 后 ≈<b>66,478 EU/t</b>(约 4.93 倍)。也就是说 {@link #PULSES_PER_ROD_PER_FULL_BOOST} 的
 * "25 = 100%" 这个换算在实机上偏保守,芷若明确表示<b>这个逆天数值不打算改</b>,故保持现状;
 * 需要精确调平衡时改该常量即可(每格脉冲数 = 棒数 × 该值 × {@link #BOOST_PERCENT})。
 *
 * <p>
 * <b>多颗元件并存是安全的:</b>锁温与 HEM 压制都是幂等的(每颗算出的目标值完全相同),增益是
 * 线性叠加(每颗各自遍历一遍),且本元件从不写别的格子,不存在并发修改。
 *
 * <p>
 * <b>依赖:</b>只依赖 IC2 的 {@code ic2.api.reactor.*} 与 GT5U 的两个公开字段;GT5U 已是项目级
 * 硬依赖,IC2 是 GTNH 必装。注册点 {@code CommonProxy} 有 {@code isModLoaded("IC2")} 守卫,
 * 无 IC2 时本类不会被加载。
 */
public class ItemElinarTianxuanCore extends Item implements IReactorComponent {

    public static ItemElinarTianxuanCore instance;

    /** 锁定的堆芯温度比例(占热容上限)。 */
    public static final double LOCKED_HEAT_FRACTION = 0.99D;

    /**
     * 全局增益比例:每根燃料棒每 tick 额外获得"自身产出 × 该值"。
     *
     * <p>
     * 1.0 = 精确翻倍(规划器实测:任何堆型都精确 ×2,与堆里放了多少根棒无关)。
     */
    public static final double BOOST_PERCENT = 1.0D;

    /**
     * 达成 {@link #BOOST_PERCENT} = 1.0 所需的"每格脉冲数"标定值。
     *
     * <p>
     * <b>为什么不是 1:</b>一次 {@code acceptUraniumPulse} 让堆芯 {@code addOutput} 的量是固定的
     * {@code sEnergy}(GT5U 里四联钍棒 ≈ 10 EU/t),而一根棒的自身产出是 {@code sEnergy × 棒数 ×
     * 该元件的脉冲数}。所以"补 1 个脉冲/棒"只等于自身产出的 {@code 1 / 25} 左右,不是 100%。
     *
     * <p>
     * <b>标定来源(2026-09-21 芷若实测):</b>她的 53 根四联钍棒堆,核心每次给每格 1 个脉冲
     * (共 4/棒)时,输出从 13,480 涨到 15,600 EU/t —— 即 <b>一个脉冲 ≈ 10 EU/t/棒</b>,而裸棒
     * 合计约 13,480 EU/t(≈254/根)。要补足 100% 需要 13,480 / 53 / 10 ≈ <b>25.4</b> 个脉冲/棒,
     * 取整 25。
     *
     * <p>
     * <b>注意这是按"燃料棒自身产出"标定的近似:</b>不同燃料棒的 {@code sEnergy} 与棒数比例不同
     * (四联铀、MOX、钍各不相同),所以换堆型时实际百分比会有偏差;要精确对齐版本,拿一根棒实测
     * 一次"裸输出 / 单脉冲增量"再改这个常数即可。
     */
    private static final int PULSES_PER_ROD_PER_FULL_BOOST = 25;

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public ItemElinarTianxuanCore() {
        setUnlocalizedName("elinarTianxuanCore");
        // 1.7.10 的 setTextureName 追加 ".name",所以实际贴图是 tianxuanCore.name.png。
        setTextureName("elinarcurios:tianxuanCore");
        setCreativeTab(ElinarCurios.creativeTab);
        // 与天枢核心不同,本元件不存热、不磨损,可以堆叠(配方需要 9 个)。
        setMaxStackSize(64);
        setMaxDamage(0);
    }

    // ==================================================================
    // 图标(静态单贴图,与天枢核心同族风格)
    // ==================================================================

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icon = reg.registerIcon("elinarcurios:tianxuanCore");
        // 兜底:覆写 registerIcons 后基类 itemIcon 从未被赋值,第三方(NEI 等)走
        // getIconFromDamage(int) 会拿到 null。
        itemIcon = icon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }

    // ==================================================================
    // IReactorComponent —— 元件协议
    // ==================================================================

    /**
     * IC2 每次结算调两趟:pass 0 是 {@code heatrun=true}(热趟)、pass 1 是 {@code heatrun=false}
     * (能量趟);堆芯的 {@code hem} 在每 tick 开头被重置为 1。两个功能都挂在能量趟:
     *
     * <ul>
     * <li>热趟不做事 —— 燃料棒在热趟才会产热;它们的产热量只取决于 {@code pulses}(与其相邻的
     * 反射板/棒),本元件发出的脉冲不进这个公式,所以那几百个脉冲不会带来额外的热;</li>
     * <li>能量趟里锁温、压 HEM(钳制岩浆/辐射/着火/爆炸威力等侧效应)、再发脉冲。</li>
     * </ul>
     *
     * <p>
     * <b>堆停机(红石信号关闭)时必须整个跳过</b>(2026-09-21 芷若实测"关了堆还有输出"):
     * IC2 的 {@code ItemReactorUranium} 是 {@code if (reactor.produceEnergy()) { ...全部... }}、
     * GT5U 的 {@code ItemRadioactiveCellIC} 是开头 {@code if (!reactor.produceEnergy()) return;} ——
     * 燃料棒自己的产 EU 被这道门禁挡住了,但<b>燃料棒的 {@code acceptUraniumPulse} 只看
     * {@code !heatrun}、不看堆开没开</b>。所以本元件若不自己加门禁,就会在停机时继续给每根棒
     * 发脉冲,把 EU 直接累加进堆芯输出 —— 表现为"反应堆关了还有电"。照 IC2 惯例补上即可。
     */
    @Override
    public void processChamber(IReactor reactor, ItemStack stack, int x, int y, boolean heatrun) {
        if (heatrun) return;
        // 堆停机时不锁温、不压 HEM、不发脉冲(与 IC2/GT5U 燃料棒的 produceEnergy 门禁一致)
        if (!reactor.produceEnergy()) return;

        // ---- ① 锁温 + 侧效应归零:温度写成 99%,hem=0 让岩浆/辐射/着火/爆炸威力全部归零 ----
        // 注意:hem 不缩放燃料棒产热(见类注释),所以不炸靠的是产热公式里没有我们的脉冲。
        reactor.setHeat((int) (reactor.getMaxHeat() * LOCKED_HEAT_FRACTION));
        reactor.setHeatEffectModifier(0.0F);

        // ---- ② 全局增益:遍历全堆,给每根燃料棒补"棒数 × BOOST_PERCENT"个脉冲 ----
        if (BOOST_PERCENT <= 0.0D) return;
        for (int gridY = 0; gridY < ROWS; gridY++) {
            for (int gridX = 0; gridX < MAX_COLUMNS; gridX++) {
                boostRod(reactor, gridX, gridY, x, y);
            }
        }
    }

    /** 反应堆最大行数(1.7.10 的核电 GUI 恒为 6 行)。 */
    private static final int ROWS = 6;

    /**
     * 最大列数(3 基础 + 最多 6 个反应堆仓)。
     *
     * <p>
     * 真实 IC2 2.2.828 的 {@code IReactor} <b>没有</b> {@code getReactorSize()}(那是 GTNH 分支的
     * 扩展),但 {@code getItemAt} 会做边界检查、越界返回 null,所以按最大列数遍历并跳过 null
     * 既安全又能覆盖任意仓数。
     */
    private static final int MAX_COLUMNS = 9;

    /**
     * 给一格里的燃料棒补脉冲。
     *
     * @param x 本元件所在列(作为脉冲来源坐标传给燃料棒,IC2 只把它当记账用)
     * @param y 本元件所在行
     */
    private void boostRod(IReactor reactor, int rodX, int rodY, int x, int y) {
        ItemStack rodStack = reactor.getItemAt(rodX, rodY);
        if (rodStack == null || rodStack.getItem() == null) return;
        if (rodStack.getItem() == this) return; // 另一颗元件不是燃料棒
        if (!(rodStack.getItem() instanceof IReactorComponent)) return;

        int rodCount = rodCountOf(rodStack);
        if (rodCount <= 0) return;

        // 每根棒自身产出 ≈ PULSES_PER_ROD_PER_FULL_BOOST 个脉冲的价值,所以按棒数成比例补脉冲:
        // fullBoost 时每格补 25 × rodCount 个;调 BOOST_PERCENT 即按比例缩放,与堆型无关。
        int pulses = (int) Math.round(rodCount * PULSES_PER_ROD_PER_FULL_BOOST * BOOST_PERCENT);
        if (pulses <= 0) return;
        IReactorComponent component = (IReactorComponent) rodStack.getItem();
        ItemStack self = stackOfThis(reactor, x, y);
        // 先试一次:燃料棒耗尽后 acceptUraniumPulse 返回 false 且不再加 EU,这时没必要把
        // 余下的几百次空调用跑完(四联棒满增益是每格 100 次)。
        if (!component.acceptUraniumPulse(reactor, rodStack, self, rodX, rodY, x, y, false)) return;
        for (int i = 1; i < pulses; i++) {
            // 参数顺序照 GT5U 自身 checkPulseable 的调用:被打的元件坐标在前,脉冲源在后。
            component.acceptUraniumPulse(reactor, rodStack, self, rodX, rodY, x, y, false);
        }
    }

    /** 取本元件所在格的 ItemStack(只作脉冲来源记账,拿不到就传 null)。 */
    private static ItemStack stackOfThis(IReactor reactor, int x, int y) {
        ItemStack self = reactor.getItemAt(x, y);
        return self != null && self.getItem() == instance ? self : null;
    }

    /**
     * 读一根燃料棒的棒数(每根单棒的脉冲数)。
     *
     * <p>
     * GT5U 的 {@code ItemRadioactiveCellIC} 公开了 {@code numberOfCells}(public final),
     * IC2 原版的 {@code ItemReactorUranium} 同样有 public field {@code numberOfCells};
     * 两者的单棒脉冲数都等于该值,所以这里反射读同一个字段名即可同时覆盖。
     *
     * @return 棒数;非燃料棒或读不到时返回 0
     */
    public static int rodCountOf(ItemStack rodStack) {
        if (rodStack == null) return 0;
        Item item = rodStack.getItem();
        if (item == null) return 0;
        java.lang.reflect.Field field = rodCountField(item.getClass());
        if (field == null) return 0;
        try {
            Object value = field.get(item);
            if (value instanceof Number) return ((Number) value).intValue();
        } catch (Exception ignored) {
            // 读失败按非燃料棒处理(不刷屏、不影响其它元件)
        }
        return 0;
    }

    /**
     * 按物品类缓存 {@code numberOfCells} 字段的查找结果。
     *
     * <p>
     * 用哨兵值 {@link #NO_FIELD} 表示"查过、没有该字段",<b>不放 null 进 map</b>;早期版本用
     * "值 == null 即未缓存"的写法在实机上抛了
     * {@code NullPointerException at ConcurrentHashMap.putVal}(2026-09-21 芷若的崩溃报告),
     * 这里改成对 null 完全不敏感的写法,并且整个探测过程包在 try/catch 里 —— 读不到就当非燃料棒。
     */
    private static final Object NO_FIELD = new Object();

    private static final java.util.Map<Class<?>, Object> ROD_COUNT_FIELDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static java.lang.reflect.Field rodCountField(Class<?> itemClass) {
        if (itemClass == null) return null;
        Object cached = ROD_COUNT_FIELDS.get(itemClass);
        if (cached == null) {
            cached = NO_FIELD;
            try {
                java.lang.reflect.Field probe = probeRodCountField(itemClass);
                if (probe != null) cached = probe;
            } catch (Throwable ignored) {
                // 任何反射失败都按"没有该字段"处理,绝不让异常冒到堆芯 tick 里去
            }
            ROD_COUNT_FIELDS.put(itemClass, cached);
        }
        return cached instanceof java.lang.reflect.Field ? (java.lang.reflect.Field) cached : null;
    }

    private static java.lang.reflect.Field probeRodCountField(Class<?> itemClass) {
        for (Class<?> c = itemClass; c != null && c != Item.class; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field field = c.getDeclaredField("numberOfCells");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }
        }
        return null;
    }

    /** 不储热(与中子反射板同):燃料棒因此不会把热丢给它。 */
    @Override
    public boolean canStoreHeat(IReactor reactor, ItemStack stack, int x, int y) {
        return false;
    }

    @Override
    public int getMaxHeat(IReactor reactor, ItemStack stack, int x, int y) {
        return 0;
    }

    @Override
    public int getCurrentHeat(IReactor reactor, ItemStack stack, int x, int y) {
        return 0;
    }

    /** 不改热:堆芯热学由 ① 的锁温全权负责。 */
    @Override
    public int alterHeat(IReactor reactor, ItemStack stack, int x, int y, int heat) {
        return heat;
    }

    /**
     * 本元件自己不接受铀脉冲(它不是燃料棒,也没有"被打到就反弹"的反射板行为)。
     *
     * <p>
     * <b>必须返回 false —— 这是本元件安全性的关键不变量,别改成 true</b>(2026-09-21 用 GT5U
     * 字节码核实):燃料棒热趟的脉冲数是
     * {@code pulses = 1 + numberOfCells/2 + checkPulseable(四个邻居)},而 {@code checkPulseable}
     * 只认"相邻且 {@code acceptUraniumPulse} 返回 true"的元件。本元件返回 true 就会被算成中子
     * 反射板,给每根相邻棒的 {@code pulses} 加一 —— 而产热是 {@code triangularNumber(pulses) * sHeat}
     * (平方级),相邻棒会成片升温,可能把堆顶爆。
     *
     * <p>
     * 返回 false 时,{@link #processChamber} 发出的那几百个脉冲<b>只走能量通道
     * (燃料棒的 {@code addOutput})</b>,完全不进入产热公式 —— 这正是本元件能做到"产能翻几倍、
     * 产热几乎不变"的原因。
     */
    @Override
    public boolean acceptUraniumPulse(IReactor reactor, ItemStack stack, ItemStack pulsingStack, int youX, int youY,
        int pulseX, int pulseY, boolean heatrun) {
        return false;
    }

    /** 0 = 不影响爆炸威力(它不隔板,也不该被算成爆炸源)。 */
    @Override
    public float influenceExplosion(IReactor reactor, ItemStack stack) {
        return 0.0F;
    }

    // ==================================================================
    // 显示:创造页 / tooltip
    // ==================================================================

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, 0));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        final String key = "item.elinarTianxuanCore.tooltip.";
        final String value = "" + EnumChatFormatting.YELLOW;

        lines.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocal(key + "lock") + " " + value + (int) (LOCKED_HEAT_FRACTION * 100.0D) + "%");
        lines.add(
            EnumChatFormatting.GRAY + StatCollector
                .translateToLocal(key + "boost") + " " + value + "+" + (int) (BOOST_PERCENT * 100.0D) + "%");
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(key + "safety")
                + " "
                + value
                + StatCollector.translateToLocal(key + "safetyValue"));
        lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(key + "note1"));
        lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(key + "note2"));
        lines.add(
            EnumChatFormatting.GOLD + "" + EnumChatFormatting.ITALIC + StatCollector.translateToLocal(key + "flavor"));
    }
}
