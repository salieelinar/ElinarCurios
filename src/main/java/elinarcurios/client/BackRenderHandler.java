package elinarcurios.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.client.model.ModelBackpack;
import elinarcurios.common.Reference;
import elinarcurios.util.BagUtils;

/**
 * Renders the worn Infinite Backpack on the player's back (Dragon skin), fully
 * self-contained: no AdventureBackpack2 classes are referenced.
 */
@SideOnly(Side.CLIENT)
public class BackRenderHandler {

    private static final ModelBackpack MODEL = new ModelBackpack();
    private static final ResourceLocation TEXTURE = new ResourceLocation(
        Reference.MOD_ID,
        "textures/models/dragonBackpack.png");

    @SubscribeEvent
    public void playerSpecialsRendering(final RenderPlayerEvent.Specials.Pre event) {
        final EntityPlayer player = event.entityPlayer;
        final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
        if (bag == null) return;

        try {
            GL11.glPushMatrix();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            MODEL.copyBodyRotation(event.renderer.modelBipedMain);
            Minecraft.getMinecraft().renderEngine.bindTexture(TEXTURE);
            if (player.isInvisible()) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.15F);
                GL11.glDepthMask(false);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                MODEL.render(player, 0, 0, 0, 0, 0, 0.0625F);
                GL11.glDepthMask(true);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
                MODEL.render(player, 0, 0, 0, 0, 0, 0.0625F);
            }
            GL11.glPopMatrix();
        } catch (Exception ignored) {}
    }
}
