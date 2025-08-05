/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.mixin.render;

import com.lambda.interaction.request.rotating.RotationManager;
import com.lambda.module.modules.player.Freecam;
import com.lambda.module.modules.render.CameraTweaks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    public abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (!Freecam.INSTANCE.isEnabled()) return;

        Freecam.updateCam();
    }

    /**
     * Sets the lock rotation to the active rotation
     * <pre>{@code
     * this.setPos(
     *     MathHelper.lerp((double)tickDelta, focusedEntity.prevX, focusedEntity.getX()),
     *     MathHelper.lerp((double)tickDelta, focusedEntity.prevY, focusedEntity.getY()) + (double)MathHelper.lerp(tickDelta, this.lastCameraY, this.cameraY),
     *     MathHelper.lerp((double)tickDelta, focusedEntity.prevZ, focusedEntity.getZ())
     *     );
     * }</pre>
     */
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V", shift = At.Shift.AFTER))
    private void injectQuickPerspectiveSwap(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        var rot = RotationManager.getLockRotation();
        if (rot == null) return;
        setRotation(rot.getYawF(), rot.getPitchF());
    }

    /**
     * Allows camera to clip through blocks in third person
     */
    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void onClipToSpace(float desiredCameraDistance, CallbackInfoReturnable<Float> info) {
        if (CameraTweaks.INSTANCE.isEnabled() && CameraTweaks.getNoClipCam()) {
            info.setReturnValue(desiredCameraDistance);
        }
    }

    /**
     * Modifies the third person camera distance
     * <pre>{@code
     * if (thirdPerson) {
     *         if (inverseView) {
     *             this.setRotation(this.yaw + 180.0F, -this.pitch);
     *         }
     *
     *         this.moveBy(-this.clipToSpace(4.0), 0.0, 0.0);
     * }
     * }</pre>
     */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float onDistanceUpdate(float desiredCameraDistance) {
        if (CameraTweaks.INSTANCE.isEnabled()) {
            return CameraTweaks.getCamDistance();
        }

        return desiredCameraDistance;
    }
}
