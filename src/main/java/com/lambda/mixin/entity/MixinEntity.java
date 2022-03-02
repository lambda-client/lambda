package com.lambda.mixin.entity;

import com.lambda.client.event.LambdaEventBus;
import com.lambda.client.event.events.SafewalkEvent;
import com.lambda.client.module.modules.movement.Velocity;
import com.lambda.client.module.modules.player.Freecam;
import com.lambda.client.module.modules.player.ViewLock;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Entity.class, priority = Integer.MAX_VALUE)
public abstract class MixinEntity {

    @Shadow private int entityId;

    @Inject(method = "applyEntityCollision", at = @At("HEAD"), cancellable = true)
    public void applyEntityCollisionHead(Entity entityIn, CallbackInfo ci) {
        Velocity.handleApplyEntityCollision((Entity) (Object) this, entityIn, ci);
    }

    @Redirect(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSneaking()Z"))
    public boolean move_isSneaking(Entity instance) {

        if (instance != Minecraft.getMinecraft().player)
            return instance.isSneaking();

        // allows one to set SafeWalking to true or false
        SafewalkEvent event = new SafewalkEvent(Minecraft.getMinecraft().player.isSneaking());
        LambdaEventBus.INSTANCE.post(event);

        return event.getSneak();

    }

    // Makes the camera guy instead of original player turn around when we move mouse
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    public void turn(float yaw, float pitch, CallbackInfo ci) {
        Entity casted = (Entity) (Object) this;

        if (Freecam.handleTurn(casted, yaw, pitch, ci)) return;
        ViewLock.handleTurn(casted, yaw, pitch, ci);
    }
}
