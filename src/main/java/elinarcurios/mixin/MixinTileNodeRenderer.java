package elinarcurios.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import elinarcurios.util.ElinarProbe;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.nodes.NodeModifier;
import thaumcraft.api.nodes.NodeType;
import thaumcraft.client.renderers.tile.TileNodeRenderer;

/**
 * Aura node reveal for the worn Infinite Backpack.
 *
 * <p>
 * Thaumcraft decides visibility inside {@code func_147500_a} by reading the helmet slot. That
 * call site is already wrapped by Backhand's late mixin, and the plain {@code @Redirect} turned
 * out not to take effect in practice. The reliable hook is the value of the {@code goggles} /
 * {@code fullbright} arguments of {@code renderNode} itself: when the local player wears the
 * backpack with the "nodes" toggle on, they are forced to true - exactly the state Thaumcraft
 * uses when real goggles are worn.
 */
@Mixin(targets = "thaumcraft.client.renderers.tile.TileNodeRenderer", remap = false)
public abstract class MixinTileNodeRenderer {

    @Redirect(
        method = "func_147500_a",
        at = @At(
            value = "INVOKE",
            target = "Lthaumcraft/client/renderers/tile/TileNodeRenderer;renderNode(Lnet/minecraft/entity/EntityLivingBase;DZZFIIIFLthaumcraft/api/aspects/AspectList;Lthaumcraft/api/nodes/NodeType;Lthaumcraft/api/nodes/NodeModifier;)V"))
    private static void elinarcurios$redirectReveal(final EntityLivingBase viewer, final double distance,
        final boolean goggles, final boolean fullbright, final float scale, final int x, final int y, final int z,
        final float partialTicks, final AspectList aspects, final NodeType type, final NodeModifier modifier) {
        ElinarProbe.NODE_REDIRECT.incrementAndGet();
        if (!goggles && elinarcurios$shouldReveal()) {
            ElinarProbe.NODE_REDIRECT_FORCED.incrementAndGet();
            ElinarProbe.dump();
            TileNodeRenderer
                .renderNode(viewer, distance, true, true, scale, x, y, z, partialTicks, aspects, type, modifier);
            return;
        }
        ElinarProbe.dump();
        TileNodeRenderer
            .renderNode(viewer, distance, goggles, fullbright, scale, x, y, z, partialTicks, aspects, type, modifier);
    }

    @ModifyVariable(method = "renderNode", at = @At("HEAD"), argsOnly = true, index = 3)
    private static boolean elinarcurios$forceGoggles(final boolean goggles) {
        ElinarProbe.NODE_ARG.incrementAndGet();
        if (!goggles && elinarcurios$shouldReveal()) {
            ElinarProbe.NODE_ARG_FORCED.incrementAndGet();
            return true;
        }
        return goggles;
    }

    @ModifyVariable(method = "renderNode", at = @At("HEAD"), argsOnly = true, index = 4)
    private static boolean elinarcurios$forceFullbright(final boolean fullbright) {
        if (!fullbright && elinarcurios$shouldReveal()) return true;
        return fullbright;
    }

    private static boolean elinarcurios$shouldReveal() {
        try {
            final EntityPlayer player = Minecraft.getMinecraft().thePlayer;
            if (player == null) return false;
            final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
            return bag != null && BagSettings.getBoolean(bag, BagSettings.NODES);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
