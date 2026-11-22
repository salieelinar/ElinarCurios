package elinarcurios.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import elinarcurios.compat.ThaumGogglesSwap;

/**
 * Same detection override for the aura node renderer: its {@code IRevealer} check reads the helmet
 * slot, and Backhand wraps that read, so instead of redirecting the call the slot is swapped for
 * the duration of this method.
 */
@Mixin(targets = "thaumcraft.client.renderers.tile.TileNodeRenderer", remap = false)
public abstract class MixinTileNodeRendererGoggles {

    @Inject(method = "func_147500_a", at = @At("HEAD"))
    private void elinarcurios$beginGoggles(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks, final CallbackInfo ci) {
        ThaumGogglesSwap.begin(Minecraft.getMinecraft().thePlayer);
    }

    @Inject(method = "func_147500_a", at = @At("RETURN"))
    private void elinarcurios$endGoggles(final TileEntity tile, final double x, final double y, final double z,
        final float partialTicks, final CallbackInfo ci) {
        ThaumGogglesSwap.end();
    }
}
