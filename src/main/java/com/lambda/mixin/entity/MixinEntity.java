package com.lambda.mixin.entity;

import com.lambda.client.module.modules.movement.Step;
import com.lambda.client.module.modules.movement.Velocity;
import com.lambda.client.module.modules.player.Freecam;
import com.lambda.client.module.modules.player.ViewLock;
import com.lambda.client.module.modules.render.FreeLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.util.math.AxisAlignedBB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = Integer.MAX_VALUE)
public abstract class MixinEntity {

    @Shadow private int entityId;

    @Shadow private AxisAlignedBB boundingBox;
    float storedStepHeight = -1;

    @Inject(method = "applyEntityCollision", at = @At("HEAD"), cancellable = true)
    public void applyEntityCollisionHead(Entity entityIn, CallbackInfo ci) {
        Velocity.handleApplyEntityCollision((Entity) (Object) this, entityIn, ci);
    }

    // Makes the camera guy instead of original player turn around when we move mouse
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    public void turn(float yaw, float pitch, CallbackInfo ci) {
        Entity casted = (Entity) (Object) this;

        if (FreeLook.handleTurn(casted, yaw, pitch, ci)) return;
        if (Freecam.handleTurn(casted, yaw, pitch, ci)) return;
        ViewLock.handleTurn(casted, yaw, pitch, ci);
    }

    // these mixins are for step module before and after the step calculations are performed
    @Inject(method = "move", at = @At(value = "FIELD", target = "net/minecraft/entity/Entity.stepHeight:F", ordinal = 3, shift = At.Shift.BEFORE))
    private void preStep(MoverType type, double x, double y, double z, CallbackInfo ci) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;

        if (player == null) return;

        if (entityId == player.getEntityId()
            && Step.INSTANCE.isEnabled()
            && !Step.INSTANCE.pre(boundingBox, player)
        ) {
            storedStepHeight = player.stepHeight;
            player.stepHeight = Step.INSTANCE.getStrict() ? 1.015f : Step.INSTANCE.getUpStep().getValue();
        }
    }

    @Inject(method = "move", at = @At(value = "INVOKE", target = "net/minecraft/entity/Entity.resetPositionToBB ()V", ordinal = 1, shift = At.Shift.BEFORE))
    private void postStep(MoverType type, double x, double y, double z, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;

        if (player == null || !Step.INSTANCE.isEnabled()) return;

        if (entityId == player.getEntityId()) {
            Step.INSTANCE.post(boundingBox, mc);
        }
    }

}
