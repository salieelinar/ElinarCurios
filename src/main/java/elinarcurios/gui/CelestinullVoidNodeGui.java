package elinarcurios.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import elinarcurios.machine.MTECelestinullVoidNode;
import elinarcurios.util.ECFormat;
import gregtech.api.modularui2.GTGuis;
import gregtech.api.modularui2.GTWidgetThemes;

/**
 * 天玄虚空能量节点的 GUI：黑屏里只有信息，没有按钮。
 *
 * <p>
 * <b>★ 关键约束（2026-09-13 排查得出的根因，别再犯）</b>：本项目要兼容 **GTNH 2.8.4 自带的 GT5U
 * 5.09.51.482**（见该整合包 manifest），而那个版本的 GT5U <b>没有</b>
 * {@code gregtech.common.gui.modularui.singleblock.base.MTETieredMachineBlockBaseGui}、也<b>没有</b>
 * {@code gregtech.api.modularui2.common.CommonWidgets}（这两个是 5.09.54 才加的）。
 * 之前编译时用的是 5.09.54.124，导致本类继承了一个运行时<b>不存在</b>的基类 —— 一加载就失败，
 * 表现为「GUI 能开、面板全空、连物品栏都没有、鼠标悬停无反应」，而且日志里没有任何异常
 * （类加载失败被 GT 静默吞掉）。**所以本类不继承任何 GT5U 的 GUI 基类**，只用运行时确实存在的 API：
 * {@link GTGuis#mteTemplatePanelBuilder}、{@code ModularPanel} 与 MUI2 的 widget。
 *
 * <p>
 * 运行时确实存在（已在 {@code gregtech-5.09.51.482.jar} 内核实）：
 * {@code CommonMetaTileEntity.useMui2() / buildUI(PosGuiData, …)}、{@code GTGuis.mteTemplatePanelBuilder}、
 * {@code GTBaseGuiBuilder}、{@code ModularPanel}、{@code Flow}、{@code ListWidget}、{@code IKey}。
 * 门控：{@code GTGuis.GLOBAL_SWITCH_MUI2} 为真才会走 MUI2 分支。
 */
public final class CelestinullVoidNodeGui {

    private static final long TICKS_PER_SECOND = 20L;

    private static final String SYNC_STORED = "voidNodeStored";
    private static final String SYNC_TICK = "voidNodeTick";
    private static final String SYNC_AVG_IN = "voidNodeAvgIn";

    private static final int SCREEN_WIDTH = 160;
    private static final int SCREEN_HEIGHT = 92;
    private static final int SCREEN_X = 8;
    private static final int SCREEN_Y = 12;

    /**
     * 黑屏里两个「会长大的数字」（缓存电量、实时输入均值）共用的科学计数阈值（2026-10-18 芷若定 10^12）：
     * 到 10^12 就写成 {@code 1.182e+12}，否则它们撑到十几位时会把 155px 的可用宽度顶破
     * （缓存那行约 1e13 起就越界，均值那行更早）。
     */
    private static final long GUI_SCIENTIFIC_THRESHOLD = 1_000_000_000_000L;

    private final MTECelestinullVoidNode machine;

    public CelestinullVoidNodeGui(MTECelestinullVoidNode machine) {
        this.machine = machine;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        // ★★ 同步值：**直接持有 handler 引用**，不要事后再用 getSyncHandler("裸名字") 去取。
        // 原因（MUI2 2.2.18 实测）：`syncValue(name, handler)` 真正存进 map 的键是
        // `makeSyncKey(name, 0)` = **"name:0"**（PanelSyncManager.putSyncValue:127 / makeSyncKey:293），
        // 而 `getSyncHandler(mapKey)` 是直接查 map（:266）—— 传裸名字会**静默返回 null**。
        // GT5U 自己也是硬编码带后缀取的（MTEMultiBlockBaseGui:311 `getSyncHandler("recipeInfo:0")`，
        // 另有 "machineMode:0" / "recipeLock:0" / "coverage:0" 等）。
        // 之前就是栽在这里：null 被下面的兜底吞掉 → 界面恒显示 0、倒数恒 100 秒，且毫无报错。
        LongSyncValue stored = new LongSyncValue(machine::getEUVar);
        LongSyncValue tick = new LongSyncValue(() -> {
            if (machine.getBaseMetaTileEntity() == null) return 0L;
            // ★ 同步「秒」而不是原始 tick：getTimer() 每 tick 都变，原样同步就是 20 包/秒；
            // 而界面显示的单位本来就是秒，除 20 后值每秒才变一次，直接省掉 19/20 的同步包。
            return machine.getBaseMetaTileEntity()
                .getTimer() / TICKS_PER_SECOND;
        });
        // GT 自己就在同步平均输入（用于 WAILA），这里复用，不额外开网络包。
        LongSyncValue avgIn = new LongSyncValue(() -> {
            if (machine.getBaseMetaTileEntity() == null) return 0L;
            return machine.getBaseMetaTileEntity()
                .getAverageElectricInput();
        });
        syncManager.syncValue(SYNC_STORED, stored);
        syncManager.syncValue(SYNC_TICK, tick);
        syncManager.syncValue(SYNC_AVG_IN, avgIn);

        // 黑屏容器：显式尺寸的 ParentWidget + 终端主题。
        // ★运行时 GT5U 5.09.51 里 `GTWidgetThemes.BACKGROUND_TERMINAL` 是个 **String** 常量，
        // 可以直接传进 `widgetTheme(String)`；5.09.54 才改成 WidgetThemeKey 类型。
        // 漏了这一步屏幕容器会**没有任何背景**（全透明），看着还是空的。
        ParentWidget<?> screen = new ParentWidget<>().size(SCREEN_WIDTH, SCREEN_HEIGHT)
            .padding(3, 2, 3, 2)
            .widgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL);
        screen.child(createContentColumn(stored, tick, avgIn));

        return GTGuis.mteTemplatePanelBuilder(machine, guiData, syncManager, uiSettings)
            .setWidth(176)
            // ★ 高度必须 ≥ 字符串 + 物品栏：物品栏是 bottom(7) 锚定的，实测高 76
            // （主体三行 y=0/18/36，快捷栏 y=58），所以面板 166 时物品栏顶端在 y=83，
            // 而屏幕占到 y=104 —— 会把物品栏第一行整个盖掉（之前「连背包格子都没了」的另一半原因）。
            .setHeight(200)
            // GT logo 固定在 (152,63) 且尺寸 17×17，会被屏幕（x 8..168）压住，索性关掉。
            .doesAddGregTechLogo(false)
            .build()
            .child(screen.pos(SCREEN_X, SCREEN_Y));
    }

    private Flow createContentColumn(LongSyncValue stored, LongSyncValue tick, LongSyncValue avgIn) {
        return Flow.column()
            .coverChildren()
            .childPadding(1)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            // 第一行：只有缓存电量（用户要求：缓存与总容量分两行显示）
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.GRAY + tr("stored")
                        + " "
                        + EnumChatFormatting.GREEN
                        + ECFormat.compactAbove(stored == null ? 0L : stored.getLongValue(), GUI_SCIENTIFIC_THRESHOLD)
                        + EnumChatFormatting.GRAY
                        + " EU")
                    .asWidget())
            // 第二行：总容量 + 占用百分比合在一行
            .child(IKey.dynamic(() -> {
                long now = stored == null ? 0L : stored.getLongValue();
                long percent = Math.min(100L, now / (MTECelestinullVoidNode.CAPACITY / 100L));
                return EnumChatFormatting.GRAY + tr("capacity")
                    + " "
                    + EnumChatFormatting.YELLOW
                    + ECFormat.compact(MTECelestinullVoidNode.CAPACITY)
                    + EnumChatFormatting.GRAY
                    + " EU（"
                    + EnumChatFormatting.AQUA
                    + percent
                    + "%"
                    + EnumChatFormatting.GRAY
                    + "）";
            })
                .asWidget())
            // 第三/四行：标签单独一行，数值 + 单位另起一行。
            // ★2026-10-18 修正：原来「实时输入(最近均值)： 11,816,060 EU/t」挤在一行，实测约 174px，
            // 而黑屏 160px 扣掉左右 padding 只剩 155px —— 会触发 MUI2 的 SIZING 错误（每 tick 一条），
            // 行尾的 EU/t 还会画到黑框外面。拆开后最宽约 95px；数值 ≥10^12 时改用科学计数，也不会再撑破。
            .child(
                IKey.dynamic(() -> EnumChatFormatting.DARK_PURPLE + tr("avg_in"))
                    .asWidget())
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.WHITE
                        + ECFormat.compactAbove(avgIn == null ? 0L : avgIn.getLongValue(), GUI_SCIENTIFIC_THRESHOLD)
                        + EnumChatFormatting.DARK_PURPLE
                        + " EU/t")
                    .asWidget())
            .child(IKey.dynamic(() -> {
                long remainSec = secondsUntilPeriodic(tick);
                return EnumChatFormatting.GOLD + tr("countdown")
                    + " "
                    + EnumChatFormatting.WHITE
                    + remainSec
                    + EnumChatFormatting.GOLD
                    + " "
                    + tr("seconds");
            })
                .asWidget())
            .child(IKey.dynamic(() -> {
                long now = stored == null ? 0L : stored.getLongValue();
                boolean byFull = now >= MTECelestinullVoidNode.WATER_MARK;
                return EnumChatFormatting.GRAY + tr(byFull ? "mode.full" : "mode.periodic");
            })
                .asWidget())
            // 警告拆成两行 —— 整句太长会伸出黑屏右边（用户反馈）。
            .child(
                IKey.dynamic(() -> EnumChatFormatting.DARK_RED + tr("warn1"))
                    .asWidget())
            .child(
                IKey.dynamic(() -> EnumChatFormatting.DARK_RED + tr("warn2"))
                    .asWidget());
    }

    /** 距离下一次「100 秒兜底倾倒」还有多少秒（同步值存的是秒，见 build() 里的说明）。 */
    private static long secondsUntilPeriodic(LongSyncValue tick) {
        long nowSec = tick == null ? 0L : tick.getLongValue();
        long periodSec = MTECelestinullVoidNode.PERIOD_TICKS / TICKS_PER_SECOND;
        return periodSec - Math.floorMod(nowSec, periodSec);
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal("elinarcurios.voidNode.gui." + key);
    }
}
