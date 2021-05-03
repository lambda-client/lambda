package com.lambda.client.mixin.client.render;

import com.lambda.client.module.modules.render.ArmorHide;
import com.lambda.client.module.modules.render.EnchantColor;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerArmorBase.class)
public abstract class MixinLayerArmorBase {
    @Shadow @Final protected static ResourceLocation ENCHANTED_ITEM_GLINT_RES;

    @Inject(method = "renderArmorLayer", at = @At("HEAD"), cancellable = true)
    public void renderArmorLayerPre(EntityLivingBase entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale, EntityEquipmentSlot slotIn, CallbackInfo ci) {
        if (ArmorHide.INSTANCE.isEnabled() && ArmorHide.shouldHide(slotIn, entityLivingBaseIn)) {
            ci.cancel();
        }
    }

    @Redirect(method = { "renderEnchantedGlint" }, at = @At(value = "INVOKE", target = "net/minecraft/client/renderer/GlStateManager.color(FFFF)V"))
    private static void renderEnchantedGlint(float a2, float a3, float a4, float v1) {
        if (EnchantColor.INSTANCE.isEnabled()) {
            if (EnchantColor.INSTANCE.getRainbow()) {
                a2 = EnchantColor.INSTANCE.getRainbowR() / 255f;
                a4 = EnchantColor.INSTANCE.getRainbowG() / 255f;
                a3 = EnchantColor.INSTANCE.getRainbowB() / 255f;
                v1 = EnchantColor.INSTANCE.getRainbowA() / 255f;
            } else {
                a2 = EnchantColor.INSTANCE.getColorSetting().getR() / 255f;
                a4 = EnchantColor.INSTANCE.getColorSetting().getG() / 255f;
                a3 = EnchantColor.INSTANCE.getColorSetting().getB() / 255f;
                v1 = EnchantColor.INSTANCE.getColorSetting().getA() / 255f;
            }
        }
        GlStateManager.color(a2, a3, a4, v1);
    }
}