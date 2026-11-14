package elinarcurios.gui;

import java.util.Arrays;
import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import elinarcurios.machine.MTEBeamVoidStorage;
import elinarcurios.machine.MTEHatchBeamVoidOutput;
import gregtech.api.modularui2.GTGuiTextures;
import gtnhlanth.common.beamline.Particle;

/**
 * 束流虚空输出仓界面：选 1 种粒子、填能量(eV) 与通量、开关"随机器启动"。
 *
 * <p>
 * 聚焦恒定 99，不给玩家改（她 2026-11-14 定）。
 */
public final class BeamVoidOutputGui {

    private static final int PANEL_WIDTH = 250;

    private static final int PANEL_HEIGHT = 246;

    private static final int TEXT_WIDTH = PANEL_WIDTH - 16;

    private final MTEHatchBeamVoidOutput machine;

    public BeamVoidOutputGui(MTEHatchBeamVoidOutput machine) {
        this.machine = machine;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        final IntSyncValue particleId = new IntSyncValue(machine::getParticleId, machine::setParticleId);
        particleId.allowC2S();
        syncManager.syncValue("beamVoidOutParticle", particleId);

        final DoubleSyncValue energy = new DoubleSyncValue(machine::getEnergyEv, machine::setEnergyEv);
        energy.allowC2S();
        syncManager.syncValue("beamVoidOutEnergy", energy);

        final LongSyncValue rate = new LongSyncValue(machine::getRatePerSecond, machine::setRatePerSecond);
        rate.allowC2S();
        syncManager.syncValue("beamVoidOutRate", rate);

        final BooleanSyncValue follow = new BooleanSyncValue(machine::isFollowMachine, machine::setFollowMachine);
        follow.allowC2S();
        syncManager.syncValue("beamVoidOutFollow", follow);

        final BooleanSyncValue followRedstone = new BooleanSyncValue(
            machine::isFollowRedstone,
            machine::setFollowRedstone);
        followRedstone.allowC2S();
        syncManager.syncValue("beamVoidOutRedstone", followRedstone);

        final StringSyncValue status = new StringSyncValue(machine::describeStatus);
        syncManager.syncValue("beamVoidOutStatus", status);

        final StringSyncValue link = new StringSyncValue(machine::describeLink);
        syncManager.syncValue("beamVoidOutLink", link);

        final Flow column = Flow.column()
            .width(TEXT_WIDTH)
            .childPadding(3)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .child(
                IKey.str(EnumChatFormatting.BLACK + "束流虚空输出仓")
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
                IKey.str(EnumChatFormatting.BLACK + "输出粒子（选 1 种）：")
                    .asWidget())
            .child(
                new Grid()
                    .gridOfWidthElements(
                        10,
                        Arrays.asList(Particle.VALUES),
                        ($x, $y, index, particle) -> particleButton(particle, particleId))
                    .coverChildren())
            .child(
                Flow.row()
                    .coverChildren()
                    .childPadding(3)
                    .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                    .child(
                        IKey.str(EnumChatFormatting.BLACK + "能量(eV)：")
                            .asWidget())
                    .child(
                        new TextFieldWidget().numbersDouble(0.0D, 1.0E18D)
                            .value(energy)
                            .width(100)
                            .height(14))
                    .child(
                        IKey.dynamic(
                            () -> EnumChatFormatting.DARK_GRAY + "= " + formatKeV(energy.getDoubleValue() / 1000.0D))
                            .asWidget()))
            .child(
                Flow.row()
                    .coverChildren()
                    .childPadding(3)
                    .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                    .child(
                        IKey.str(EnumChatFormatting.BLACK + "通量/s：")
                            .asWidget())
                    .child(
                        new TextFieldWidget().numbersLong(0L, Integer.MAX_VALUE)
                            .value(rate)
                            .width(100)
                            .height(14)))
            .child(
                Flow.row()
                    .coverChildren()
                    .childPadding(3)
                    .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                    .child(
                        new ToggleButton().value(follow)
                            .size(14)
                            .overlay(true, GTGuiTextures.OVERLAY_BUTTON_CHECKMARK)
                            .addTooltipLine("机器开机时输出；束流合成器则需要有配方在跑"))
                    .child(
                        IKey.dynamic(() -> EnumChatFormatting.BLACK + "随机器" + (follow.getBoolValue() ? "：开" : "：关"))
                            .asWidget())
                    .child(
                        new ToggleButton().value(followRedstone)
                            .size(14)
                            .overlay(true, GTGuiTextures.OVERLAY_BUTTON_CHECKMARK)
                            .addTooltipLine("本方块收到红石信号就输出（与随机器取或）"))
                    .child(
                        IKey.dynamic(
                            () -> EnumChatFormatting.BLACK + "随红石" + (followRedstone.getBoolValue() ? "：开" : "：关"))
                            .asWidget()))
            .child(
                IKey.str(EnumChatFormatting.DARK_GRAY + "聚焦固定 99；通量上限 2,147,483,647 /s")
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.str(EnumChatFormatting.DARK_GRAY + "合成器不看能量；一种粒子一个输出仓，配方要两种就放两个")
                    .asWidget()
                    .width(TEXT_WIDTH));

        return new ModularPanel("elinarBeamVoidOutput").size(PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(column.pos(8, 6));
    }

    /** keV 读数：按大小选格式，保证右边那截不会被界面边缘裁掉。 */
    private static String formatKeV(double keV) {
        final double abs = Math.abs(keV);
        if (abs == 0.0D) return "0 keV";
        if (abs >= 1.0E6D || abs < 1.0E-3D) return String.format(Locale.ROOT, "%.2e keV", keV);
        if (abs >= 100.0D) return String.format(Locale.ROOT, "%,.0f keV", keV);
        if (abs >= 1.0D) return String.format(Locale.ROOT, "%.2f keV", keV);
        return String.format(Locale.ROOT, "%.4f keV", keV);
    }

    private ToggleButton particleButton(Particle particle, IntSyncValue selected) {
        return new ToggleButton().invertSelected(true)
            .size(18)
            .overlay(particle.getTexture())
            .value(
                new BoolValue.Dynamic(
                    () -> selected.getIntValue() == particle.getId(),
                    value -> selected.setIntValue(value ? particle.getId() : -1)))
            .addTooltipLine(MTEBeamVoidStorage.particleName(particle.getId()));
    }
}
