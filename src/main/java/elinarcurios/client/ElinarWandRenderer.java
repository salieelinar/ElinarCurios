package elinarcurios.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.IItemRenderer.ItemRenderType;
import net.minecraftforge.client.IItemRenderer.ItemRendererHelper;

import org.lwjgl.opengl.GL11;

import elinarcurios.client.model.ModelElinarWand;
import thaumcraft.api.wands.ItemFocusBasic;
import thaumcraft.client.lib.UtilsFX;
import thaumcraft.common.items.wands.ItemWandCasting;

/**
 * Replaces TC's ItemWandRenderer for the Celestial Wand only. Same hand/inventory
 * transforms; the longer rod and 15% wider solid caps come from
 * {@link ModelElinarWand}.
 */
public final class ElinarWandRenderer implements IItemRenderer {

    private final ModelElinarWand model = new ModelElinarWand();

    @Override
    public boolean handleRenderType(final ItemStack item, final ItemRenderType type) {
        return true;
    }

    @Override
    public boolean shouldUseRenderHelper(final ItemRenderType type, final ItemStack item,
        final ItemRendererHelper helper) {
        return helper != ItemRendererHelper.BLOCK_3D;
    }

    @Override
    public void renderItem(final ItemRenderType type, final ItemStack item, final Object... data) {
        final Minecraft mc = Minecraft.getMinecraft();
        if (item == null || !(item.getItem() instanceof ItemWandCasting)) return;

        final ItemWandCasting wand = (ItemWandCasting) item.getItem();
        final ItemStack focusStack = wand.getFocusItem(item);
        final float pt = UtilsFX.getTimer(mc).renderPartialTicks;

        EntityLivingBase wielder = null;
        if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
            wielder = (EntityLivingBase) data[1];
        }

        GL11.glPushMatrix();
        if (type != ItemRenderType.INVENTORY) {
            if (type == ItemRenderType.ENTITY) {
                GL11.glTranslated(0.0D, 1.0D, 0.0D);
            } else {
                GL11.glTranslated(0.5D, 1.5D, 0.5D);
                if (type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                    GL11.glScaled(1.0D, 1.1D, 1.0D);
                }
            }
        } else {
            GL11.glRotatef(66.0F, 0.0F, 0.0F, 1.0F);
            GL11.glTranslated(0.0D, 0.6D, 0.0D);
        }

        GL11.glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
        if (wielder != null && wielder instanceof EntityPlayer && ((EntityPlayer) wielder).getItemInUse() != null) {
            float t = ((EntityPlayer) wielder).getItemInUseDuration() + pt;
            if (t > 3.0F) t = 3.0F;

            GL11.glTranslated(0.0D, 1.0D, 0.0D);
            if (type != ItemRenderType.EQUIPPED_FIRST_PERSON) {
                GL11.glRotatef(33.0F, 0.0F, 0.0F, 1.0F);
            } else {
                GL11.glRotatef(10.0F, 1.0F, 0.0F, 0.0F);
                GL11.glRotatef(10.0F, 0.0F, 0.0F, 1.0F);
            }
            GL11.glRotatef(60.0F * (t / 3.0F), -1.0F, 0.0F, 0.0F);
            if (wand.animation != ItemFocusBasic.WandFocusAnimation.WAVE
                && (wand.getFocus(item) == null || wand.getFocus(item)
                    .getAnimation(focusStack) != ItemFocusBasic.WandFocusAnimation.WAVE)) {
                if (wand.getFocus(item) != null && wand.getFocus(item)
                    .getAnimation(focusStack) == ItemFocusBasic.WandFocusAnimation.CHARGE) {
                    float wave = MathHelper.sin((((EntityPlayer) wielder).getItemInUseDuration() + pt) / 0.8F) * 1.0F;
                    GL11.glRotatef(wave, 0.0F, 0.0F, 1.0F);
                    wave = MathHelper.sin((((EntityPlayer) wielder).getItemInUseDuration() + pt) / 0.7F) * 1.0F;
                    GL11.glRotatef(wave, 1.0F, 0.0F, 0.0F);
                }
            } else {
                float wave = MathHelper.sin((((EntityPlayer) wielder).getItemInUseDuration() + pt) / 10.0F) * 10.0F;
                GL11.glRotatef(wave, 0.0F, 0.0F, 1.0F);
                wave = MathHelper.sin((((EntityPlayer) wielder).getItemInUseDuration() + pt) / 15.0F) * 10.0F;
                GL11.glRotatef(wave, 1.0F, 0.0F, 0.0F);
            }
            GL11.glTranslated(0.0D, -1.0D, 0.0D);
        }

        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        this.model.render(item);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
    }
}
