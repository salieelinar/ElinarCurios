package elinarcurios.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.compat.ThaumGogglesSwap;
import elinarcurios.util.GogglesState;

/**
 * Central tile-entity render hook: swap the helmet slot for the whole dispatcher call, so whatever
 * renderer (or renderer wrapper added by other mods) runs inside sees goggles. This is the outer
 * swap; the Thaumcraft-specific hooks only bump the depth counter.
 *
 * <p>
 * The per-tick cached {@link GogglesState#active} flag is checked first so the hot path only pays
 * one volatile read when the feature is off (or Thaumcraft is absent).
 */
@Mixin(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcherGoggles {

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"))
    private void elinarcurios$beginGoggles(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks, final CallbackInfo ci) {
        if (!GogglesState.active) return;
        ThaumGogglesSwap.begin(Minecraft.getMinecraft().thePlayer);
    }

    @Inject(method = "renderTileEntityAt", at = @At("RETURN"))
    private void elinarcurios$endGoggles(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks, final CallbackInfo ci) {
        ThaumGogglesSwap.end();
    }
}
