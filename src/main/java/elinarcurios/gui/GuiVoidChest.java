package elinarcurios.gui;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;

import elinarcurios.ElinarCurios;
import elinarcurios.network.DissociateVoidChestPacket;
import elinarcurios.network.SolidifyVoidChestPacket;
import elinarcurios.tile.TileEntityVoidChest;

/**
 * 虚界百宝箱 GUI:与永恒箱子相同的 12 页 × 243 格布局,右上角两个按钮:
 * 「固化」——箱内所有非空槽位的物品数量统一刷新为 10 亿;
 * 「解离」——镜像操作,统一刷新为 1。
 */
public class GuiVoidChest extends GuiEternalChest {

    /** 固化按钮的控件 id(页签使用 100-111,不冲突)。 */
    private static final int BUTTON_SOLIDIFY = 200;
    /** 解离按钮的控件 id。 */
    private static final int BUTTON_DISSOCIATE = 201;

    public GuiVoidChest(@Nonnull final TileEntityVoidChest chest, final InventoryPlayer inventoryPlayer) {
        super(chest, inventoryPlayer);
    }

    @Override
    protected String getContainerTitleKey() {
        return "container.elinarVoidChest";
    }

    @Override
    public void initGui() {
        super.initGui();
        // Dissociate button left of the solidify button, both in the top-right corner.
        buttonList.add(
            new GuiMiniButton(
                BUTTON_DISSOCIATE,
                guiLeft + xSize - 56 - 52 - 4,
                guiTop + 3,
                52,
                13,
                I18n.format("button.elinarcurios.dissociate")));
        buttonList.add(
            new GuiMiniButton(
                BUTTON_SOLIDIFY,
                guiLeft + xSize - 56,
                guiTop + 3,
                52,
                13,
                I18n.format("button.elinarcurios.solidify")));
    }

    @Override
    protected void actionPerformed(final GuiButton button) {
        if (button.id == BUTTON_SOLIDIFY) {
            ElinarCurios.networkWrapper.sendToServer(new SolidifyVoidChestPacket.Message());
            return;
        }
        if (button.id == BUTTON_DISSOCIATE) {
            ElinarCurios.networkWrapper.sendToServer(new DissociateVoidChestPacket.Message());
            return;
        }
        super.actionPerformed(button);
    }
}
