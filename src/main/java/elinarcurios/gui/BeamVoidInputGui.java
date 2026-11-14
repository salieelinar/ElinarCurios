package elinarcurios.gui;

import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.layout.Flow;

import elinarcurios.machine.IBeamVoidCollector;
import elinarcurios.machine.MTEBeamVoidStorage;
import gregtech.api.modularui2.GTGuiTextures;

/**
 * 束流虚空输入仓界面：绑定状态 + 最近收到的一包束流读数 + 累计入库。
 *
 * <p>
 * 只读面板（这个仓没有任何设置项：19 种粒子全收，是她的要求）。
 */
public final class BeamVoidInputGui {

    private static final int PANEL_WIDTH = 250;

    private static final int PANEL_HEIGHT = 168;

    private static final int TEXT_WIDTH = PANEL_WIDTH - 16;

    private final IBeamVoidCollector machine;

    public BeamVoidInputGui(IBeamVoidCollector machine) {
        this.machine = machine;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        final IntSyncValue particle = new IntSyncValue(machine::getLastParticleId);
        syncManager.syncValue("beamVoidInParticle", particle);

        final IntSyncValue rate = new IntSyncValue(machine::getLastRate);
        syncManager.syncValue("beamVoidInRate", rate);

        final DoubleSyncValue energy = new DoubleSyncValue(machine::getLastEnergy);
        syncManager.syncValue("beamVoidInEnergy", energy);

        final DoubleSyncValue focus = new DoubleSyncValue(machine::getLastFocus);
        syncManager.syncValue("beamVoidInFocus", focus);

        final LongSyncValue total = new LongSyncValue(machine::getTotalReceived);
        syncManager.syncValue("beamVoidInTotal", total);

        final StringSyncValue link = new StringSyncValue(machine::describeLink);
        syncManager.syncValue("beamVoidInLink", link);

        final StringSyncValue status = new StringSyncValue(machine::describeStatus);
        syncManager.syncValue("beamVoidInStatus", status);

        final Flow column = Flow.column()
            .width(TEXT_WIDTH)
            .childPadding(4)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .child(
                IKey.dynamic(() -> EnumChatFormatting.BLACK + machine.getGuiTitle())
                    .asWidget())
            .child(
                IKey.dynamic(() -> EnumChatFormatting.DARK_GRAY + link.getStringValue())
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.DARK_GRAY + "状态：" + status.getStringValue())
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> particle.getIntValue() < 0 ? EnumChatFormatting.BLACK + "最近粒子：无"
                        : EnumChatFormatting.BLACK + "最近粒子：" + MTEBeamVoidStorage.particleName(particle.getIntValue()))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> String.format(
                        Locale.ROOT,
                        "%s能量（eV）：%,.2f",
                        EnumChatFormatting.BLACK,
                        energy.getDoubleValue() * 1000.0D))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> String.format(
                        Locale.ROOT,
                        "%s通量：%,d /s    聚焦：%.1f",
                        EnumChatFormatting.BLACK,
                        rate.getIntValue(),
                        focus.getDoubleValue()))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> String.format(Locale.ROOT, "%s累计入库：%,d", EnumChatFormatting.BLACK, total.getLongValue()))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.str(EnumChatFormatting.DARK_GRAY + "19 种粒子全收；收到即入库，不做过滤")
                    .asWidget()
                    .width(TEXT_WIDTH));

        return new ModularPanel("elinarBeamVoidInput").size(PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(column.pos(8, 6));
    }
}
