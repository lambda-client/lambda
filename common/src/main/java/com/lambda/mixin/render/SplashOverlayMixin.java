package com.lambda.mixin.render;

import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

@Mixin(SplashOverlay.class)
public class SplashOverlayMixin {
    @Mutable
    @Shadow @Final
    static Identifier LOGO;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int redirectBrandArgb(IntSupplier originalSupplier) {
        return ColorHelper.Argb.getArgb(255, 35, 35, 35);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        LOGO = Identifier.of("lambda", "textures/lambda_banner.png");
    }
}
