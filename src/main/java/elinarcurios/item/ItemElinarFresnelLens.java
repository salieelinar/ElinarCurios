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
import elinarcurios.Config;
import elinarcurios.ElinarCurios;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;

/**
 * 菲涅尔透镜 —— 全堆均匀中子辐照元件(2026-10-13 应芷若要求新增)。
 *
 * <p>
 * <b>它要解决的问题:</b>GT5U 的增殖棒({@code gregtech.api.items.ItemBreederCell},
 * 阳光化合物棒 / 氚棒)靠"挨中子脉冲"累积进度 —— 每收到一次脉冲进度
 * {@code + 1 + 堆芯热量 / 3000},满 10000 脉冲原地变成产物(阳光化合物棒 / 氚棒)。
 * 而原版 IC2 燃料棒({@code ItemReactorUranium.processChamber})只往<b>自己上下左右四格</b>
 * 各发脉冲,于是"辐射覆盖"既受限于几何布局(贴着棒才吃得到),又因邻居的脉冲强度不同而
 * 各个格子进度参差不齐。本元件把这层几何约束整个拿掉:插进堆里,<b>全堆每一格的增殖元件
 * 都直接吃到中子</b>。
 *
 * <p>
 * <b>实现依据(2026-10-13 用 IC2 2.2.828 与 GT5U 5.09.54.183 的字节码/反编译源码双重核实):</b>
 *
 * <ul>
 * <li><b>堆芯每秒只结算一次、但一次结算走两趟</b>:IC2
 * {@code TileEntityNuclearReactorElectric.updateEntityServer} 每 {@code getTickRate() == 20}
 * 个游戏刻调用一次 {@code processChambers()},后者做两次全网格扫描
 * {@code for (pass = 0; pass &lt; 2; pass++) ... comp.processChamber(this, stack, x, y, pass == 0)}。
 * 也就是说 <b>{@code heatrun} 的真值表是"第 0 趟 = true(热趟)、第 1 趟 = false(能量趟)"</b>。</li>
 * <li><b>增殖进度只在热趟累加</b>:{@code ItemBreederCell.acceptUraniumPulse} 的第一句就是
 * {@code if (heatrun) { ... 累加进度 / 满则 setItemAt 换成产物 ... }},非热趟只会返回
 * {@code deflector}(默认为 false)。所以本元件<b>必须且只能在 {@code heatrun == true} 时发脉冲</b>。</li>
 * <li><b>热趟发脉冲不会附带发电</b>:燃料棒 {@code ItemReactorUranium.acceptUraniumPulse} 的
 * {@code reactor.addOutput(1.0F)} 写在 {@code if (!heatrun)} 里。增殖棒自己则根本不碰
 * {@code addOutput}。于是热趟的脉冲是"纯辐照",堆芯输出一个 EU 都不会多 —— 这与天玄·恒温核心
 * 故意走能量趟刷产能是相反的两条路,别混淆。</li>
 * <li><b>为什么不能给燃料棒发脉冲</b>:燃料棒热趟的脉冲数
 * {@code pulses = 1 + numberOfCells/2 + checkPulseable(四邻)},而 {@code checkPulseable} 只认
 * "四邻里 {@code acceptUraniumPulse} 返回 true"的元件。燃料棒被打到就返回 true → 会被计进
 * {@code pulses} → 产热是 {@code triangularNumber(pulses) * sHeat}(平方级),相邻燃料棒会成片
 * 升温甚至把堆顶爆。本元件因此在<b>热趟刻意跳过燃料棒</b>(但会接住它们打过来的脉冲)。</li>
 * <li><b>为什么也不能给反射板 / 冷却单元 / 散热片发脉冲</b>:它们的热趟分支都有副作用
 * (IC2 反射板每次被脉冲扣损坏度;GT5U 的 {@code ItemNeutronReflector} 会把脉冲转发回燃料棒;
 * 散热片在热趟会真的排热)。本元件的判定条件把它们全部排除,见 {@link #isIrradiationTarget}。</li>
 * </ul>
 *
 * <p>
 * <b>均匀性是怎么保证的:</b>增殖棒的进度只读 {@code reactor.getHeat()} 这个<b>堆芯全局标量</b>
 * (不存在"按格分摊的辐射值"),而本元件既不产热也不吸热({@link #canStoreHeat} =
 * {@code false}、{@link #alterHeat} 原样返回),所以只要堆芯热量不变,全堆每一格的进度就是
 * 逐 tick 完全同步的 —— 从插进去那一刻起一起涨、到 10000 一起变产物。
 *
 * <p>
 * <b>速度:</b>每格每 tick 发 {@link Config#fresnelLensPulsesPerTick} 次脉冲(芷若 2026-10-13
 * 拍板 4),于是每格进度速度 = {@code 4 × (1 + 堆芯热量/3000)}(以 GT5U 口径 3000 为步长),
 * 满 10000 脉冲所需时间 = {@code 10000 / 该速度} 秒。堆芯 0 热量时约 2500 秒全堆同清;
 * 3000 热量时约 1250 秒。换句话说<b>转化速度仍然完全由你堆的堆芯热量决定,透镜只负责
 * "覆盖全堆 + 完全均匀"</b>,不改 GT 的数值平衡。
 *
 * <p>
 * <b>红石门禁:不加</b>。IC2 燃料棒与 GT5U 燃料棒都在 {@code processChamber} 开头检查
 * {@code reactor.produceEnergy()}(红石开机才发电),但增殖棒自己不检查;本透镜是对外辐照,
 * 与堆开不开机无关 —— 停机状态下热趟照常发生、堆芯热量也不产不涨,所以关堆时进度速度与
 * 开堆完全一致,不会出现"关着堆偷偷长进度"的不一致。
 *
 * <p>
 * <b>多颗并存:</b>每颗透镜各自遍历一遍全堆,脉冲次数线性叠加(放 N 颗 = 每格每 tick
 * {@code 4N} 次)。放几颗由玩家决定,不做抑制。
 *
 * <p>
 * <b>依赖:</b>只依赖 IC2 的 {@code ic2.api.reactor.*},IC2 是 GTNH 必装;注册点
 * {@code CommonProxy} 有 {@code isModLoaded("IC2")} 守卫,无 IC2 时本类不会被加载。
 */
public class ItemElinarFresnelLens extends Item implements IReactorComponent {

    public static ItemElinarFresnelLens instance;

    /** 反应堆最大行数(1.7.10 的核电 GUI 恒为 6 行)。 */
    private static final int ROWS = 6;

    /**
     * 最大列数(3 基础 + 最多 6 个反应堆仓)。
     *
     * <p>
     * 真实 IC2 2.2.828 的 {@code IReactor} <b>没有</b> {@code getReactorSize()}(那是 GTNH 分支的
     * 扩展),而 {@code getItemAt} 自带边界检查、越界返回 null,所以按最大列数遍历并跳过 null
     * 既安全又能覆盖任意仓数(与天玄·恒温核心同一套做法,已在实机验证)。
     */
    private static final int MAX_COLUMNS = 9;

    @SideOnly(Side.CLIENT)
    private IIcon icon;

    public ItemElinarFresnelLens() {
        setUnlocalizedName("elinarFresnelLens");
        setTextureName("elinarcurios:fresnelLens");
        setCreativeTab(ElinarCurios.creativeTab);
        // 本元件不存任何状态(不存热、不磨损),所以可以堆叠。
        setMaxStackSize(64);
        setMaxDamage(0);
    }

    // ==================================================================
    // 图标(静态单贴图)
    // ==================================================================

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {
        icon = reg.registerIcon("elinarcurios:fresnelLens");
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
     * 只做一件事:<b>热趟</b>里给全堆每一格的增殖元件各发 {@code N} 次中子脉冲。
     *
     * @param heatrun 第 0 趟为 true(热趟,增殖进度在这里累加)、第 1 趟为 false(能量趟)
     */
    @Override
    public void processChamber(IReactor reactor, ItemStack stack, int x, int y, boolean heatrun) {
        // 非热趟直接走人:增殖棒在非热趟不累加进度,而且此刻发脉冲会经由燃料棒的
        // addOutput 变成白送的 EU(见类注释)。
        if (!heatrun) return;

        final int pulses = Config.fresnelLensPulsesPerTick;
        if (pulses <= 0) return;

        for (int gridY = 0; gridY < ROWS; gridY++) {
            for (int gridX = 0; gridX < MAX_COLUMNS; gridX++) {
                irradiate(reactor, gridX, gridY, x, y, pulses);
            }
        }
    }

    /**
     * 给一格里的增殖元件补脉冲。
     *
     * @param targetX 被打的那一格坐标
     * @param x       本透镜所在列(作为脉冲来源坐标传给对方,IC2 只把它当记账用)
     */
    private void irradiate(IReactor reactor, int targetX, int targetY, int x, int y, int pulses) {
        ItemStack target = reactor.getItemAt(targetX, targetY);
        if (target == null || target.getItem() == null) return;
        if (!(target.getItem() instanceof IReactorComponent)) return;
        if (!isIrradiationTarget(reactor, target, targetX, targetY)) return;

        IReactorComponent component = (IReactorComponent) target.getItem();
        ItemStack self = stackOfThis(reactor, x, y);
        for (int i = 0; i < pulses; i++) {
            // 参数顺序照 IC2 自身 checkPulseable 的调用:被打的元件坐标在前,脉冲源在后。
            component.acceptUraniumPulse(reactor, target, self, targetX, targetY, x, y, true);
        }
    }

    /**
     * 判定"这一格是不是该吃辐照的增殖元件"。
     *
     * <p>
     * 判据是<b>结构性的、不认类名</b>:{@code canStoreHeat == false}(不吃热、不是冷却单元/散热片/
     * 热交换器)且 {@code getMaxHeat == 0}(不是任何热容类元件)。GT5U 的增殖棒
     * ({@code ItemBreederCell})、IC2 的锂增殖棒以及 NuclearHorizons 的增殖棒都满足这两条,
     * 而燃料棒、中子反射板、冷却单元、散热片、反应堆隔板全部被排除在外。
     *
     * <p>
     * <b>为什么这道判据是安全的关键:</b>燃料棒被打到会返回 true,从而被算进相邻燃料棒的
     * {@code pulses}(产热平方级上涨,可能爆堆);IC2 反射板被打到会扣损坏度并在耗尽时消失;
     * GT5U 反射板会把脉冲转发回燃料棒。这三类元件在热趟都有副作用,必须一个都不能碰。
     *
     * <p>
     * 每次调用都可能抛异常(第三方元件实现不可控),所以整段用 {@code catch Throwable} 包住 ——
     * 堆芯 tick 里抛异常会直接把游戏打崩,这里按"不是目标"静默跳过。
     */
    private static boolean isIrradiationTarget(IReactor reactor, ItemStack target, int x, int y) {
        if (!(target.getItem() instanceof IReactorComponent)) return false;
        IReactorComponent component = (IReactorComponent) target.getItem();
        try {
            if (component.canStoreHeat(reactor, target, x, y)) return false;
            if (component.getMaxHeat(reactor, target, x, y) != 0) return false;
        } catch (Throwable ignored) {
            return false;
        }
        return true;
    }

    /** 取本透镜所在格的 ItemStack(只作脉冲来源记账,拿不到就传 null)。 */
    private static ItemStack stackOfThis(IReactor reactor, int x, int y) {
        ItemStack self = reactor.getItemAt(x, y);
        return self != null && self.getItem() == instance ? self : null;
    }

    /** 本透镜不储热:燃料棒因此不会把热摊给它(它也不该吃热)。 */
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

    /** 不改热:堆芯热学与本元件无关(均匀性正建立在"透镜不动堆芯热量"之上)。 */
    @Override
    public int alterHeat(IReactor reactor, ItemStack stack, int x, int y, int heat) {
        return heat;
    }

    /**
     * 接住别人打过来的脉冲,但<b>一律返回 false</b>。
     *
     * <p>
     * 返回 false 有两个作用:①本元件不会被算作中子反射板给相邻燃料棒加 {@code pulses};
     * ②燃料棒的 {@code checkPulseable} 看到 false 后不会再往这里转发,脉冲链到此为止。
     * 它自身也没有任何"被打到就累积"的状态 —— 它是一个透镜,不是靶子。
     */
    @Override
    public boolean acceptUraniumPulse(IReactor reactor, ItemStack stack, ItemStack pulsingStack, int youX, int youY,
        int pulseX, int pulseY, boolean heatrun) {
        return false;
    }

    /** 0 = 不影响爆炸威力。 */
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
        final String key = "item.elinarFresnelLens.tooltip.";
        final String value = "" + EnumChatFormatting.YELLOW;

        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(key + "pulses")
                + " "
                + value
                + Config.fresnelLensPulsesPerTick
                + " "
                + StatCollector.translateToLocal(key + "pulsesUnit"));
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(key + "coverage")
                + " "
                + value
                + StatCollector.translateToLocal(key + "coverageValue"));
        lines.add(
            EnumChatFormatting.GRAY + StatCollector.translateToLocal(key + "cost")
                + " "
                + value
                + StatCollector.translateToLocal(key + "costValue"));
        // 带参数的这条必须走 translateToLocalFormatted:%s 在 translateToLocal 下会原样显示。
        lines.add(
            EnumChatFormatting.DARK_GRAY
                + StatCollector.translateToLocalFormatted(key + "note1", Config.fresnelLensPulsesPerTick));
        lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(key + "note2"));
        lines.add(EnumChatFormatting.DARK_GRAY + StatCollector.translateToLocal(key + "note3"));
        lines.add(
            EnumChatFormatting.GOLD + "" + EnumChatFormatting.ITALIC + StatCollector.translateToLocal(key + "flavor"));
    }
}
