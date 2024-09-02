package com.lambda.mixin.render;

import com.lambda.module.modules.render.WorldColors;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;getX()D"))
    private static double redirectRed(Vec3d baseColor) {
        return WorldColors.backgroundColor(baseColor).getX();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;getY()D"))
    private static double redirectGreen(Vec3d baseColor) {
        return WorldColors.backgroundColor(baseColor).getY();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;getZ()D"))
    private static double redirectBlue(Vec3d baseColor) {
        return WorldColors.backgroundColor(baseColor).getZ();
    }
}
