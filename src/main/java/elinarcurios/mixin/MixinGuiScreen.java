package elinarcurios.mixin;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.client.overlay.BagOverlay;

/**
 * Routes the raw GUI input of the floating Infinite Backpack overlay.
 *
 * <p>
 * 1.7.10 Forge has no cancellable GUI mouse/keyboard events, so the overlay hooks the head of
 * {@code GuiScreen.handleMouseInput} / {@code handleKeyboardInput}: when the pointer is over the
 * panel (or Esc is pressed while it is open) the vanilla handler is cancelled, otherwise it runs
 * untouched. That is what keeps the vanilla inventory / GregTech machine GUI usable below the
 * panel. The overlay itself is client-only; on a dedicated server the {@code GuiScreen} target does
 * not exist and this mixin is skipped.
 */
@Mixin(GuiScreen.class)
public abstract class MixinGuiScreen {

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$bagOverlayMouse(final CallbackInfo ci) {
        if (BagOverlay.handleRawMouseInput()) ci.cancel();
    }

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$bagOverlayKeyboard(final CallbackInfo ci) {
        if (BagOverlay.handleRawKeyboardInput()) ci.cancel();
    }
}
