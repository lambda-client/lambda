package com.lambda.mixin.gui;

import com.lambda.client.module.modules.player.Freecam;
import com.lambda.client.module.modules.render.HungerOverlay;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class MixinGuiIngameForge {

    @Shadow
    public abstract void renderFood(int width, int height);

    @ModifyVariable(method = "renderAir", at = @At(value = "STORE", ordinal = 0))
    private EntityPlayer renderAir$getRenderViewEntity(EntityPlayer renderViewEntity) {
        return Freecam.getRenderViewEntity(renderViewEntity);
    }

    @ModifyVariable(method = "renderHealth", at = @At(value = "STORE", ordinal = 0))
    private EntityPlayer renderHealth$getRenderViewEntity(EntityPlayer renderViewEntity) {
        return Freecam.getRenderViewEntity(renderViewEntity);
    }

    @ModifyVariable(method = "renderFood", at = @At(value = "STORE", ordinal = 0))
    private EntityPlayer renderFood$getRenderViewEntity(EntityPlayer renderViewEntity) {
        return Freecam.getRenderViewEntity(renderViewEntity);
    }

    @ModifyVariable(method = "renderHealthMount", at = @At(value = "STORE", ordinal = 0))
    private EntityPlayer renderHealthMount$getRenderViewEntity(EntityPlayer renderViewEntity) {
        return Freecam.getRenderViewEntity(renderViewEntity);
    }

    @Inject(method = "renderHealthMount", at = @At("HEAD"))
    private void renderHealthMount(int width, int height, CallbackInfo ci) {
        if (HungerOverlay.INSTANCE.getRenderFoodOnRideable()) {
            this.renderFood(width, height);
        }
    }
}
