package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.module.modules.movement.ElytraFlight;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static me.zeroeightsix.kami.KamiMod.MODULE_MANAGER;

@Mixin(ModelPlayer.class)
public class MixinModelPlayer extends ModelBiped {

    @Redirect(method = "setRotationAngles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelBiped;setRotationAngles(FFFFFFLnet/minecraft/entity/Entity;)V"))
    public void setRotationAngles(ModelBiped modelBiped, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scaleFactor, Entity entityIn) {
        if (MODULE_MANAGER.getModuleT(ElytraFlight.class).shouldSwing()) {
            this.bipedHead.rotateAngleY = netHeadYaw * 0.017453292F;

            this.bipedHead.rotateAngleX = -((float) Math.PI / 4F);

            this.bipedBody.rotateAngleY = 0.0F;
            this.bipedRightArm.rotationPointZ = 0.0F;
            this.bipedRightArm.rotationPointX = -5.0F;
            this.bipedLeftArm.rotationPointZ = 0.0F;
            this.bipedLeftArm.rotationPointX = 5.0F;

            this.bipedRightArm.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 2.0F * limbSwingAmount * 0.5F;
            this.bipedLeftArm.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F) * 2.0F * limbSwingAmount * 0.5F;
            this.bipedRightArm.rotateAngleZ = 0.0F;
            this.bipedLeftArm.rotateAngleZ = 0.0F;
            this.bipedRightLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
            this.bipedLeftLeg.rotateAngleX = MathHelper.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;
            this.bipedRightLeg.rotateAngleY = 0.0F;
            this.bipedLeftLeg.rotateAngleY = 0.0F;
            this.bipedRightLeg.rotateAngleZ = 0.0F;
            this.bipedLeftLeg.rotateAngleZ = 0.0F;

            this.bipedRightArm.rotateAngleY = 0.0F;
            this.bipedRightArm.rotateAngleZ = 0.0F;

            switch (this.leftArmPose) {
                case EMPTY:
                    this.bipedLeftArm.rotateAngleY = 0.0F;
                    break;
                case BLOCK:
                    this.bipedLeftArm.rotateAngleX = this.bipedLeftArm.rotateAngleX * 0.5F - 0.9424779F;
                    this.bipedLeftArm.rotateAngleY = 0.5235988F;
                    break;
                case ITEM:
                    this.bipedLeftArm.rotateAngleX = this.bipedLeftArm.rotateAngleX * 0.5F - ((float) Math.PI / 10F);
                    this.bipedLeftArm.rotateAngleY = 0.0F;
            }

            switch (this.rightArmPose) {
                case EMPTY:
                    this.bipedRightArm.rotateAngleY = 0.0F;
                    break;
                case BLOCK:
                    this.bipedRightArm.rotateAngleX = this.bipedRightArm.rotateAngleX * 0.5F - 0.9424779F;
                    this.bipedRightArm.rotateAngleY = -0.5235988F;
                    break;
                case ITEM:
                    this.bipedRightArm.rotateAngleX = this.bipedRightArm.rotateAngleX * 0.5F - ((float) Math.PI / 10F);
                    this.bipedRightArm.rotateAngleY = 0.0F;
            }

            if (this.swingProgress > 0.0F) {
                EnumHandSide enumhandside = this.getMainHand(entityIn);
                ModelRenderer modelrenderer = this.getArmForSide(enumhandside);
                float f1 = this.swingProgress;
                this.bipedBody.rotateAngleY = MathHelper.sin(MathHelper.sqrt(f1) * ((float) Math.PI * 2F)) * 0.2F;

                if (enumhandside == EnumHandSide.LEFT) {
                    this.bipedBody.rotateAngleY *= -1.0F;
                }

                this.bipedRightArm.rotationPointZ = MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
                this.bipedRightArm.rotationPointX = -MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;
                this.bipedLeftArm.rotationPointZ = -MathHelper.sin(this.bipedBody.rotateAngleY) * 5.0F;
                this.bipedLeftArm.rotationPointX = MathHelper.cos(this.bipedBody.rotateAngleY) * 5.0F;
                this.bipedRightArm.rotateAngleY += this.bipedBody.rotateAngleY;
                this.bipedLeftArm.rotateAngleY += this.bipedBody.rotateAngleY;
                this.bipedLeftArm.rotateAngleX += this.bipedBody.rotateAngleY;
                f1 = 1.0F - this.swingProgress;
                f1 = f1 * f1;
                f1 = f1 * f1;
                f1 = 1.0F - f1;
                float f2 = MathHelper.sin(f1 * (float) Math.PI);
                float f3 = MathHelper.sin(this.swingProgress * (float) Math.PI) * -(this.bipedHead.rotateAngleX - 0.7F) * 0.75F;
                modelrenderer.rotateAngleX = (float) ((double) modelrenderer.rotateAngleX - ((double) f2 * 1.2D + (double) f3));
                modelrenderer.rotateAngleY += this.bipedBody.rotateAngleY * 2.0F;
                modelrenderer.rotateAngleZ += MathHelper.sin(this.swingProgress * (float) Math.PI) * -0.4F;
            }

            this.bipedBody.rotateAngleX = 0.0F;
            this.bipedRightLeg.rotationPointZ = 0.1F;
            this.bipedLeftLeg.rotationPointZ = 0.1F;
            this.bipedRightLeg.rotationPointY = 12.0F;
            this.bipedLeftLeg.rotationPointY = 12.0F;
            this.bipedHead.rotationPointY = 0.0F;

            this.bipedRightArm.rotateAngleZ += MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
            this.bipedLeftArm.rotateAngleZ -= MathHelper.cos(ageInTicks * 0.09F) * 0.05F + 0.05F;
            this.bipedRightArm.rotateAngleX += MathHelper.sin(ageInTicks * 0.067F) * 0.05F;
            this.bipedLeftArm.rotateAngleX -= MathHelper.sin(ageInTicks * 0.067F) * 0.05F;

            if (this.rightArmPose == ModelBiped.ArmPose.BOW_AND_ARROW) {
                this.bipedRightArm.rotateAngleY = -0.1F + this.bipedHead.rotateAngleY;
                this.bipedLeftArm.rotateAngleY = 0.1F + this.bipedHead.rotateAngleY + 0.4F;
                this.bipedRightArm.rotateAngleX = -((float) Math.PI / 2F) + this.bipedHead.rotateAngleX;
                this.bipedLeftArm.rotateAngleX = -((float) Math.PI / 2F) + this.bipedHead.rotateAngleX;
            } else if (this.leftArmPose == ModelBiped.ArmPose.BOW_AND_ARROW) {
                this.bipedRightArm.rotateAngleY = -0.1F + this.bipedHead.rotateAngleY - 0.4F;
                this.bipedLeftArm.rotateAngleY = 0.1F + this.bipedHead.rotateAngleY;
                this.bipedRightArm.rotateAngleX = -((float) Math.PI / 2F) + this.bipedHead.rotateAngleX;
                this.bipedLeftArm.rotateAngleX = -((float) Math.PI / 2F) + this.bipedHead.rotateAngleX;
            }

            copyModelAngles(this.bipedHead, this.bipedHeadwear);
        } else {
            super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn);
        }
    }
}
