package elinarcurios.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.nodes.NodeModifier;
import thaumcraft.api.nodes.NodeType;
import thaumcraft.client.renderers.tile.TileNodeRenderer;

/**
 * Aura node reveal for the worn Infinite Backpack.
 *
 * <p>
 * Thaumcraft decides visibility inside {@code TileNodeRenderer#func_147500_a} by reading the helmet
 * slot three times ({@code armorItemInSlot(3)}). That call site is already wrapped by Backhand's
 * late mixin, which intercepts it and answers with the <em>original</em> slot content, so a
 * redirect on the slot read never sees our goggles stack and the condition short-circuits to
 * "invisible".
 *
 * <p>
 * Instead the call to {@code renderNode(...)} itself is redirected: when the viewer wears the
 * backpack and its "nodes" toggle is on, the two visibility flags are forced to true (exactly what
 * Thaumcraft does when real goggles are worn / when a node jar is looked at). Nothing else in the
 * game redirects this call.
 */
@Mixin(targets = "thaumcraft.client.renderers.tile.TileNodeRenderer", remap = false)
public abstract class MixinTileNodeRenderer {

    @Redirect(
        method = "func_147500_a",
        at = @At(
            value = "INVOKE",
            target = "Lthaumcraft/client/renderers/tile/TileNodeRenderer;renderNode(Lnet/minecraft/entity/EntityLivingBase;DZZFIIIFLthaumcraft/api/aspects/AspectList;Lthaumcraft/api/nodes/NodeType;Lthaumcraft/api/nodes/NodeModifier;)V"))
    private static void elinarcurios$revealNodes(final EntityLivingBase viewer, final double distance,
        final boolean goggles, final boolean fullbright, final float scale, final int x, final int y, final int z,
        final float partialTicks, final AspectList aspects, final NodeType type, final NodeModifier modifier) {
        if (!goggles && viewer instanceof EntityPlayer) {
            final ItemStack bag = BagUtils.getWornInfiniteBackpack((EntityPlayer) viewer);
            if (bag != null && BagSettings.getBoolean(bag, BagSettings.NODES)) {
                TileNodeRenderer
                    .renderNode(viewer, distance, true, true, scale, x, y, z, partialTicks, aspects, type, modifier);
                return;
            }
        }
        TileNodeRenderer
            .renderNode(viewer, distance, goggles, fullbright, scale, x, y, z, partialTicks, aspects, type, modifier);
    }
}
