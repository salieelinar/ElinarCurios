package elinarcurios.gui;

import net.minecraft.util.EnumChatFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import elinarcurios.machine.IPatternMirror;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.modularui2.GTGuiTextures;

/**
 * 总成镜像的配置界面（物品版与流体版共用）—— 一个**不含任何槽位**的纯设置面板。
 *
 * <p>
 * ★ <b>这里刻意不用 GT 的 {@code mteTemplatePanelBuilder}</b>。那个模板是按"机器有库存 + 玩家背包"
 * 设计的，会往容器里加槽位；而镜像的库存是 **0 格**（材料都在总成里），实测右键会抛
 * {@code RuntimeException: Slot 49 not in valid range - [0,0)}，界面根本打不开。镜像本来也不需要
 * 任何槽位，所以自绘一个纯设置面板：标题 + 绑定状态 + 格号按钮 + 当前镜像内容，不带玩家背包。
 *
 * <p>
 * 只做三件事（芷若的定稿要求）：选「第几格」、提示绑定的总成坐标、回显当前这一格镜像到什么。
 * 界面里**没有格子**——这正是她要的样子（"不应该有格子的，只保留哪个选择格子的按钮就行"）。
 *
 * <p>
 * 同步：格号与两个文字都要走同步值（客户端那份机器实例的字段未必最新），所以用
 * {@link IntSyncValue} + {@link StringSyncValue}，而不是直接读字段。
 */
public final class MirrorGui {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 104;
    private static final int BUTTON_SIZE = 18;
    /** 文字控件宽度：让长文案在面板内自动换行，而不是溢出去。 */
    private static final int TEXT_WIDTH = PANEL_WIDTH - 16;

    private static final String SYNC_SLOT = "alapSlot";
    private static final String SYNC_BINDING = "alapBinding";
    private static final String SYNC_PROVIDED = "alapProvided";

    private final MetaTileEntity machine;
    private final IPatternMirror mirror;

    public MirrorGui(MetaTileEntity machine, IPatternMirror mirror) {
        this.machine = machine;
        this.mirror = mirror;
    }

    public ModularPanel build(PosGuiData guiData, PanelSyncManager syncManager, UISettings uiSettings) {
        IntSyncValue slotSync = new IntSyncValue(mirror::getSlotIndex, mirror::setSlotIndex);
        syncManager.syncValue(SYNC_SLOT, slotSync);

        StringSyncValue bindingSync = new StringSyncValue(this::describeBinding);
        syncManager.syncValue(SYNC_BINDING, bindingSync);

        // 当前提供物也从服务端取：它读的是总成里那一格，客户端那份数据不同步。
        StringSyncValue providedSync = new StringSyncValue(mirror::getProvidedText);
        syncManager.syncValue(SYNC_PROVIDED, providedSync);

        Flow column = Flow.column()
            .width(TEXT_WIDTH)
            .childPadding(4)
            .crossAxisAlignment(com.cleanroommc.modularui.utils.Alignment.CrossAxis.START)
            .child(
                IKey.dynamic(() -> EnumChatFormatting.BLACK + machine.getLocalName() + EnumChatFormatting.RESET)
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.BLACK + stripColors(bindingSync.getStringValue())
                        + EnumChatFormatting.RESET)
                    .asWidget()
                    .width(TEXT_WIDTH))
            .child(
                Flow.row()
                    .coverChildren()
                    .childPadding(6)
                    .child(stepButton(slotSync, -1))
                    .child(
                        IKey.dynamic(
                            () -> EnumChatFormatting.BLACK + "第 "
                                + slotSync.getIntValue()
                                + " / "
                                + mirror.getMaxSlot()
                                + " 格")
                            .asWidget())
                    .child(stepButton(slotSync, 1)))
            .child(
                IKey.dynamic(
                    () -> EnumChatFormatting.BLACK + "当前："
                        + EnumChatFormatting.RESET
                        + stripColors(providedSync.getStringValue()))
                    .asWidget()
                    .width(TEXT_WIDTH));

        return new ModularPanel("alapMirror").size(PANEL_WIDTH, PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_STANDARD)
            .child(column.pos(8, 10));
    }

    /** 服务端算好、客户端显示的绑定描述。文案刻意写短，避免超出面板。 */
    private String describeBinding() {
        if (!mirror.isBound()) {
            return "未绑定：闪存左键总成 → 右键本方块";
        }
        int[] coords = mirror.getBoundCoords();
        if (coords == null) return "未绑定";
        return "已绑定总成 @ " + coords[0] + ", " + coords[1] + ", " + coords[2];
    }

    /**
     * 加/减一格并回绕。
     *
     * <p>
     * 用 {@link InteractionSyncHandler} 而不是 {@code onMousePressed}：前者能明确区分
     * 客户端/服务端（{@code mouseDelta.isClient()}），只在服务端改机器状态，避免两端各改一次。
     */
    /** Strip every color code: this panel paints all text black for readability on the light background. */
    private static String stripColors(String text) {
        if (text == null) return "";
        return text.replaceAll("§.", "");
    }

    private ButtonWidget<?> stepButton(IntSyncValue slotSync, int delta) {
        InteractionSyncHandler handler = new InteractionSyncHandler().setOnMousePressed(mouseDelta -> {
            if (!mouseDelta.isClient()) {
                int max = mirror.getMaxSlot();
                int next = mirror.getSlotIndex() + delta;
                if (next < 1) next = max;
                if (next > max) next = 1;
                mirror.setSlotIndex(next);
                slotSync.notifyUpdate();
            }
        });
        return new ButtonWidget<>().syncHandler(handler)
            .overlay(delta < 0 ? IKey.str("<") : IKey.str(">"))
            .size(BUTTON_SIZE, BUTTON_SIZE);
    }
}
