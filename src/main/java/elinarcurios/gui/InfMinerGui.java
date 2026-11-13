package elinarcurios.gui;

import java.util.List;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import elinarcurios.machine.MTEComprehensiveMiningPlant;
import elinarcurios.util.ECFormat;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 综合采掘工厂 GUI：在原生多方块 GUI 上
 * - 去掉“成型按钮”（1×1×1 不需要）；
 * - 右上按钮改成“并行 / 模式设置”，点开副面板；
 * - 右下保留电源开关。
 *
 * <p>
 * 副面板里并行数值仍然“下次启动生效”（改并行要重算缓冲/补电），而**产出模式是立即生效**的：
 * 点哪档，服务端当场切档（{@code setMode}），并把状态同步回客户端把对应按钮点亮。
 */
public class InfMinerGui extends MTEMultiBlockBaseGui<MTEComprehensiveMiningPlant> {

    /** 副面板宽度 / 高度（父面板 198×203，副面板贴在父面板右侧、距底 40）。 */
    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_HEIGHT = 138;
    private static final int CONTENT_WIDTH = PANEL_WIDTH - 8;
    /** 三个模式按钮的尺寸：3×54 + 2×2 间距 = 166，塞进 172 的内容宽度。 */
    private static final int MODE_BUTTON_WIDTH = 54;
    private static final int MODE_BUTTON_HEIGHT = 18;

    public InfMinerGui(MTEComprehensiveMiningPlant multiblock) {
        super(multiblock);
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel panel) {
        LongSyncValue status = new LongSyncValue(() -> multiblock.isWorkEnabled() ? 1L : 0L, v -> {});
        LongSyncValue perSecond = new LongSyncValue(multiblock::getPerSecondEUt, v -> {});
        LongSyncValue perTick = new LongSyncValue(multiblock::getPerTickEUt, v -> {});
        LongSyncValue percent = new LongSyncValue(multiblock::getEnergyPercent, v -> {});
        LongSyncValue oreSpeed = new LongSyncValue(multiblock::getOrePerSecond, v -> {});
        syncManager.syncValue("infMinerStatusLine", status);
        syncManager.syncValue("infMinerPerSecond", perSecond);
        syncManager.syncValue("infMinerPerTick", perTick);
        syncManager.syncValue("infMinerPercent", percent);
        syncManager.syncValue("infMinerOreSpeed", oreSpeed);

        LongSyncValue fluidCount = new LongSyncValue(() -> (long) multiblock.getFluidTypeCount(), v -> {});
        syncManager.syncValue("infMinerFluidCount", fluidCount);
        StringSyncValue dimensionHint = new StringSyncValue(multiblock::getDimensionHint);
        syncManager.syncValue("infMinerDimensionHint", dimensionHint);

        StringSyncValue[] fluidLines = new StringSyncValue[10];
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            fluidLines[i] = new StringSyncValue(() -> {
                List<String> lines = multiblock.getFluidRateLines();
                return idx < lines.size() ? lines.get(idx) : "";
            });
            syncManager.syncValue("infMinerFluidLine" + i, fluidLines[i]);
        }

        ListWidget list = new ListWidget();
        list.crossAxisAlignment(Alignment.CrossAxis.START);
        list.collapseDisabledChild(true);
        list.child(
            IKey.dynamic(() -> "状态：" + (status.getValue() != 0L ? "运行" : "停机"))
                .asWidget());
        list.child(
            IKey.dynamic(() -> "当前耗电量：")
                .asWidget());
        list.child(
            IKey.dynamic(() -> ECFormat.compactAbove(perTick.getValue(), 1_000_000_000L) + "EU/t")
                .asWidget());
        list.child(
            IKey.dynamic(
                () -> ECFormat.compactAbove(perSecond.getValue(), 1_000_000_000L) + "EU/s（剩余"
                    + percent.getValue()
                    + "%）")
                .asWidget());
        list.child(
            IKey.dynamic(() -> "流体采掘速度：")
                .asWidget()
                .setEnabledIf(w -> fluidCount.getValue() > 0L));
        for (int i = 0; i < 10; i++) {
            final int idx = i;
            list.child(
                IKey.dynamic(() -> fluidLines[idx].getStringValue())
                    .asWidget()
                    .setEnabledIf(w -> fluidCount.getValue() > idx));
        }
        list.child(
            IKey.dynamic(() -> "矿石采掘速度：" + oreSpeed.getValue() + "块/秒")
                .asWidget());
        list.child(
            IKey.dynamic(() -> dimensionHint.getStringValue())
                .asWidget()
                .setEnabledIf(
                    w -> !dimensionHint.getStringValue()
                        .isEmpty()));
        return list;
    }

    @Override
    protected Flow createButtonColumn(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.column()
            .coverChildren()
            .topRel(0)
            .rightRel(0)
            .child(createParallelButton(syncManager, panel))
            .child(createPowerSwitchButton());
    }

    private ButtonWidget<?> createParallelButton(PanelSyncManager syncManager, ModularPanel panel) {
        IPanelHandler parallelPanel = syncManager
            .syncedPanel("infMinerParallel", true, (sm, sh) -> createParallelPanel(sm, panel));
        return new ButtonWidget<>().size(18, 18)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_SCREWDRIVER)
            .onMousePressed(d -> {
                if (!parallelPanel.isPanelOpen()) {
                    parallelPanel.openPanel();
                } else {
                    parallelPanel.closePanel();
                }
                return true;
            });
    }

    private ModularPanel createParallelPanel(PanelSyncManager syncManager, ModularPanel parent) {
        LongSyncValue ore = new LongSyncValue(
            () -> (long) multiblock.getConfiguredOre(),
            value -> multiblock
                .setConfiguredOre((int) Math.max(1L, Math.min(MTEComprehensiveMiningPlant.MAX_ORE_PARALLEL, value))))
                    .allowC2S();
        LongSyncValue fluid = new LongSyncValue(
            () -> (long) multiblock.getConfiguredFluid(),
            value -> multiblock.setConfiguredFluid(
                (int) Math.max(0L, Math.min(MTEComprehensiveMiningPlant.MAX_FLUID_PARALLEL, value)))).allowC2S();
        syncManager.syncValue("infMinerOre", ore);
        syncManager.syncValue("infMinerFluid", fluid);

        // 产出模式：只读同步值（当前档），点按钮时服务端改档后 notifyUpdate 同步回客户端。
        IntSyncValue mode = new IntSyncValue(multiblock::getMode, v -> {});
        syncManager.syncValue("infMinerMode", mode);

        return new ModularPanel("infMinerParallel").relative(parent)
            .rightRel(1)
            .bottom(40)
            .size(PANEL_WIDTH, PANEL_HEIGHT)
            .child(
                Flow.column()
                    .full()
                    .padding(4)
                    .child(
                        IKey.lang("elinarinfminer.gui.parallel.title")
                            .asWidget()
                            .marginBottom(4))
                    .child(
                        Flow.row()
                            .size(CONTENT_WIDTH, 20)
                            .child(
                                IKey.lang("elinarinfminer.gui.ore")
                                    .asWidget()
                                    .marginRight(4))
                            .child(
                                new TextFieldWidget()
                                    .numbersLong(() -> 1L, () -> (long) MTEComprehensiveMiningPlant.MAX_ORE_PARALLEL)
                                    .value(ore)
                                    .width(70)))
                    .child(
                        Flow.row()
                            .size(CONTENT_WIDTH, 20)
                            .child(
                                IKey.lang("elinarinfminer.gui.fluid")
                                    .asWidget()
                                    .marginRight(4))
                            .child(
                                new TextFieldWidget()
                                    .numbersLong(() -> 0L, () -> (long) MTEComprehensiveMiningPlant.MAX_FLUID_PARALLEL)
                                    .value(fluid)
                                    .width(70)))
                    .child(
                        IKey.lang("elinarinfminer.gui.mode")
                            .asWidget()
                            .marginTop(2))
                    .child(
                        Flow.row()
                            .size(CONTENT_WIDTH, MODE_BUTTON_HEIGHT)
                            .childPadding(2)
                            .child(modeButton(mode, MTEComprehensiveMiningPlant.MODE_REALTIME, "mod1 实时"))
                            .child(modeButton(mode, MTEComprehensiveMiningPlant.MODE_ACCUMULATE, "mod2 汇集"))
                            .child(modeButton(mode, MTEComprehensiveMiningPlant.MODE_SETTLE, "mod3 结算")))
                    .child(
                        IKey.dynamic(() -> modeHint(mode.getIntValue()))
                            .asWidget()
                            .marginTop(2))
                    .child(
                        IKey.lang("elinarinfminer.gui.parallel.hint")
                            .asWidget()
                            .marginTop(4))
                    .child(
                        IKey.lang("elinarinfminer.gui.parallel.limits")
                            .asWidget()
                            .marginTop(2)));
    }

    /**
     * 一个模式选择按钮：点哪档就把服务端切到哪档（立即生效），选中态用绿字加粗标出来。
     *
     * <p>
     * 用 {@link InteractionSyncHandler} 而不是 {@code onMousePressed}：前者能明确区分客户端/服务端
     * （{@code mouseDelta.isClient()}），只在服务端改机器状态，避免两端各改一次。
     */
    private ButtonWidget<?> modeButton(IntSyncValue mode, int target, String label) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(mouseDelta -> {
            if (!mouseDelta.isClient()) {
                multiblock.setMode(target);
                mode.notifyUpdate();
            }
        });
        return new ButtonWidget<>().syncHandler(handler)
            .size(MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT)
            .overlay(IKey.dynamic(() -> (mode.getIntValue() == target ? "§a§l" : "§7") + label));
    }

    /** 模式说明（按同步下来的当前档拼，间隔取静态配置，两端一致）。 */
    private static String modeHint(int mode) {
        int seconds = MTEComprehensiveMiningPlant.intervalSecondsFor(mode);
        switch (mode) {
            case MTEComprehensiveMiningPlant.MODE_REALTIME:
                return "§7实时：每秒倾倒进 AE";
            case MTEComprehensiveMiningPlant.MODE_SETTLE:
                return "§7结算：每 " + seconds + " 秒结算一次";
            default:
                return "§7汇集：每 " + seconds + " 秒倾倒一次";
        }
    }
}
