package com.lambda.mixin.world;

import com.lambda.client.event.LambdaEventBus;
import com.lambda.client.event.events.WorldEvent;
import net.minecraft.world.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public class MixinExplosion {
    @Inject(method = "doExplosionA", at = @At(value = "INVOKE", target = "Lnet/minecraftforge/event/ForgeEventFactory;onExplosionDetonate(Lnet/minecraft/world/World;Lnet/minecraft/world/Explosion;Ljava/util/List;D)V"))
    void onDoExplosionAForgeEventFactory(CallbackInfo ci) {
        WorldEvent.PostExplosion event = new WorldEvent.PostExplosion((Explosion) (Object) this);
        LambdaEventBus.INSTANCE.post(event);
    }
}
