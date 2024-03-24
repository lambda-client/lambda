package com.lambda.mixin;

import com.lambda.interaction.RotationManager;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "updateTargetedEntity", at = @At("HEAD"))
    private void onUpdateTargetedEntity(CallbackInfo ci) {
        RotationManager.updateInterpolated();
    }
}
