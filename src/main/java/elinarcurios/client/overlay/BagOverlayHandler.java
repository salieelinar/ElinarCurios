package elinarcurios.client.overlay;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Per-tick wiring of the floating backpack overlay.
 *
 * <p>
 * Drawing happens in {@code MixinMinecraftBagOverlay} (right after the GUI screen finished
 * drawing) and input interception in {@code MixinGuiScreen}; this handler only keeps the overlay
 * alive while a GUI is open.
 */
@SideOnly(Side.CLIENT)
public class BagOverlayHandler {

    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BagOverlay.tick(Minecraft.getMinecraft());
    }

    /**
     * Draws the panel after the frame's GUI rendering (this is the only hook that also covers GUI
     * classes which override {@code drawScreen} without calling super, e.g. ModularUI screens).
     */
    @SubscribeEvent
    public void onRenderTick(final TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BagOverlay.renderAfterGui();
    }
}
