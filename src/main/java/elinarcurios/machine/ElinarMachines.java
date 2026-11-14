package elinarcurios.machine;

import net.minecraft.item.ItemStack;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

/**
 * ElinarCurios 的 GT 机器注册。
 *
 * <p>
 * GT 机器 ID 是硬编码 + 先到先得的共享空间（上限 {@code GregTechAPI.MAXIMUM_METATILE_IDS = 32766}），
 * 本 mod 目前占以下号：
 * <ul>
 * <li>{@link #ID_VOID_NODE} = 19082 —— 天玄虚空能量节点（预留 19082-19101 共 20 个号给后续档位/变体）</li>
 * <li>{@link #ID_COMPREHENSIVE_MINING_PLANT} = 19084 —— 综合采掘工厂</li>
 * <li>{@link #ID_PATTERN_REDSTONE_DETECTOR} = 19085 —— 样板总成侦测器</li>
 * <li>{@link #ID_BEAM_FLUX_MULTIPLIER} = 19086 —— 束流倍流器</li>
 * <li>{@link #ID_BEAM_VOID_INPUT_HATCH} = 19087 —— 束流虚空输入仓</li>
 * <li>{@link #ID_BEAM_VOID_STORAGE} = 19088 —— 束流虚空存储器</li>
 * <li>{@link #ID_BEAM_VOID_OUTPUT_HATCH} = 19089 —— 束流虚空输出仓</li>
 * <li>{@link #ID_BEAM_VOID_INPUT_CHAMBER} = 19090 —— 束流虚空输入室</li>
 * <li>{@link #ID_MIRROR_ITEM} = 19102 —— 物品总成镜像</li>
 * <li>{@link #ID_MIRROR_FLUID} = 19103 —— 流体总成镜像</li>
 * <li>{@link #ID_ADVANCED_RADIO_HATCH} = 19104 —— 高级放射仓</li>
 * <li>{@link #ID_HIGH_TIER_ASSEMBLY_LINE} = 32030 —— 高级装配线（沿用独立验证版运行时扫描到的号）</li>
 * </ul>
 * 取号前请实测 FullPack 全库是否已有同号（产物参考 {@code D:\Agent\GTNH\ref\mte-id-survey.txt}），
 * 或用 JVM 参数 {@code -Dgt.debug=true} 启动一次看完整占用清单。
 *
 * <p>
 * ★ 两个镜像方块原本是独立验证工程 ALAPattern（2026-10-13 立项）里的原型，2026-10-16 并入本工程。
 * **ID 与 mName 一律照搬原型，绝不能改** —— 芷若的主存档里已经放好了这些方块，认领靠的就是
 * "MTE ID + mName" 这一对；改了会让已放置的方块变成死块、内部物品不可逆丢失。
 */
public final class ElinarMachines {

    /** 天玄虚空能量节点。19082 在 2026-09-12 实测为空（TST 占 18791-19081，GT5U 高位区 32022+）。 */
    public static final int ID_VOID_NODE = 19082;

    /** 物品总成镜像 —— 占装配线的一条输入总线位，对外只提供样板里"第 k 格物品"。 */
    public static final int ID_MIRROR_ITEM = 19102;

    /** 流体总成镜像 —— 占装配线的一个输入仓位置，对外只提供样板里"第 m 格流体"。 */
    public static final int ID_MIRROR_FLUID = 19103;

    /**
     * 高级装配线。32030 来自独立验证版 ElinarAssemblyLine 0.1.1 的运行时扫描结果（2026-10-18 日志实测），
     * 保留 ID + mName 与验证版一致，避免她测试存档里已放置的方块变成死块。
     */
    public static final int ID_HIGH_TIER_ASSEMBLY_LINE = 32030;

    /** 综合采掘工厂（独立验证版 0.2.10 的 ID）。 */
    public static final int ID_COMPREHENSIVE_MINING_PLANT = 19084;

    /**
     * 高级放射仓（2026-10-31 新增）。
     *
     * <p>
     * 19104 在 RC-1 全库源码扫描中无占用；19083 是独立验证版 ElinarInputHatch 的号，
     * 将来并入本工程时也不能撞，所以高级放射仓从 19104 起取。
     */
    public static final int ID_ADVANCED_RADIO_HATCH = 19104;

    /**
     * 样板总成侦测器（2026-11-07 新增）—— 绑定一台样板输入总成，按总成里有没有料发红石信号。
     *
     * <p>
     * 19085 落在 19082 的预留区里（19083 是独立工程 ElinarInputHatch、19084 是综合采掘工厂）。
     * FullPack 全库源码扫描无 MTE 构造器占用；若实机首启报 ID 冲突，会在注册阶段直接抛异常
     * （不坏存档，只是本方块注册失败）。
     */
    public static final int ID_PATTERN_REDSTONE_DETECTOR = 19085;

    /**
     * 束流倍流器（2026-11-07 新增）—— 1×1 单方块，把束流通量（rate）乘 1~32768 倍后从背面转发。
     *
     * <p>
     * 19086 落在 19082 的预留区里（19083 独立工程、19084 采掘、19085 侦测器），FullPack 全库源码扫描
     * 无 MTE 构造器占用；若实机首启报 ID 冲突，注册阶段会直接抛异常（不坏存档，只是本方块注册失败）。
     */
    public static final int ID_BEAM_FLUX_MULTIPLIER = 19086;

    /** 束流倍流器的 mName；lang 键为 {@code gt.blockmachines.<mName>.name}。 */
    public static final String NAME_BEAM_FLUX_MULTIPLIER = "elinar.machine.beam_flux_multiplier";

    /** 束流线是 ZPM 阶段内容，机器也取 ZPM 档（tier 只影响外壳颜色与扳手等级）。 */
    public static final int TIER_BEAM_FLUX_MULTIPLIER = 7;

    /**
     * 束流虚空四件套（2026-11-14 从独立验证版 ElinarBeamVoid 并入）。
     *
     * <p>
     * 19087~19090 落在 19082 的预留区里；独立验证版实机跑过（启动 / 输入侧 / 绑定 / 界面），
     * 并入时 <b>ID 与 mName 原样照搬</b> —— 她在测试存档里已经放过这些方块。
     */
    public static final int ID_BEAM_VOID_INPUT_HATCH = 19087;

    public static final int ID_BEAM_VOID_STORAGE = 19088;

    public static final int ID_BEAM_VOID_OUTPUT_HATCH = 19089;

    public static final int ID_BEAM_VOID_INPUT_CHAMBER = 19090;

    public static final String NAME_BEAM_VOID_INPUT_HATCH = "elinar.machine.beam_void_input_hatch";

    public static final String NAME_BEAM_VOID_STORAGE = "elinar.machine.beam_void_storage";

    public static final String NAME_BEAM_VOID_OUTPUT_HATCH = "elinar.machine.beam_void_output_hatch";

    public static final String NAME_BEAM_VOID_INPUT_CHAMBER = "elinar.machine.beam_void_input_chamber";

    /** 输入仓 / 输入室 / 存储器对齐原版过滤型出束仓（UV=8）；输出仓对齐原版束流输入仓（LuV=6）。 */
    public static final int TIER_BEAM_VOID_INPUT_HATCH = 8;

    public static final int TIER_BEAM_VOID_STORAGE = 8;

    public static final int TIER_BEAM_VOID_OUTPUT_HATCH = 6;

    public static final int TIER_BEAM_VOID_INPUT_CHAMBER = 8;

    /** 可调式流体输入仓的 ID —— 沿用独立验证版 ElinarInputHatch 的号（19083），绝不改。 */
    public static final int ID_INPUT_HATCH_ADJUSTABLE = 19083;

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
