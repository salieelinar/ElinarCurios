package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cpw.mods.fml.common.Loader;
import elinarcurios.compat.ThaumWailaAspectNbt;

/**
 * Waila's Thaumcraft addon reads the armour array directly (it does not go through
 * {@code armorItemInSlot}), so the InventoryPlayer hook cannot reach it. This gate short-circuits
 * its NBT collection with the very same "Aspects" payload it would have written for goggles.
 */
@Mixin(targets = "mcp.mobius.waila.addons.thaumcraft.HUDHandlerIAspectContainer", remap = false)
public abstract class MixinWailaNodeAspects {

    @Inject(method = "getNBTData", at = @At("HEAD"), cancellable = true)
    private void elinarcurios$aspects(final EntityPlayerMP player, final TileEntity tile, final NBTTagCompound tag,
        final World world, final int x, final int y, final int z, final CallbackInfoReturnable<NBTTagCompound> cir) {
        if (!elinarcurios$thaumcraftLoaded()) return;
        if (ThaumWailaAspectNbt.addAspects(player, tile, tag)) {
            cir.setReturnValue(tag);
        }
    }

    private static boolean elinarcurios$thaumcraftLoaded() {
        try {
            return Loader.isModLoaded("Thaumcraft");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
