package com.lambda.mixin.render;

import com.lambda.module.modules.player.Freecam;
import com.lambda.module.modules.render.WorldColors;
import com.lambda.util.math.ColorUtils;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
    private boolean renderIsThirdPerson(Camera camera) {
        return Freecam.INSTANCE.isEnabled() || camera.isThirdPerson();
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V"), index = 3)
    private boolean renderSetupTerrainModifyArg(boolean spectator) {
        return Freecam.INSTANCE.isEnabled() || spectator;
    }

    @Redirect(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getSkyColor(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d redirectSkyColor(ClientWorld world, Vec3d cameraPos, float tickDelta){
        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomSky()) {
           return ColorUtils.getVec3d(WorldColors.getSkyColor());
        }
        return world.getSkyColor(cameraPos, tickDelta);
    }

    @Redirect(method = "renderClouds(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FDDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/world/ClientWorld;getCloudsColor(F)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d redirectCloudColor(ClientWorld world, float tickDelta){
        if (WorldColors.INSTANCE.isEnabled() && WorldColors.getCustomClouds()) {
            return ColorUtils.getVec3d(WorldColors.getCloudsColor());
        }
        return world.getCloudsColor(tickDelta);
    }
}
