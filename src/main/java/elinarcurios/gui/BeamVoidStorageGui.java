package elinarcurios.gui;

import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import elinarcurios.machine.MTEBeamVoidStorage;
import gregtech.api.modularui2.GTGuiTextures;
import gtnhlanth.common.beamline.Particle;

/**
 * 束流虚空存储器界面：19 种粒子、每种两行（类型 / 库存，超出高度用滚条）+ 已绑定输入/输出仓数量。
 *
 * <p>
 * 每行的数字走 {@link LongSyncValue}（每秒由服务端同步一次），粒子名走本 mod 的中文名表。
 */
public final class BeamVoidStorageGui {

    private static final int PANEL_WIDTH = 250;

    private static final int PANEL_HEIGHT = 240;

    private static final int LIST_WIDTH = PANEL_WIDTH - 20;

    private static final int LIST_HEIGHT = 178;

    private final MTEBeamVoidStorage storage;

    public BeamVoidStorageGui(MTEBeamVoidStorage storage) {
        this.storage = storage;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        final int count = MTEBeamVoidStorage.PARTICLE_COUNT;
        final LongSyncValue[] counts = new LongSyncValue[count];
        for (int i = 0; i < count; i++) {
            final int index = i;
            counts[i] = new LongSyncValue(() -> storage.getCount(index));
            syncManager.syncValue("beamVoidCount" + i, counts[i]);
        }

        final IntSyncValue boundInputs = new IntSyncValue(() -> storage.countBoundHatches(true));
        final IntSyncValue boundOutputs = new IntSyncValue(() -> storage.countBoundHatches(false));
        syncManager.syncValue("beamVoidInputs", boundInputs);
        syncManager.syncValue("beamVoidOutputs", boundOutputs);

        final ListWidget<IWidget, ?> list = new ListWidget<>().size(LIST_WIDTH, LIST_HEIGHT)
            .crossAxisAlignment(Alignment.CrossAxis.START);
        for (int i = 0; i < count; i++) {
            addParticleRows(list, i, counts[i]);
        }

        final Flow column = Flow.column()
            .width(PANEL_WIDTH - 16)
            .childPadding(3)
            .crossAxisAlignment(Alignment.CrossAxis.START)
            .child(
                IKey.str(EnumChatFormatting.BLACK + "束流虚空存储器")
                    .asWidget())
            .child(
                IKey.dynamic(
                    () -> String.format(
                        Locale.ROOT,
                        "%s输入仓：%d    输出仓：%d",
                        EnumChatFormatting.DARK_GRAY,
                        boundInputs.getIntValue(),
                        boundOutputs.getIntValue()))
                    .asWidget())
            .child(list)
            .child(
                IKey.str(EnumChatFormatting.DARK_GRAY + "闪存左键本方块 → 右键输入仓 / 输出仓绑定")
                    .asWidget());

        return new ModularPanel("elinarBeamVoidStorage").size(PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(column.pos(8, 6));
    }

    /**
     * 每种粒子占两行（她 2026-11-14 指定）：第一行粒子类型（图标 + 名字，黑色），
     * 第二行「库存：数量」（黑灰色）；两行都左对齐。数字不再挤在右边缘，也不会被滚动条盖住。
     */
    private static void addParticleRows(ListWidget<IWidget, ?> list, int particleId, LongSyncValue count) {
        final Particle particle = Particle.getParticleFromId(particleId);
        list.child(
            Flow.row()
                .coverChildren()
                .childPadding(2)
                .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                .child(
                    new ParentWidget<>().size(10)
                        .background(particle.getTexture()))
                .child(
                    IKey.dynamic(
                        () -> EnumChatFormatting.BLACK
                            + StatCollector.translateToLocal("elinarcurios.beamvoid.particle." + particle.getName()))
                        .asWidget()));
        list.child(
            IKey.dynamic(
                () -> String.format(Locale.ROOT, "%s库存：%,d", EnumChatFormatting.DARK_GRAY, count.getLongValue()))
                .asWidget());
    }
}
