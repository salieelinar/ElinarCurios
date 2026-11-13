package elinarcurios.gui;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import elinarcurios.machine.MTEPatternRedstoneDetector;
import gregtech.api.modularui2.GTGuiTextures;

/**
 * 样板总成侦测器的设置界面 —— 纯设置面板，不带任何槽位（本方块没有库存，和镜像同款做法，
 * 用 GT 的 {@code mteTemplatePanelBuilder} 会因「0 格库存」直接抛异常）。
 *
 * <p>
 * 只做三件事（芷若 2026-11-07 拍板）：显示绑定状态、设置「信号保持秒数」、显示当前信号状态。
 * 秒数范围 1 ~ {@link Integer#MAX_VALUE}，输入后按回车生效（ModularUI2 文本框的标准行为）。
 */
public final class PatternRedstoneDetectorGui {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 116;
    private static final int TEXT_WIDTH = PANEL_WIDTH - 16;

    private static final String SYNC_SECONDS = "elinarDetectorSeconds";
    private static final String SYNC_BINDING = "elinarDetectorBinding";
    private static final String SYNC_STATUS = "elinarDetectorStatus";

    private final MTEPatternRedstoneDetector detector;

    public PatternRedstoneDetectorGui(MTEPatternRedstoneDetector detector) {
        this.detector = detector;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        LongSyncValue secondsSync = new LongSyncValue(
            () -> (long) detector.getHoldSeconds(),
            value -> detector.setHoldSeconds((int) Math.min((long) Integer.MAX_VALUE, Math.max(1L, value)))).allowC2S();
        syncManager.syncValue(SYNC_SECONDS, secondsSync);

        StringSyncValue bindingSync = new StringSyncValue(detector::describeBinding);
        syncManager.syncValue(SYNC_BINDING, bindingSync);

        StringSyncValue statusSync = new StringSyncValue(detector::describeStatus);
        syncManager.syncValue(SYNC_STATUS, statusSync);

        Flow column = Flow.column()
            .width(TEXT_WIDTH)
            .childPadding(4)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .child(
                IKey.dynamic(() -> EnumChatFormatting.BLACK + detector.getLocalName() + EnumChatFormatting.RESET)
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.BLACK + bindingSync.getStringValue())
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                Flow.row()
                    .coverChildren()
                    .childPadding(4)
                    .child(
                        IKey.str("信号保持：")
                            .asWidget())
                    .child(
                        new TextFieldWidget().numbersLong(() -> 1L, () -> (long) Integer.MAX_VALUE)
                            .value(secondsSync)
                            .width(84))
                    .child(
                        IKey.str(" 秒")
                            .asWidget()))
            .child(
                IKey.str("范围 1 ~ 2147483647 秒，输入后按回车生效")
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.BLACK + statusSync.getStringValue())
                    .asWidget()
                    .width(TEXT_WIDTH));

        return new ModularPanel("elinarPatternDetector").size(PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(column.pos(8, 10));
    }
}
