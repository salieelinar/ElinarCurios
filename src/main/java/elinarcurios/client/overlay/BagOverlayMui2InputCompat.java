package elinarcurios.client.overlay;

import net.minecraftforge.common.MinecraftForge;

import com.cleanroommc.modularui.api.event.KeyboardInputEvent;
import com.cleanroommc.modularui.api.event.MouseInputEvent;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Takes the floating backpack overlay input before ModularUI2 sees a raw input event.
 *
 * <p>
 * MUI2 installs its own {@code @Redirect} on {@code currentScreen.handleMouseInput()} /
 * {@code handleKeyboardInput()} inside {@code Minecraft.runTick} and posts these cancelable events
 * first; when an event is cancelled the original GUI input is skipped entirely. Listening with
 * {@link EventPriority#HIGHEST} lets the overlay consume its events before MUI2's own handler, and
 * cancelling keeps the click away from both the ModularUI screen and the GUI below.
 *
 * <p>
 * The class references MUI2 types directly, so the guard lives at the call site in ClientProxy
 * ({@code Loader.isModLoaded("modularui2")}).
 */
@SideOnly(Side.CLIENT)
public final class BagOverlayMui2InputCompat {

    private BagOverlayMui2InputCompat() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new Handler());
    }

    private static final class Handler {

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onMouseInputPre(final MouseInputEvent.Pre event) {
            if (BagOverlay.handleRawMouseInput()) event.setCanceled(true);
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onKeyboardInputPre(final KeyboardInputEvent.Pre event) {
            if (BagOverlay.handleRawKeyboardInput()) event.setCanceled(true);
        }
    }
}
