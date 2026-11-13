package elinarcurios.client.model;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Backpack model worn on the player's back.
 *
 * <p>
 * Uses the same UV layout as AdventureBackpack2's ModelBackpackArmor (LGPL-3.0), so the
 * Dragon skin texture maps correctly onto it. Tank/tool parts are omitted.
 */
@SideOnly(Side.CLIENT)
public class ModelBackpack extends ModelBiped {

    public final ModelRenderer mainBody;

    public ModelBackpack() {
        super();
        textureWidth = 128;
        textureHeight = 64;

        mainBody = new ModelRenderer(this, 0, 9);
        mainBody.addBox(-5.0F, 0.0F, -3.0F, 10, 9, 5);
        mainBody.setRotationPoint(0.0F, 0.0F, 0.0F);

        final ModelRenderer leftStrap = new ModelRenderer(this, 21, 24);
        leftStrap.setRotationPoint(3.0F, 0.0F, -3.0F);
        leftStrap.addBox(0.0F, 0.0F, -1.0F, 1, 8, 1);
        mainBody.addChild(leftStrap);

        final ModelRenderer rightStrap = new ModelRenderer(this, 26, 24);
        rightStrap.setRotationPoint(-4.0F, 0.0F, -3.0F);
        rightStrap.addBox(0.0F, 0.0F, -1.0F, 1, 8, 1);
        mainBody.addChild(rightStrap);

        final ModelRenderer top = new ModelRenderer(this, 0, 0);
        top.setRotationPoint(0.0F, 0.0F, -3.0F);
        top.addBox(-5.0F, -3.0F, 0.0F, 10, 3, 5);
        mainBody.addChild(top);

        final ModelRenderer bottom = new ModelRenderer(this, 0, 34);
        bottom.setRotationPoint(-5.0F, 9.0F, -3.0F);
        bottom.addBox(0.0F, 0.0F, 0.0F, 10, 1, 4);
        mainBody.addChild(bottom);

        final ModelRenderer pocketFace = new ModelRenderer(this, 0, 24);
        pocketFace.setRotationPoint(0.0F, 6.9F, 2.0F);
        pocketFace.addBox(-4.0F, -6.0F, 0.0F, 8, 6, 2);
        mainBody.addChild(pocketFace);

        bipedBody.addChild(mainBody);

        // Offset like AdventureBackpack so the bag sits on the back.
        final float offsetZ = 0.4F;
        final float offsetY = 0.2F;
        for (Object o : bipedBody.childModels) {
            final ModelRenderer part = (ModelRenderer) o;
            part.offsetX += 0;
            part.offsetY += offsetY;
            part.offsetZ += offsetZ;
        }
    }

    /** Copies the body pose from the player's main model (called before render). */
    public void copyBodyRotation(final ModelBiped reference) {
        bipedBody.rotateAngleX = reference.bipedBody.rotateAngleX;
        bipedBody.rotateAngleY = reference.bipedBody.rotateAngleY;
        bipedBody.rotateAngleZ = reference.bipedBody.rotateAngleZ;
    }

    /**
     * Renders only the backpack, transformed by the body rotation, so it follows the
     * player when turning / sneaking (ModelBiped.render would reset the angles).
     */
    @Override
    public void render(final Entity entity, final float f, final float f1, final float f2, final float f3,
        final float f4, final float f5) {
        GL11.glPushMatrix();
        GL11.glTranslatef(bipedBody.rotationPointX * f5, bipedBody.rotationPointY * f5, bipedBody.rotationPointZ * f5);
        if (bipedBody.rotateAngleZ != 0.0F) {
            GL11.glRotatef(bipedBody.rotateAngleZ * (180F / (float) Math.PI), 0.0F, 0.0F, 1.0F);
        }
        if (bipedBody.rotateAngleY != 0.0F) {
            GL11.glRotatef(bipedBody.rotateAngleY * (180F / (float) Math.PI), 0.0F, 1.0F, 0.0F);
        }
        if (bipedBody.rotateAngleX != 0.0F) {
            GL11.glRotatef(bipedBody.rotateAngleX * (180F / (float) Math.PI), 1.0F, 0.0F, 0.0F);
        }
        GL11.glTranslatef(-(bipedBody.offsetX) * f5, -(bipedBody.offsetY) * f5, -(bipedBody.offsetZ) * f5);
        mainBody.render(f5);
        GL11.glPopMatrix();
    }
}
