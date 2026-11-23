package elinarcurios.mixin;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.compat.ThaumGogglesSwap;

/**
 * Central tile-entity render hook: swap the helmet slot for the whole dispatcher call, so whatever
 * renderer (or renderer wrapper added by other mods) runs inside sees goggles. This is the outer
 * swap; the Thaumcraft-specific hooks only bump the depth counter.
 */
@Mixin(net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcherGoggles {

    @Inject(method = "renderTileEntityAt", at = @At("HEAD"))
    private void elinarcurios$beginGoggles(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks, final CallbackInfo ci) {
        ThaumGogglesSwap.begin(net.minecraft.client.Minecraft.getMinecraft().thePlayer);
    }

    @Inject(method = "renderTileEntityAt", at = @At("RETURN"))
    private void elinarcurios$endGoggles(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks, final CallbackInfo ci) {
        ThaumGogglesSwap.end();
    }
}
