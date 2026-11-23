package elinarcurios.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import elinarcurios.client.overlay.BagOverlay;

/**
 * Feeds the raw mouse/keyboard events to the floating backpack overlay before the current GUI
 * screen sees them.
 *
 * <p>
 * Injecting {@code GuiScreen.handleMouseInput} is not enough for ModularUI screens: their wrapper
 * GUI classes override the input handlers and never call super, so the overlay clicks went through
 * to the machine slots underneath. Redirecting the two {@code currentScreen.handle*Input()} call
 * sites inside {@code Minecraft.runTick} works for every GUI class regardless of overrides.
 *
 * <p>
 * Client-only target: on a dedicated server {@code Minecraft} does not exist and the mixin is
 * skipped.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraftMouseInput {

    @Redirect(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleMouseInput()V"))
    private void elinarcurios$bagOverlayMouse(final GuiScreen screen) {
        if (BagOverlay.handleRawMouseInput()) return;
        screen.handleMouseInput();
    }

    @Redirect(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleKeyboardInput()V"))
    private void elinarcurios$bagOverlayKeyboard(final GuiScreen screen) {
        if (BagOverlay.handleRawKeyboardInput()) return;
        screen.handleKeyboardInput();
    }
}
