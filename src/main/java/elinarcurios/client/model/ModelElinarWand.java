package elinarcurios.client.model;

import java.awt.Color;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import thaumcraft.client.lib.UtilsFX;
import thaumcraft.client.renderers.block.BlockRenderer;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.items.wands.ItemWandCasting;

/**
 * The Celestial Wand's low-poly model, matching the approved concept:
 * TC ModelWand's rod + two 2x2x2 caps, with the rod lengthened 18 -> 24 and
 * the caps 15% wider than the rod. The rod is four textured quads (the original
 * 4-column 24x60 rune strip over the animated molten-space-time base); the caps
 * are solid deep orange-red boxes.
 *
 * <p>
 * Only the parts the Celestial Wand actually uses are kept: no staff/sceptre
 * scaling, no rune ring. The focus rendering is kept so installed focuses still
 * display on the wand.
 */
public class ModelElinarWand extends ModelBase {

    private static final float CAP_SCALE = 1.15F;
    private static final int ROD_FRAME_COUNT = 128;
    private static final int ROD_FRAME_TIME = 3;
    private static final ResourceLocation[] ROD_FRAMES = new ResourceLocation[ROD_FRAME_COUNT];

    static {
        for (int i = 0; i < ROD_FRAME_COUNT; i++) {
            ROD_FRAMES[i] = new ResourceLocation("elinarcurios", "textures/models/wand_rod_elinar_" + i + ".png");
        }
    }

    ModelRenderer Focus;
    ModelRenderer Cap;
    ModelRenderer CapBottom;
    private final RenderBlocks renderBlocks = new RenderBlocks();

    public ModelElinarWand() {
        this.textureWidth = 32;
        this.textureHeight = 32;

        this.Cap = new ModelRenderer(this, 0, 0);
        this.Cap.addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2);
        this.Cap.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.Cap.setTextureSize(16, 18);
        this.Cap.mirror = true;

        this.CapBottom = new ModelRenderer(this, 0, 0);
        this.CapBottom.addBox(-1.0F, -1.0F, -1.0F, 2, 2, 2);
        this.CapBottom.setRotationPoint(0.0F, 26.0F, 0.0F);
        this.CapBottom.setTextureSize(16, 18);
        this.CapBottom.mirror = true;

        this.Focus = new ModelRenderer(this, 0, 0);
        this.Focus.addBox(-3.0F, -6.0F, -3.0F, 6, 6, 6);
        this.Focus.setRotationPoint(0.0F, 0.0F, 0.0F);
        this.Focus.setTextureSize(64, 32);
        this.Focus.mirror = true;
    }

    public void render(final ItemStack wandStack) {
        if (RenderManager.instance.renderEngine == null) return;

        final ItemWandCasting wand = (ItemWandCasting) wandStack.getItem();
        final ItemStack focusStack = wand.getFocusItem(wandStack);
        final EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        // Rod: four quads, one per original rune column. The molten-space-time
        // animation is bound per frame: a standalone bindTexture texture does not
        // process .mcmeta animation, so we pick one of the 32 64x64 frame textures.
        final int rodFrame = (player.ticksExisted / ROD_FRAME_TIME) % ROD_FRAME_COUNT;
        Minecraft.getMinecraft().renderEngine.bindTexture(ROD_FRAMES[rodFrame]);
        GL11.glPushMatrix();
        if (wand.getRod(wandStack)
            .isGlowing()) {
            final int j = (int) (200.0F + MathHelper.sin(player.ticksExisted) * 5.0F + 5.0F);
            final int k = j % 65536;
            final int l = j / 65536;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, k / 1.0F, l / 1.0F);
        }
        this.renderRod();
        if (wand.getRod(wandStack)
            .isGlowing()) {
            final int i = player.getBrightnessForRender(0.0F);
            final int j = i % 65536;
            final int k = i / 65536;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, j / 1.0F, k / 1.0F);
        }
        GL11.glPopMatrix();

        // Caps: solid 2x2x2 boxes, 15% wider than the rod.
        Minecraft.getMinecraft().renderEngine.bindTexture(
            wand.getCap(wandStack)
                .getTexture());
        GL11.glPushMatrix();
        GL11.glScaled(CAP_SCALE, 1.0F, CAP_SCALE);
        this.Cap.render(0.0625F);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glScaled(CAP_SCALE, 1.0F, CAP_SCALE);
        this.CapBottom.render(0.0625F);
        GL11.glPopMatrix();

        if (wand.getFocus(wandStack) != null) {
            this.renderFocus(wandStack, wand, focusStack, player);
        }
    }

    private void renderRod() {
        final Tessellator t = Tessellator.instance;
        final float s = 0.0625F;
        final float col = 6.0F / 64.0F;
        final float v0 = 0.0F;
        final float v1 = 60.0F / 64.0F;

        t.startDrawingQuads();
        // +X (outward winding: normal +X)
        t.setNormal(1.0F, 0.0F, 0.0F);
        t.addVertexWithUV(1 * s, 1 * s, 1 * s, 0.0F, v1);
        t.addVertexWithUV(1 * s, 1 * s, -1 * s, col, v1);
        t.addVertexWithUV(1 * s, 25 * s, -1 * s, col, v0);
        t.addVertexWithUV(1 * s, 25 * s, 1 * s, 0.0F, v0);
        // -X
        t.setNormal(-1.0F, 0.0F, 0.0F);
        t.addVertexWithUV(-1 * s, 1 * s, -1 * s, col, v1);
        t.addVertexWithUV(-1 * s, 1 * s, 1 * s, col * 2, v1);
        t.addVertexWithUV(-1 * s, 25 * s, 1 * s, col * 2, v0);
        t.addVertexWithUV(-1 * s, 25 * s, -1 * s, col, v0);
        // +Z
        t.setNormal(0.0F, 0.0F, 1.0F);
        t.addVertexWithUV(-1 * s, 1 * s, 1 * s, col * 2, v1);
        t.addVertexWithUV(1 * s, 1 * s, 1 * s, col * 3, v1);
        t.addVertexWithUV(1 * s, 25 * s, 1 * s, col * 3, v0);
        t.addVertexWithUV(-1 * s, 25 * s, 1 * s, col * 2, v0);
        // -Z
        t.setNormal(0.0F, 0.0F, -1.0F);
        t.addVertexWithUV(1 * s, 1 * s, -1 * s, col * 3, v1);
        t.addVertexWithUV(-1 * s, 1 * s, -1 * s, col * 4, v1);
        t.addVertexWithUV(-1 * s, 25 * s, -1 * s, col * 4, v0);
        t.addVertexWithUV(1 * s, 25 * s, -1 * s, col * 3, v0);
        t.draw();
    }

    private void renderFocus(final ItemStack wandStack, final ItemWandCasting wand, final ItemStack focusStack,
        final EntityPlayer player) {
        if (wand.getFocus(wandStack)
            .getOrnament(focusStack) != null) {
            GL11.glPushMatrix();
            GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
            final Tessellator tessellator = Tessellator.instance;
            final IIcon icon = wand.getFocus(wandStack)
                .getOrnament(focusStack);
            final float f1 = icon.getMaxU();
            final float f2 = icon.getMinV();
            final float f3 = icon.getMinU();
            final float f4 = icon.getMaxV();
            RenderManager.instance.renderEngine.bindTexture(TextureMap.locationItemsTexture);
            GL11.glPushMatrix();
            GL11.glTranslatef(-0.25F, -0.1F, 0.0275F);
            GL11.glScaled(0.5, 0.5, 0.5);
            ItemRenderer.renderItemIn2D(tessellator, f1, f2, f3, f4, icon.getIconWidth(), icon.getIconHeight(), 0.1F);
            GL11.glPopMatrix();
            GL11.glPushMatrix();
            GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
            GL11.glTranslatef(-0.25F, -0.1F, 0.0275F);
            GL11.glScaled(0.5, 0.5, 0.5);
            ItemRenderer.renderItemIn2D(tessellator, f1, f2, f3, f4, icon.getIconWidth(), icon.getIconHeight(), 0.1F);
            GL11.glPopMatrix();
            GL11.glPopMatrix();
        }

        float alpha = 0.95F;
        if (wand.getFocus(wandStack)
            .getFocusDepthLayerIcon(focusStack) != null) {
            GL11.glPushMatrix();
            GL11.glTranslatef(0.0F, -0.09F, 0.0F);
            GL11.glScaled(0.16, 0.16, 0.16);
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.locationItemsTexture);
            this.renderBlocks.setRenderBoundsFromBlock(Blocks.stone);
            BlockRenderer.drawFaces(
                this.renderBlocks,
                null,
                wand.getFocus(wandStack)
                    .getFocusDepthLayerIcon(focusStack),
                false);
            GL11.glPopMatrix();
            alpha = 0.6F;
        }

        if (Thaumcraft.isHalloween) {
            UtilsFX.bindTexture("textures/models/spec_h.png");
        } else {
            UtilsFX.bindTexture("textures/models/wand.png");
        }

        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        final Color c = new Color(
            wand.getFocus(wandStack)
                .getFocusColor(focusStack));
        GL11.glColor4f(c.getRed() / 255.0F, c.getGreen() / 255.0F, c.getBlue() / 255.0F, alpha);
        final int j = (int) (195.0F + MathHelper.sin(player.ticksExisted / 3.0F) * 10.0F + 10.0F);
        final int k = j % 65536;
        final int l = j / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, k / 1.0F, l / 1.0F);
        this.Focus.render(0.0625F);
        GL11.glPopMatrix();
    }
}
