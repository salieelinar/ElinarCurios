package elinarcurios.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiScreenEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Rendering and per-tick wiring of the floating backpack overlay.
 *
 * <p>
 * Mouse/keyboard interception lives in {@code MixinGuiScreen}: 1.7.10 Forge has no
 * {@code GuiScreenEvent.MouseInputEvent} / {@code KeyboardInputEvent} (those are 1.8+ APIs), so the
 * only way to stop the GUI below from also consuming a click is to inject at the head of
 * {@code GuiScreen.handleMouseInput} / {@code handleKeyboardInput}.
 */
@SideOnly(Side.CLIENT)
public class BagOverlayHandler {

    @SubscribeEvent
    public void onDrawScreen(final GuiScreenEvent.DrawScreenEvent.Post event) {
        // Container screens are drawn at the TAIL of GuiContainer.drawScreen (see
        // MixinGuiContainerBagOverlay), i.e. after the carried item and the tooltip; plain
        // GuiScreens have nothing drawn after this event.
        if (event.gui instanceof GuiContainer) return;
        BagOverlay.render(event.gui, event.mouseX, event.mouseY);
    }

    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BagOverlay.tick(Minecraft.getMinecraft());
    }
}
