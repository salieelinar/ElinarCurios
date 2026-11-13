package elinarcurios.gui;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;
import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import elinarcurios.machine.MTEHatchInputAdjustable;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.hatch.MTEHatchInputGui;

/**
 * 可调容量流体输入仓的 GUI：原版输入仓界面 + 左下角螺丝刀按钮 + 容量设置浮层。
 *
 * <p>
 * 浮层模式照搬 GT 自己的 {@code MTEHatchInputBusCompressedGui}（原 GUI + 左下按钮 + syncedPanel +
 * TextFieldWidget + LongSyncValue.allowC2S），主面板尺寸不变。
 */
public class MTEHatchInputAdjustableGui extends MTEHatchInputGui {

    private final MTEHatchInputAdjustable adjustable;

    public MTEHatchInputAdjustableGui(MTEHatchInputAdjustable machine) {
        super(machine);
        this.adjustable = machine;
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createBottomLeftCornerFlow(panel, syncManager).child(createSettingsButton(syncManager, panel));
    }

    private IWidget createSettingsButton(PanelSyncManager syncManager, ModularPanel parent) {
        IPanelHandler settingsPanel = syncManager.syncedPanel(
            "elinarInputHatchCapacity",
            true,
            (p_syncManager, syncHandler) -> createSettingsPanel(p_syncManager, parent));
        return new ButtonWidget<>().size(18, 18)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_SCREWDRIVER)
            .onMousePressed(d -> {
                if (!settingsPanel.isPanelOpen()) {
                    settingsPanel.openPanel();
                } else {
                    settingsPanel.closePanel();
                }
                return true;
            })
            .addTooltipLine(StatCollector.translateToLocal("elinarcurios.gui.input_hatch.capacity.tooltip"))
            .tooltipShowUpTimer(TOOLTIP_DELAY);
    }

    private ModularPanel createSettingsPanel(PanelSyncManager syncManager, ModularPanel parent) {
        LongSyncValue capacitySyncer = new LongSyncValue(
            () -> (long) adjustable.getConfiguredCapacity(),
            value -> adjustable.setConfiguredCapacity((int) Math.max(1L, Math.min(Integer.MAX_VALUE, value))))
                .allowC2S();
        return new ModularPanel("elinarInputHatchCapacity").relative(parent)
            .rightRel(1)
            .bottom(50)
            .size(180, 84)
            .child(
                Flow.column()
                    .full()
                    .padding(4)
                    .child(
                        IKey.lang("elinarcurios.gui.input_hatch.capacity.title")
                            .asWidget()
                            .marginBottom(4))
                    .child(
                        Flow.row()
                            .size(172, 20)
                            .child(
                                IKey.lang("elinarcurios.gui.input_hatch.capacity.label")
                                    .asWidget()
                                    .marginRight(4))
                            .child(
                                new TextFieldWidget().numbersLong(() -> 1L, () -> (long) Integer.MAX_VALUE)
                                    .value(capacitySyncer)
                                    .width(88)))
                    .child(
                        IKey.dynamic(
                            () -> StatCollector.translateToLocalFormatted(
                                "elinarcurios.gui.input_hatch.capacity.current",
                                formatNumber(adjustable.getConfiguredCapacity())))
                            .asWidget()
                            .marginTop(4)));
    }
}
