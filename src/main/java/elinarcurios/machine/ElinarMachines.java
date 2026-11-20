package elinarcurios.machine;

import net.minecraft.item.ItemStack;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * ElinarCurios 的 GT 机器注册。
 *
 * <p>
 * GT 机器 ID 是硬编码 + 先到先得的共享空间（上限 {@code GregTechAPI.MAXIMUM_METATILE_IDS = 32766}）。
 * 2026-11-14 因 TST 0.8.1 抢占了旧号（19082/19083），全部机器整段搬到 <b>23100-23112</b>
 * （23100-23199 实测为空，见 {@code D:\Agent\GTNH\ref\mte-id-map-2026-11-14.md}）：
 * <ul>
 * <li>{@link #ID_VOID_NODE} = 23100 —— 天玄虚空能量节点（旧号 19082）</li>
 * <li>{@link #ID_INPUT_HATCH_ADJUSTABLE} = 23101 —— 可调式流体输入仓（旧号 19083）</li>
 * <li>{@link #ID_COMPREHENSIVE_MINING_PLANT} = 23102 —— 综合采掘工厂（旧号 19084）</li>
 * <li>{@link #ID_PATTERN_REDSTONE_DETECTOR} = 23103 —— 样板总成侦测器（旧号 19085）</li>
 * <li>{@link #ID_BEAM_FLUX_MULTIPLIER} = 23104 —— 束流倍流器（旧号 19086）</li>
 * <li>{@link #ID_BEAM_VOID_INPUT_HATCH} = 23105 —— 束流虚空输入仓（旧号 19087）</li>
 * <li>{@link #ID_BEAM_VOID_STORAGE} = 23106 —— 束流虚空存储器（旧号 19088）</li>
 * <li>{@link #ID_BEAM_VOID_OUTPUT_HATCH} = 23107 —— 束流虚空输出仓（旧号 19089）</li>
 * <li>{@link #ID_BEAM_VOID_INPUT_CHAMBER} = 23108 —— 束流虚空输入室（旧号 19090）</li>
 * <li>{@link #ID_MIRROR_ITEM} = 23109 —— 物品总成镜像（旧号 19102）</li>
 * <li>{@link #ID_MIRROR_FLUID} = 23110 —— 流体总成镜像（旧号 19103）</li>
 * <li>{@link #ID_ADVANCED_RADIO_HATCH} = 23111 —— 高级放射仓（旧号 19104）</li>
 * <li>{@link #ID_HIGH_TIER_ASSEMBLY_LINE} = 23112 —— 高级装配线（旧号 32030）</li>
 * </ul>
 * v1.0.0c2 / c2a 是过渡版（新旧号双注册 + {@code /Elinar idmigrate}）；v1.0.0c3 起
 * <b>只注册新号</b>，旧号仅留在 {@link #ID_MAP} 里给迁移/校验逻辑识别。
 * 取号前请实测 FullPack 全库是否已有同号（参考 {@code D:\Agent\GTNH\ref\mte-id-survey.txt}）。
 *
 * <p>
 * ★ 两个镜像方块原本是独立验证工程 ALAPattern（2026-10-13 立项）里的原型，2026-10-16 并入本工程。
 * <b>mName 绝不能改</b> —— 存档认领靠的是 "MTE ID + mName" 这一对。
 */
public final class ElinarMachines {

    /** 天玄虚空能量节点（旧号 19082；2026-11-14 因 TST 0.8.1 撞号迁到 23100）。 */
    public static final int ID_VOID_NODE = 23100;

    /** 物品总成镜像 —— 占装配线的一条输入总线位，对外只提供样板里"第 k 格物品"（旧号 19102）。 */
    public static final int ID_MIRROR_ITEM = 23109;

    /** 流体总成镜像 —— 占装配线的一个输入仓位置，对外只提供样板里"第 m 格流体"（旧号 19103）。 */
    public static final int ID_MIRROR_FLUID = 23110;

    /** 高级装配线（旧号 32030）。 */
    public static final int ID_HIGH_TIER_ASSEMBLY_LINE = 23112;

    /** 综合采掘工厂（旧号 19084）。 */
    public static final int ID_COMPREHENSIVE_MINING_PLANT = 23102;

    /** 高级放射仓（旧号 19104）。tier 3 = HV，与最基础的原版放射仓同档（贴图完全一致）。 */
    public static final int ID_ADVANCED_RADIO_HATCH = 23111;

    /** 样板总成侦测器（旧号 19085）—— 绑定一台样板输入总成，按总成里有没有料发红石信号。 */
    public static final int ID_PATTERN_REDSTONE_DETECTOR = 23103;

    /** 束流倍流器（旧号 19086）—— 1×1 单方块，把束流通量乘 1~32768 倍后从背面转发。 */
    public static final int ID_BEAM_FLUX_MULTIPLIER = 23104;

    /** 束流倍流器的 mName；lang 键为 {@code gt.blockmachines.<mName>.name}。 */
    public static final String NAME_BEAM_FLUX_MULTIPLIER = "elinar.machine.beam_flux_multiplier";

    /** 束流线是 ZPM 阶段内容，机器也取 ZPM 档（tier 只影响外壳颜色与扳手等级）。 */
    public static final int TIER_BEAM_FLUX_MULTIPLIER = 7;

    /** 束流虚空四件套（2026-11-14 从独立验证版 ElinarBeamVoid 并入；旧号 19087-19090）。 */
    public static final int ID_BEAM_VOID_INPUT_HATCH = 23105;

    public static final int ID_BEAM_VOID_STORAGE = 23106;

    public static final int ID_BEAM_VOID_OUTPUT_HATCH = 23107;

    public static final int ID_BEAM_VOID_INPUT_CHAMBER = 23108;

    public static final String NAME_BEAM_VOID_INPUT_HATCH = "elinar.machine.beam_void_input_hatch";

    public static final String NAME_BEAM_VOID_STORAGE = "elinar.machine.beam_void_storage";

    public static final String NAME_BEAM_VOID_OUTPUT_HATCH = "elinar.machine.beam_void_output_hatch";

    public static final String NAME_BEAM_VOID_INPUT_CHAMBER = "elinar.machine.beam_void_input_chamber";

    /** 输入仓 / 输入室 / 存储器对齐原版过滤型出束仓（UV=8）；输出仓对齐原版束流输入仓（LuV=6）。 */
    public static final int TIER_BEAM_VOID_INPUT_HATCH = 8;

    public static final int TIER_BEAM_VOID_STORAGE = 8;

    public static final int TIER_BEAM_VOID_OUTPUT_HATCH = 6;

    public static final int TIER_BEAM_VOID_INPUT_CHAMBER = 8;

    /** 可调式流体输入仓（旧号 19083）。 */
    public static final int ID_INPUT_HATCH_ADJUSTABLE = 23101;

    /** 可调式流体输入仓的 mName；lang 键为 {@code gt.blockmachines.<mName>.name}。 */
    public static final String NAME_INPUT_HATCH_ADJUSTABLE = "elinar.machine.input_hatch_adjustable";

    /** 可调式流体输入仓取低压（LV，tier 1）—— 2026-10-20 拍板：tier 只决定外壳颜色，容量由 GUI 设。 */
    public static final int TIER_INPUT_HATCH_ADJUSTABLE = 1;

    /** 样板总成侦测器的 mName；lang 键为 {@code gt.blockmachines.<mName>.name}。 */
    public static final String NAME_PATTERN_REDSTONE_DETECTOR = "elinar.machine.pattern_redstone_detector";

    /**
     * 侦测器是纯工具方块，取 LV 档：外壳颜色最基础、扳手等级 WrenchLevel1。
     * （它不发/不输电，tier 只影响外观与拆解工具。）
     */
    public static final int TIER_PATTERN_REDSTONE_DETECTOR = 1;

    public static final String NAME_COMPREHENSIVE_MINING_PLANT = "elinar.machine.comprehensive_mining_plant";

    /** 高级放射仓的 mName；lang 键为 {@code gt.blockmachines.<mName>.name}。 */
    public static final String NAME_ADVANCED_RADIO_HATCH = "elinar.machine.radio_hatch_advanced";

    /**
     * 高级放射仓的机壳等级 = HV（tier 3），与最基础的原版放射仓同档 ——「外观材质采用原版的」
     * 就是靠这一档让贴图完全一致（放射仓的 tier 只决定贴图颜色，不影响细菌培养缸等结构）。
     */
    public static final int TIER_ADVANCED_RADIO_HATCH = 3;

    /** lang 键：{@code gt.blockmachines.celestinull.name}（由 MTE 的 mName 自动拼接）。 */
    public static final String LANG_KEY_VOID_NODE = "gt.blockmachines.celestinull.name";

    /**
     * 两个镜像的 mName。★<b>沿用 ALAPattern 原型里的值，绝不可改</b> —— 见类注释里的存档兼容说明；
     * 对应的 lang 键是 {@code gt.blockmachines.<mName>.name}，两个 lang 文件里都已有这两条。
     */
    public static final String NAME_MIRROR_ITEM = "alap_pattern_mirror_item";

    public static final String NAME_MIRROR_FLUID = "alap_pattern_mirror_fluid";

    /** 高级装配线的 mName —— 与独立验证版一致，lang 键为 {@code gt.blockmachines.<mName>.name}。 */
    public static final String NAME_HIGH_TIER_ASSEMBLY_LINE = "elinar.machine.high_tier_assemblyline";

    /** 装配线是 LuV+ 的机器，镜像统一取 IV 档（tier 6）—— 覆盖绝大多数装配线配方，且不吃满档贴图。 */
    public static final int TIER_MIRROR = 6;

    /**
     * 旧号 → 新号。
     *
     * <p>
     * v1.0.0c3 起旧号<b>不再注册</b>（腾给 TST），这张表只给迁移/校验逻辑识别：
     * 物品 damage 修正、{@code /Elinar idcheck} 报旧号残留、锁死机器的提示。
     */
    public static final java.util.Map<Integer, Integer> ID_MAP;

    static {
        final java.util.Map<Integer, Integer> map = new java.util.LinkedHashMap<>();
        map.put(19082, ID_VOID_NODE);
        map.put(19083, ID_INPUT_HATCH_ADJUSTABLE);
        map.put(19084, ID_COMPREHENSIVE_MINING_PLANT);
        map.put(19085, ID_PATTERN_REDSTONE_DETECTOR);
        map.put(19086, ID_BEAM_FLUX_MULTIPLIER);
        map.put(19087, ID_BEAM_VOID_INPUT_HATCH);
        map.put(19088, ID_BEAM_VOID_STORAGE);
        map.put(19089, ID_BEAM_VOID_OUTPUT_HATCH);
        map.put(19090, ID_BEAM_VOID_INPUT_CHAMBER);
        map.put(19102, ID_MIRROR_ITEM);
        map.put(19103, ID_MIRROR_FLUID);
        map.put(19104, ID_ADVANCED_RADIO_HATCH);
        map.put(32030, ID_HIGH_TIER_ASSEMBLY_LINE);
        ID_MAP = java.util.Collections.unmodifiableMap(map);
    }

    /**
     * 这个 ItemStack 是不是本 mod 注册的 GT 机器。
     *
     * <p>
     * 机器物品的注册名归 GT（{@code gregtech}），所以 tooltip 标识不能靠 {@code GameRegistry} 的
     * modid 判断。这里用「物品是 GT 机器块的物品形态」+「该 meta 上的 MTE 是本 mod 包里的类」双重判断：
     * <ul>
     * <li>只认 {@code sBlockMachines} 的物品 —— 否则同号 meta 的其它 GT 元物品会被误伤；</li>
     * <li>MTE 类名落在 {@code elinarcurios.} 下 —— 不写死 13 个号，以后加机器自动覆盖。</li>
     * </ul>
     * 只读，不碰任何实例状态。
     */
    public static boolean isOwnMachineStack(final ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() != net.minecraft.item.Item.getItemFromBlock(gregtech.api.GregTechAPI.sBlockMachines))
            return false;
        final gregtech.api.interfaces.metatileentity.IMetaTileEntity[] mtes = gregtech.api.GregTechAPI.METATILEENTITIES;
        final int id = stack.getItemDamage();
        if (id < 0 || id >= mtes.length) return false;
        final IMetaTileEntity mte = mtes[id];
        return mte != null && mte.getClass()
            .getName()
            .startsWith("elinarcurios.");
    }

    private static IMetaTileEntity voidNode;
    private static IMetaTileEntity mirrorItem;
    private static IMetaTileEntity mirrorFluid;
    private static IMetaTileEntity highTierAssemblyLine;
    private static IMetaTileEntity comprehensiveMiningPlant;
    private static IMetaTileEntity advancedRadioHatch;
    private static IMetaTileEntity patternRedstoneDetector;
    private static IMetaTileEntity beamFluxMultiplier;
    private static IMetaTileEntity inputHatchAdjustable;
    private static IMetaTileEntity beamVoidInputHatch;
    private static IMetaTileEntity beamVoidStorage;
    private static IMetaTileEntity beamVoidOutputHatch;
    private static IMetaTileEntity beamVoidInputChamber;

    private ElinarMachines() {}

    /**
     * 注册全部机器。必须在 GT 的 load 阶段（{@code GregTechAPI.sPreloadStarted && !sPostloadStarted}）
     * 调用 —— MTE 的构造器本身就会做 ID 占用检查，阶段不对会抛 {@link IllegalAccessError}，
     * 号被别人占了会抛 {@link IllegalArgumentException}。
     */
    public static void register() {
        voidNode = new MTECelestinullVoidNode(ID_VOID_NODE, "celestinull", "天玄虚空能量节点", 14);
        mirrorItem = new MTEHatchPatternMirrorItem(ID_MIRROR_ITEM, NAME_MIRROR_ITEM, "物品总成镜像", TIER_MIRROR);
        mirrorFluid = new MTEHatchPatternMirrorFluid(ID_MIRROR_FLUID, NAME_MIRROR_FLUID, "流体总成镜像", TIER_MIRROR);
        comprehensiveMiningPlant = new MTEComprehensiveMiningPlant(
            ID_COMPREHENSIVE_MINING_PLANT,
            NAME_COMPREHENSIVE_MINING_PLANT,
            "综合采掘工厂");
        highTierAssemblyLine = new MTEHighTierAssemblyLine(
            ID_HIGH_TIER_ASSEMBLY_LINE,
            NAME_HIGH_TIER_ASSEMBLY_LINE,
            "高级装配线");
        advancedRadioHatch = new MTEAdvancedRadioHatch(
            ID_ADVANCED_RADIO_HATCH,
            NAME_ADVANCED_RADIO_HATCH,
            "高级放射仓",
            TIER_ADVANCED_RADIO_HATCH);
        patternRedstoneDetector = new MTEPatternRedstoneDetector(
            ID_PATTERN_REDSTONE_DETECTOR,
            NAME_PATTERN_REDSTONE_DETECTOR,
            "样板总成侦测器",
            TIER_PATTERN_REDSTONE_DETECTOR);
        beamFluxMultiplier = new MTEBeamFluxMultiplier(
            ID_BEAM_FLUX_MULTIPLIER,
            NAME_BEAM_FLUX_MULTIPLIER,
            "束流倍流器",
            TIER_BEAM_FLUX_MULTIPLIER);
        inputHatchAdjustable = new MTEHatchInputAdjustable(
            ID_INPUT_HATCH_ADJUSTABLE,
            NAME_INPUT_HATCH_ADJUSTABLE,
            "可调式流体输入仓",
            TIER_INPUT_HATCH_ADJUSTABLE);
        beamVoidInputHatch = new MTEHatchBeamVoidInput(
            ID_BEAM_VOID_INPUT_HATCH,
            NAME_BEAM_VOID_INPUT_HATCH,
            "束流虚空输入仓",
            TIER_BEAM_VOID_INPUT_HATCH);
        beamVoidStorage = new MTEBeamVoidStorage(
            ID_BEAM_VOID_STORAGE,
            NAME_BEAM_VOID_STORAGE,
            "束流虚空存储器",
            TIER_BEAM_VOID_STORAGE);
        beamVoidOutputHatch = new MTEHatchBeamVoidOutput(
            ID_BEAM_VOID_OUTPUT_HATCH,
            NAME_BEAM_VOID_OUTPUT_HATCH,
            "束流虚空输出仓",
            TIER_BEAM_VOID_OUTPUT_HATCH);
        beamVoidInputChamber = new MTEBeamVoidInputChamber(
            ID_BEAM_VOID_INPUT_CHAMBER,
            NAME_BEAM_VOID_INPUT_CHAMBER,
            "束流虚空输入室",
            TIER_BEAM_VOID_INPUT_CHAMBER);
    }

    /** 天玄虚空能量节点的物品形态（用于配方输出）。未注册成功时返回 null。 */
    public static ItemStack getVoidNode(int amount) {
        final IMetaTileEntity mte = voidNode;
        if (mte == null) return null;
        return mte.getStackForm(amount);
    }

    /** 物品总成镜像的物品形态。未注册成功时返回 null。 */
    public static IMetaTileEntity getMirrorItem() {
        return mirrorItem;
    }

    /** 流体总成镜像的物品形态。未注册成功时返回 null。 */
    public static IMetaTileEntity getMirrorFluid() {
        return mirrorFluid;
    }

    /** 高级装配线的物品形态。未注册成功时返回 null。 */
    public static IMetaTileEntity getHighTierAssemblyLine() {
        return highTierAssemblyLine;
    }

    /** 高级放射仓的物品形态。未注册成功时返回 null。 */
    public static IMetaTileEntity getAdvancedRadioHatch() {
        return advancedRadioHatch;
    }

    /** 样板总成侦测器的物品形态。未注册成功时返回 null。 */
    public static IMetaTileEntity getPatternRedstoneDetector() {
        return patternRedstoneDetector;
    }

    /** 束流倍流器的物品形态。未注册成功时返回 null。 */
    public static IMetaTileEntity getBeamFluxMultiplier() {
        return beamFluxMultiplier;
    }

    /** 可调式流体输入仓的物品形态。未注册成功时返回 null。 */
    public static IMetaTileEntity getInputHatchAdjustable() {
        return inputHatchAdjustable;
    }

    public static IMetaTileEntity getBeamVoidInputHatch() {
        return beamVoidInputHatch;
    }

    public static IMetaTileEntity getBeamVoidStorage() {
        return beamVoidStorage;
    }

    public static IMetaTileEntity getBeamVoidOutputHatch() {
        return beamVoidOutputHatch;
    }

    public static IMetaTileEntity getBeamVoidInputChamber() {
        return beamVoidInputChamber;
    }

    /** 综合采掘工厂配方：8 个流体钻机 IV 围 1 个虚空采矿场 I。 */
    public static void registerRecipes() {
        net.minecraft.item.ItemStack voidMiner = null;
        for (int id = 0; id < gregtech.api.GregTechAPI.METATILEENTITIES.length; id++) {
            if (gregtech.api.GregTechAPI.METATILEENTITIES[id] != null
                && gregtech.api.GregTechAPI.METATILEENTITIES[id].getClass()
                    .getName()
                    .endsWith("MTEVoidMiners$VMLUV")) {
                voidMiner = new net.minecraft.item.ItemStack(gregtech.api.GregTechAPI.sBlockMachines, 1, id);
                break;
            }
        }
        net.minecraft.item.ItemStack oilDrill = gregtech.api.enums.ItemList.OilDrill4.get(1);
        if (voidMiner != null && oilDrill != null) {
            gregtech.api.util.GTModHandler.addCraftingRecipe(
                new net.minecraft.item.ItemStack(
                    gregtech.api.GregTechAPI.sBlockMachines,
                    1,
                    ID_COMPREHENSIVE_MINING_PLANT),
                new Object[] { "AAA", "ABA", "AAA", 'A', oilDrill, 'B', voidMiner });
        }

        // 高级放射仓：9 个原版放射仓（任意等级）→ 1 个。
        elinarcurios.recipe.AdvancedRadioHatchRecipes.register();

        // 样板总成侦测器：9 个红石火把（2026-11-07 芷若拍板）。
        elinarcurios.recipe.PatternRedstoneDetectorRecipes.register();

        // 束流倍流器：4 束流管 + 4 ZPM 机壳 + 1 ZPM 发射器（默认方案，2026-11-07）。
        elinarcurios.recipe.BeamFluxMultiplierRecipes.register();

        // 束流虚空四件套（2026-11-14 并入）：输入仓 / 存储器 / 输出仓 / 输入室。
        elinarcurios.recipe.BeamVoidRecipes.register();

        // 可调式流体输入仓：9 个原版输入仓（任意电压）→ 1 个。
        elinarcurios.recipe.AdjustableInputHatchRecipes.register();
    }
}
