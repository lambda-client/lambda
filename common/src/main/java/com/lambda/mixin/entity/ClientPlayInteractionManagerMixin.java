package com.lambda.mixin.entity;

import com.lambda.event.EventFlow;
import com.lambda.event.events.InteractionEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayInteractionManagerMixin {

    @Final
    @Shadow
    private MinecraftClient client;

    @Inject(method = "interactBlock", at = @At("HEAD"))
    public void interactBlockHead(final ClientPlayerEntity player, final Hand hand, final BlockHitResult hitResult, final CallbackInfoReturnable<ActionResult> cir) {
        if (client.world == null) return;
        EventFlow.post(new InteractionEvent.Block(client.world, hitResult));
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    public void onAttackBlock(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (EventFlow.post(new InteractionEvent.AttackBlock(pos, side)).isCanceled()) cir.cancel();
    }
}
