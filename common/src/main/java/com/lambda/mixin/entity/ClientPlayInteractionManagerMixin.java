package com.lambda.mixin.entity;

import com.lambda.event.EventFlow;
import com.lambda.event.events.AttackEvent;
import com.lambda.event.events.InteractionEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    public void clickSlotHead(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (syncId != player.currentScreenHandler.syncId) return;
        var click = new InteractionEvent.SlotClick(syncId, slotId, button, actionType, player.currentScreenHandler);
        if (EventFlow.post(click).isCanceled()) ci.cancel();
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    void onAttackPre(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (EventFlow.post(new AttackEvent.Pre(target)).isCanceled()) ci.cancel();
    }

    @Inject(method = "attackEntity", at = @At("TAIL"))
    void onAttackPost(PlayerEntity player, Entity target, CallbackInfo ci) {
        EventFlow.post(new AttackEvent.Post(target));
    }
}
