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
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import elinarcurios.machine.MTEBeamFluxMultiplier;
import gregtech.api.modularui2.GTGuiTextures;
import gtnhlanth.common.beamline.Particle;

/**
 * 束流倍流器的设置 / 读数面板（无槽位，纯设置面板，和样板总成侦测器同款做法）。
 *
 * <p>
 * 上半部分显示最近一包束流的粒子/能量/聚焦/输入通量/输出通量，下半部分设倍率（1 ~ 32768，回车生效）。
 * 束流本身每 20 tick 才有一包，所以这些读数也是每秒刷新一次，不是每 tick 变。
 */
public final class BeamFluxMultiplierGui {

    private static final int PANEL_WIDTH = 220;
    private static final int PANEL_HEIGHT = 200;
    private static final int TEXT_WIDTH = PANEL_WIDTH - 16;

    private final MTEBeamFluxMultiplier machine;

    public BeamFluxMultiplierGui(MTEBeamFluxMultiplier machine) {
        this.machine = machine;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        final IntSyncValue multiplier = new IntSyncValue(machine::getMultiplier, machine::setMultiplier).allowC2S();
        syncManager.syncValue("beamFluxMultiplier", multiplier);

        final IntSyncValue particleId = new IntSyncValue(machine::getLastParticleId);
        syncManager.syncValue("beamFluxParticle", particleId);

        final DoubleSyncValue energy = new DoubleSyncValue(machine::getLastEnergy);
        syncManager.syncValue("beamFluxEnergy", energy);

        final DoubleSyncValue focus = new DoubleSyncValue(machine::getLastFocus);
        syncManager.syncValue("beamFluxFocus", focus);

        final IntSyncValue inputRate = new IntSyncValue(machine::getLastInputRate);
        syncManager.syncValue("beamFluxInputRate", inputRate);

        final IntSyncValue outputRate = new IntSyncValue(machine::getLastOutputRate);
        syncManager.syncValue("beamFluxOutputRate", outputRate);

        final StringSyncValue status = new StringSyncValue(machine::describeStatus);
        syncManager.syncValue("beamFluxStatus", status);

        final Flow column = Flow.column()
            .width(TEXT_WIDTH)
            .childPadding(4)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .child(
                IKey.dynamic(() -> EnumChatFormatting.BLACK + machine.getLocalName() + EnumChatFormatting.RESET)
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.BLACK + describeParticle(particleId.getIntValue()))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> String
                        .format(Locale.ROOT, "%s能量：%,.3f keV", EnumChatFormatting.BLACK, energy.getDoubleValue()))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> String.format(Locale.ROOT, "%s聚焦：%.1f", EnumChatFormatting.BLACK, focus.getDoubleValue()))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> String.format(Locale.ROOT, "%s输入通量：%,d", EnumChatFormatting.BLACK, inputRate.getIntValue()))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> String.format(Locale.ROOT, "%s输出通量：%,d", EnumChatFormatting.BLACK, outputRate.getIntValue()))
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                Flow.row()
                    .coverChildren()
                    .childPadding(4)
                    .child(
                        IKey.dynamic(() -> EnumChatFormatting.BLACK + "倍率：")
                            .asWidget())
                    .child(
                        new TextFieldWidget()
                            .numbersInt(MTEBeamFluxMultiplier.MIN_MULTIPLIER, MTEBeamFluxMultiplier.MAX_MULTIPLIER)
                            .value(multiplier)
                            .width(80)
                            .height(14))
                    .child(
                        IKey.dynamic(() -> EnumChatFormatting.BLACK + " x")
                            .asWidget()))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.BLACK + "状态：" + status.getStringValue())
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.DARK_GRAY + "正面进、背面出；扳手点选正面")
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.DARK_GRAY + "范围 1~32768，回车生效；只乘通量")
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(() -> EnumChatFormatting.DARK_GRAY + "能量/聚焦/粒子原样透传")
                    .asWidget()
                    .width(TEXT_WIDTH));

        return new ModularPanel("elinarBeamFluxMultiplier").size(PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(column.pos(8, 8));
    }

    private static String describeParticle(int id) {
        if (id < 0 || id >= Particle.VALUES.length) return "粒子：无束流";
        return "粒子：" + Particle.getParticleFromId(id)
            .getLocalisedName();
    }
}
