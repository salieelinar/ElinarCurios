package elinarcurios.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.client.overlay.BagOverlay;

/**
 * Draws the floating Infinite Backpack overlay at the very end of a container screen.
 *
 * <p>
 * {@code DrawScreenEvent.Post} fires inside {@code GuiScreen.drawScreen()}, which
 * {@code GuiContainer.drawScreen()} calls <em>before</em> slot items, the carried item and the
 * hovered-slot tooltip. Drawing there makes those layers appear on top of the panel. The TAIL
 * injection runs after all of them, so the panel is a clean top window and can draw its own
 * carried item / tooltip (with the int-safe K/M/B item renderer).
 *
 * <p>
 * Client-only target: on a dedicated server {@code GuiContainer} does not exist and the mixin is
 * skipped.
 */
@Mixin(value = GuiContainer.class, priority = 500)
public abstract class MixinGuiContainerBagOverlay {

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void elinarcurios$drawBagOverlay(final int mouseX, final int mouseY, final float partialTicks,
        final CallbackInfo ci) {
        BagOverlay.renderAtTail(mouseX, mouseY);
    }
}
