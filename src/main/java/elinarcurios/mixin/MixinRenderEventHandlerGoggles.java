package elinarcurios.mixin;

import net.minecraftforge.client.event.DrawBlockHighlightEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.compat.ThaumGogglesSwap;

/**
 * Forces Thaumcraft's goggles detection during its block-highlight overlay (aspect tags for
 * containers and nodes) without replacing or hiding the player's real helmet.
 */
@Mixin(targets = "thaumcraft.client.lib.RenderEventHandler", remap = false)
public abstract class MixinRenderEventHandlerGoggles {

    @Inject(method = "blockHighlight", at = @At("HEAD"))
    private void elinarcurios$beginGoggles(final DrawBlockHighlightEvent event, final CallbackInfo ci) {
        ThaumGogglesSwap.begin(event == null ? null : event.player);
    }

    @Inject(method = "blockHighlight", at = @At("RETURN"))
    private void elinarcurios$endGoggles(final DrawBlockHighlightEvent event, final CallbackInfo ci) {
        ThaumGogglesSwap.end();
    }
}
