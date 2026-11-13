package elinarcurios.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Small flat button used for the equip/unequip, buff toggles and page tabs in the backpack GUI. */
@SideOnly(Side.CLIENT)
public class GuiMiniButton extends GuiButton {

    /** Highlighted state (used for the active page tab). */
    public boolean selected;

    public GuiMiniButton(final int id, final int x, final int y, final int w, final int h, final String text) {
        super(id, x, y, w, h, text);
    }

    @Override
    public void drawButton(final Minecraft mc, final int mouseX, final int mouseY) {
        if (!visible) return;
        final boolean hovered = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        final int bg = hovered ? 0xCCFFFFFF : selected ? 0xCCFFD700 : 0xAA555555;
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, bg);
        drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, 0xFF101010);
        drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, 0xFF101010);
        drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, 0xFF101010);
        drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, 0xFF101010);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        mc.fontRenderer.drawStringWithShadow(
            displayString,
            xPosition + (width - mc.fontRenderer.getStringWidth(displayString)) / 2,
            yPosition + (height - 8) / 2 + 1,
            0xFFFFFF);
    }
}
