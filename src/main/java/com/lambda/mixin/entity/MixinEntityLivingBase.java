package com.lambda.mixin.entity;

import com.lambda.client.event.LambdaEventBus;
import com.lambda.client.event.Phase;
import com.lambda.client.event.events.ElytraTravelEvent;
import com.lambda.client.module.modules.movement.ElytraFlight;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase extends Entity {
    @Unique
    private Vec3d modifiedVec = null;

    public MixinEntityLivingBase(World worldIn) {
        super(worldIn);
    }

    @ModifyVariable(
        method = "travel(FFF)V",
        at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/entity/EntityLivingBase;getLookVec()Lnet/minecraft/util/math/Vec3d;", ordinal = 0)
    )
    private Vec3d vec3d(Vec3d original) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (!ElytraFlight.shouldModify() || !this.getClass().isInstance(player)) return original;

        float negPacketPitch = -ElytraFlight.INSTANCE.getPacketPitch();
        float negPacketYaw = -ElytraFlight.INSTANCE.getPacketYaw();

        float f0 = MathHelper.cos((float) (negPacketYaw * 0.017453292f - Math.PI));
        float f1 = MathHelper.sin((float) (negPacketYaw * 0.017453292f - Math.PI));
        float f2 = -MathHelper.cos(negPacketPitch * 0.017453292f);
        float f3 = MathHelper.sin(negPacketPitch * 0.017453292f);

        return new Vec3d(f1 * f2, f3, f0 * f2);
    }

    @ModifyVariable(
        method = "travel(FFF)V",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 3
    )
    private float f(float original) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (!ElytraFlight.shouldModify() || !this.getClass().isInstance(player)) return original;

        return ElytraFlight.INSTANCE.getPacketPitch() * 0.017453292f;
    }

    @Inject(
        method = "travel(FFF)V",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/entity/EntityLivingBase;motionZ:D", ordinal = 3),
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void getVec(
        float strafe,
        float vertical,
        float forward,
        CallbackInfo ci,
        // Local capture
        Vec3d vec3d
    ) {
        modifiedVec = vec3d;
    }

    @Redirect(
        method = "travel(FFF)V",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/entity/EntityLivingBase;motionX:D", ordinal = 7)
    )
    public double motionX(EntityLivingBase it) {
        if (!ElytraFlight.shouldModify()) return it.motionX;
        return it.motionX += modifiedVec.x * 0.1 + (modifiedVec.x * 1.5 - this.motionX) * 0.5;
    }

    @Redirect(
        method = "travel(FFF)V",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/entity/EntityLivingBase;motionY:D", ordinal = 7)
    )
    public double motionY(EntityLivingBase it) {
        if (!ElytraFlight.shouldModify()) return it.motionY;
        return it.motionY += modifiedVec.y * 0.1 + (modifiedVec.y * 1.5 - this.motionY) * 0.5;
    }

    @Redirect(
        method = "travel(FFF)V",
        at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, target = "Lnet/minecraft/entity/EntityLivingBase;motionZ:D", ordinal = 7)
    )
    public double motionZ(EntityLivingBase it) {
        if (!ElytraFlight.shouldModify()) return it.motionZ;
        return it.motionZ += modifiedVec.z * 0.1 + (modifiedVec.z * 1.5 - this.motionZ) * 0.5;
    }

    @Inject(method = "travel", at = @At("HEAD"))
    private void onTravelPre(float strafe, float vertical, float forward, CallbackInfo ci) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (!this.getClass().isInstance(player) || !player.isElytraFlying()) return;

        ElytraTravelEvent event = new ElytraTravelEvent(Phase.PRE);
        LambdaEventBus.INSTANCE.post(event);
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelPost(float strafe, float vertical, float forward, CallbackInfo ci) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (!this.getClass().isInstance(player) || !player.isElytraFlying()) return;

        ElytraTravelEvent event = new ElytraTravelEvent(Phase.POST);
        LambdaEventBus.INSTANCE.post(event);
    }
}
