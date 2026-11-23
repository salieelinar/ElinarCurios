package elinarcurios.client.overlay;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiAdapter;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Makes NEI's item panel give way to the floating backpack panel.
 *
 * <p>
 * NEI lays its item panel over the whole screen edge; without this adapter the panel keeps
 * drawing under / around the overlay. Exposing the overlay rectangle through
 * {@code hideItemPanelSlot} lets NEI blank out exactly the slots the overlay covers (same idea as
 * GTNL's floating-window NEI adapter).
 *
 * <p>
 * The class references NEI types directly, so callers must guard with
 * {@code Loader.isModLoaded("NotEnoughItems")} at the call site (see ClientProxy).
 */
@SideOnly(Side.CLIENT)
public final class BagOverlayNEICompat {

    private BagOverlayNEICompat() {}

    public static void register() {
        API.registerNEIGuiHandler(new Handler());
    }

    private static final class Handler extends INEIGuiAdapter {

        @Override
        public boolean hideItemPanelSlot(final GuiContainer gui, final int x, final int y, final int width,
            final int height) {
            if (!BagOverlayState.isOpen()) return false;
            final int panelX = BagOverlayState.getX();
            final int panelY = BagOverlayState.getY();
            return x < panelX + BagOverlayState.WIDTH && x + width > panelX
                && y < panelY + BagOverlayState.HEIGHT
                && y + height > panelY;
        }
    }
}
