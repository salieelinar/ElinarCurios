package elinarcurios.client.overlay;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.client.RenderItemInfinity;
import elinarcurios.common.Reference;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.network.EquipUnequipBagPacket;

/**
 * Drawing and input of the floating Infinite Backpack overlay.
 *
 * <p>
 * Rendered after the underlying GUI ({@code GuiScreenEvent.DrawScreenEvent.Post}) and fed by the
 * raw mouse/key events first: hits inside the panel are cancelled and handled here, everything
 * outside is left to the GUI below, which keeps the vanilla inventory / GregTech machine screens
 * fully usable at the same time.
 */
@SideOnly(Side.CLIENT)
public final class BagOverlay {

    private static final ResourceLocation TEXTURE = new ResourceLocation(
        Reference.MOD_ID,
        "textures/gui/infiniteBackpack.png");

    private static final RenderItemInfinity ITEM_RENDERER = new RenderItemInfinity();

    private static final int COLUMNS = 27;
    private static final int ROWS = 9;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 18;
    private static final int BUTTON_Y = 3;
    private static final int PAGE_TAB_WIDTH = 13;
    private static final int PAGE_TAB_STEP = 15;
    private static final int BUFF_WIDTH = 34;
    private static final int BUTTON_HEIGHT = 13;
    private static final int EQUIP_WIDTH = 52;
    private static final int EQUIP_MARGIN = 56;
    private static final int BUFF_STEP = 38;
    private static final double TEXTURE_SIZE = 500.0;

    private BagOverlay() {}

    // ------------------------------------------------------------------ rendering

    /**
     * Entry point from {@code MixinGuiContainerBagOverlay}: runs at the TAIL of
     * {@code GuiContainer.drawScreen}, i.e. after slot items, the carried item and the
     * hovered-slot tooltip, so the panel is a clean top layer.
     */
    public static void renderAtTail(final int mouseX, final int mouseY) {
        final GuiScreen screen = Minecraft.getMinecraft().currentScreen;
        if (screen == null) return;
        render(screen, mouseX, mouseY);
    }

    public static void render(final GuiScreen screen, final int mouseX, final int mouseY) {
        if (!BagOverlayState.isOpen()) return;
        final Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;
        BagOverlayState.ensurePosition(screen.width, screen.height);
        if (BagOverlayState.isDragging()) {
            if (!Mouse.isButtonDown(0)) BagOverlayState.endDrag();
            else BagOverlayState
                .setPos(mouseX - BagOverlayState.getDragOffsetX(), mouseY - BagOverlayState.getDragOffsetY());
        }
        final int x = BagOverlayState.getX();
        final int y = BagOverlayState.getY();
        final int width = BagOverlayState.WIDTH;
        final int height = BagOverlayState.HEIGHT;

        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_LIGHTING_BIT
                | GL11.GL_TRANSFORM_BIT
                | GL11.GL_TEXTURE_BIT);
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        // Drop shadow + hard border: makes the panel read as its own window instead of a
        // slab of GUI texture glued onto the screen below it.
        rect(x - 3, y - 3, x + width + 3, y + height + 3, 0x50000000);
        rect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF000000);

        drawBackground(mc, x, y, width, height);
        drawTitle(mc, x, y);
        drawPageTabs(mc, x, y, mouseX, mouseY);
        drawBuffButtons(mc, x, y, width, mouseX, mouseY);
        drawEquipButton(mc, x, y, width, mouseX, mouseY);
        drawStorage(mc, x, y, mouseX, mouseY);
        drawCarriedItem(mc, mouseX, mouseY);
        drawHoveredTooltip(mc, screen, x, y, mouseX, mouseY);

        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    private static void drawBackground(final Minecraft mc, final int x, final int y, final int width,
        final int height) {
        mc.getTextureManager()
            .bindTexture(TEXTURE);
        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorOpaque(255, 255, 255);
        final double vMax = height / TEXTURE_SIZE;
        tessellator.addVertexWithUV(x, y + height, 0, 0.0, vMax);
        tessellator.addVertexWithUV(x + width, y + height, 0, 1.0, vMax);
        tessellator.addVertexWithUV(x + width, y, 0, 1.0, 0.0);
        tessellator.addVertexWithUV(x, y, 0, 0.0, 0.0);
        tessellator.draw();
    }

    private static void drawTitle(final Minecraft mc, final int x, final int y) {
        mc.fontRenderer.drawString(I18n.format("container.infiniteBackpack"), x + 7, y + 7, 0x404040);
    }

    private static void drawPageTabs(final Minecraft mc, final int x, final int y, final int mouseX, final int mouseY) {
        final int startX = x + pageTabStart();
        for (int i = 0; i < ItemInfiniteBackpack.PAGE_COUNT; i++) {
            final int buttonX = startX + i * PAGE_TAB_STEP;
            final int buttonY = y + BUTTON_Y;
            drawMiniButton(
                mc,
                buttonX,
                buttonY,
                PAGE_TAB_WIDTH,
                BUTTON_HEIGHT,
                Integer.toString(i + 1),
                BagOverlayState.getPage() == i,
                hovered(mouseX, mouseY, buttonX, buttonY, PAGE_TAB_WIDTH, BUTTON_HEIGHT));
        }
    }

    private static void drawBuffButtons(final Minecraft mc, final int x, final int y, final int width, final int mouseX,
        final int mouseY) {
        final int count = ItemInfiniteBackpack.BUFF_KEYS.length;
        for (int i = 0; i < count; i++) {
            final int buttonX = x + width - EQUIP_MARGIN - (count - i) * BUFF_STEP;
            final int buttonY = y + BUTTON_Y;
            final boolean enabled = BagOverlayState.isBuffEnabled(i);
            drawMiniButton(
                mc,
                buttonX,
                buttonY,
                BUFF_WIDTH,
                BUTTON_HEIGHT,
                I18n.format(ItemInfiniteBackpack.BUFF_LANG_KEYS[i]) + (enabled ? "\u221a" : "\u00d7"),
                false,
                hovered(mouseX, mouseY, buttonX, buttonY, BUFF_WIDTH, BUTTON_HEIGHT));
        }
    }

    private static void drawEquipButton(final Minecraft mc, final int x, final int y, final int width, final int mouseX,
        final int mouseY) {
        final int buttonX = x + width - EQUIP_MARGIN;
        final int buttonY = y + BUTTON_Y;
        final String label = I18n
            .format(BagOverlayState.getFrom() == 0 ? "button.elinarcurios.unequip" : "button.elinarcurios.equip");
        drawMiniButton(
            mc,
            buttonX,
            buttonY,
            EQUIP_WIDTH,
            BUTTON_HEIGHT,
            label,
            false,
            hovered(mouseX, mouseY, buttonX, buttonY, EQUIP_WIDTH, BUTTON_HEIGHT));
    }

    private static void drawStorage(final Minecraft mc, final int x, final int y, final int mouseX, final int mouseY) {
        final int hoveredSlot = slotAt(x, y, mouseX, mouseY);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        for (int i = 0; i < BagOverlayState.SLOTS; i++) {
            final ItemStack stack = BagOverlayState.getStack(i);
            if (stack == null) continue;
            final int slotX = x + GRID_X + (i % COLUMNS) * SLOT_SIZE;
            final int slotY = y + GRID_Y + (i / COLUMNS) * SLOT_SIZE;
            ITEM_RENDERER.zLevel = 100F;
            ITEM_RENDERER.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, slotX, slotY);
            ITEM_RENDERER.renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, slotX, slotY, null);
            ITEM_RENDERER.zLevel = 0F;
        }
        RenderHelper.disableStandardItemLighting();
        if (hoveredSlot >= 0) {
            final int slotX = x + GRID_X + (hoveredSlot % COLUMNS) * SLOT_SIZE;
            final int slotY = y + GRID_Y + (hoveredSlot / COLUMNS) * SLOT_SIZE;
            rect(slotX, slotY, slotX + 16, slotY + 16, 0x66FFFFFF);
        }
    }

    private static void drawCarriedItem(final Minecraft mc, final int mouseX, final int mouseY) {
        final ItemStack carried = mc.thePlayer == null ? null : mc.thePlayer.inventory.getItemStack();
        if (carried == null) return;
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        ITEM_RENDERER.zLevel = 200F;
        ITEM_RENDERER
            .renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), carried, mouseX - 8, mouseY - 8);
        ITEM_RENDERER
            .renderItemOverlayIntoGUI(mc.fontRenderer, mc.getTextureManager(), carried, mouseX - 8, mouseY - 8, null);
        ITEM_RENDERER.zLevel = 0F;
        RenderHelper.disableStandardItemLighting();
    }

    private static void drawHoveredTooltip(final Minecraft mc, final GuiScreen screen, final int x, final int y,
        final int mouseX, final int mouseY) {
        if (mc.thePlayer == null || mc.thePlayer.inventory.getItemStack() != null) return;
        final int slot = slotAt(x, y, mouseX, mouseY);
        if (slot < 0) return;
        final ItemStack stack = BagOverlayState.getStack(slot);
        if (stack == null) return;
        drawTooltip(mc, screen, stack, mouseX, mouseY);
    }

    private static void drawTooltip(final Minecraft mc, final GuiScreen screen, final ItemStack stack, final int mouseX,
        final int mouseY) {
        final List<String> lines = stack.getTooltip(mc.thePlayer, mc.gameSettings.advancedItemTooltips);
        if (lines == null || lines.isEmpty()) return;
        int width = 0;
        for (String line : lines) width = Math.max(width, mc.fontRenderer.getStringWidth(line));
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;
        if (tooltipX + width + 6 > screen.width) tooltipX = mouseX - width - 16;
        if (tooltipY + lines.size() * 10 + 6 > screen.height) {
            tooltipY = screen.height - lines.size() * 10 - 6;
        }
        if (tooltipY < 0) tooltipY = 0;

        rect(tooltipX - 3, tooltipY - 3, tooltipX + width + 3, tooltipY + lines.size() * 10 + 3, 0xF0100010);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        for (int i = 0; i < lines.size(); i++) {
            mc.fontRenderer.drawStringWithShadow(lines.get(i), tooltipX, tooltipY + i * 10, 0xFFFFFF);
        }
    }

    private static void drawMiniButton(final Minecraft mc, final int x, final int y, final int width, final int height,
        final String label, final boolean selected, final boolean hovered) {
        final int background = hovered ? 0xCCFFFFFF : selected ? 0xCCFFD700 : 0xAA555555;
        rect(x, y, x + width, y + height, background);
        rect(x, y, x + width, y + 1, 0xFF101010);
        rect(x, y + height - 1, x + width, y + height, 0xFF101010);
        rect(x, y, x + 1, y + height, 0xFF101010);
        rect(x + width - 1, y, x + width, y + height, 0xFF101010);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        mc.fontRenderer.drawStringWithShadow(
            label,
            x + (width - mc.fontRenderer.getStringWidth(label)) / 2,
            y + (height - 8) / 2 + 1,
            0xFFFFFF);
    }

    /** Solid rectangle; expects nothing about the caller's GL state and restores it. */
    private static void rect(final int x1, final int y1, final int x2, final int y2, final int argb) {
        final float alpha = (argb >> 24 & 0xFF) / 255F;
        final float red = (argb >> 16 & 0xFF) / 255F;
        final float green = (argb >> 8 & 0xFF) / 255F;
        final float blue = (argb & 0xFF) / 255F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(red, green, blue, alpha);
        final Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertex(x1, y2, 0);
        tessellator.addVertex(x2, y2, 0);
        tessellator.addVertex(x2, y1, 0);
        tessellator.addVertex(x1, y1, 0);
        tessellator.draw();
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    // ------------------------------------------------------------------ input

    /** Called from MixinGuiScreen at the head of {@code GuiScreen.handleMouseInput}. */
    public static boolean handleRawMouseInput() {
        if (!BagOverlayState.isOpen()) return false;
        final Minecraft mc = Minecraft.getMinecraft();
        final GuiScreen screen = mc.currentScreen;
        if (screen == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) return false;
        final int mouseX = Mouse.getEventX() * screen.width / mc.displayWidth;
        final int mouseY = screen.height - Mouse.getEventY() * screen.height / mc.displayHeight - 1;
        final int button = Mouse.getEventButton();
        final boolean pressed = Mouse.getEventButtonState();
        return mouseInput(screen, mouseX, mouseY, button, pressed);
    }

    /** Called from MixinGuiScreen at the head of {@code GuiScreen.handleKeyboardInput}. */
    public static boolean handleRawKeyboardInput() {
        if (!BagOverlayState.isOpen()) return false;
        if (!Keyboard.getEventKeyState()) return false;
        return keyInput(Keyboard.getEventKey());
    }

    /** Returns true when the event belongs to the overlay and must not reach the GUI below. */
    public static boolean mouseInput(final GuiScreen screen, final int mouseX, final int mouseY, final int button,
        final boolean pressed) {
        if (!BagOverlayState.isOpen()) return false;
        final int x = BagOverlayState.getX();
        final int y = BagOverlayState.getY();
        final boolean inside = mouseX >= x && mouseX < x + BagOverlayState.WIDTH
            && mouseY >= y
            && mouseY < y + BagOverlayState.HEIGHT;
        if (BagOverlayState.isDragging()) {
            if (!pressed) BagOverlayState.endDrag();
            return true;
        }
        if (!inside) return false;
        // Mouse motion (button == -1) must stay with the GUI below; the wheel is swallowed
        // so the GUI below does not scroll while the pointer is over the panel.
        if (button < 0) return Mouse.getEventDWheel() != 0;
        if (!pressed) return true;
        if (button != 0 && button != 1) return true;

        final int pageX = x + pageTabStart();
        for (int i = 0; i < ItemInfiniteBackpack.PAGE_COUNT; i++) {
            final int buttonX = pageX + i * PAGE_TAB_STEP;
            if (hovered(mouseX, mouseY, buttonX, y + BUTTON_Y, PAGE_TAB_WIDTH, BUTTON_HEIGHT)) {
                BagOverlayState.switchPage(i);
                return true;
            }
        }
        final int buffCount = ItemInfiniteBackpack.BUFF_KEYS.length;
        for (int i = 0; i < buffCount; i++) {
            final int buttonX = x + BagOverlayState.WIDTH - EQUIP_MARGIN - (buffCount - i) * BUFF_STEP;
            if (hovered(mouseX, mouseY, buttonX, y + BUTTON_Y, BUFF_WIDTH, BUTTON_HEIGHT)) {
                BagOverlayState.toggleBuff(i);
                return true;
            }
        }
        if (hovered(
            mouseX,
            mouseY,
            x + BagOverlayState.WIDTH - EQUIP_MARGIN,
            y + BUTTON_Y,
            EQUIP_WIDTH,
            BUTTON_HEIGHT)) {
            final int action = BagOverlayState.getFrom() == 0 ? EquipUnequipBagPacket.UNEQUIP
                : EquipUnequipBagPacket.EQUIP;
            ElinarCurios.networkWrapper.sendToServer(new EquipUnequipBagPacket.Message((byte) action));
            BagOverlayState.close();
            return true;
        }
        final int slot = slotAt(x, y, mouseX, mouseY);
        if (slot >= 0) {
            BagOverlayState.clickSlot(slot, button, isShiftDown());
            return true;
        }
        // Everything that is not a slot or a button is a drag handle: the panel can be picked up
        // anywhere (title bar, gaps between the tabs, the empty margins around the grid, the
        // border area) and moved around freely.
        if (button == 0) BagOverlayState.beginDrag(mouseX - x, mouseY - y);
        return true;
    }

    /** Returns true when the key press was consumed (Esc closes the panel, not the GUI below). */
    public static boolean keyInput(final int keyCode) {
        if (!BagOverlayState.isOpen()) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            BagOverlayState.close();
            return true;
        }
        return false;
    }

    /** Per-tick housekeeping: the overlay only lives while a GUI is open in a running world. */
    public static void tick(final Minecraft mc) {
        if (!BagOverlayState.isOpen()) return;
        if (mc.currentScreen == null || mc.thePlayer == null) {
            BagOverlayState.close();
            return;
        }
        if (BagOverlayState.isDragging() && !Mouse.isButtonDown(0)) BagOverlayState.endDrag();
    }

    // ------------------------------------------------------------------ geometry

    private static int pageTabStart() {
        return 7 + Minecraft.getMinecraft().fontRenderer.getStringWidth(I18n.format("container.infiniteBackpack")) + 10;
    }

    private static boolean hovered(final int mouseX, final int mouseY, final int x, final int y, final int width,
        final int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int slotAt(final int x, final int y, final int mouseX, final int mouseY) {
        final int gridX = mouseX - x - GRID_X;
        final int gridY = mouseY - y - GRID_Y;
        if (gridX < 0 || gridY < 0) return -1;
        final int column = gridX / SLOT_SIZE;
        final int row = gridY / SLOT_SIZE;
        if (column >= COLUMNS || row >= ROWS) return -1;
        if (gridX - column * SLOT_SIZE >= 16 || gridY - row * SLOT_SIZE >= 16) return -1;
        return row * COLUMNS + column;
    }

    private static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }
}
