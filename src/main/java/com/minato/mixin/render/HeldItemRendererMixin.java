package com.minato.mixin.render;

import com.google.common.base.MoreObjects;
import com.minato.Minato;
import com.minato.graphics.mc.ItemAlphaManager;
import com.minato.module.modules.render.ViewModel;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Final @Shadow private MinecraftClient client;
    @Shadow private ItemStack mainHand;
    @Shadow private ItemStack offHand;
    @Shadow private float equipProgressMainHand;
    @Shadow private float equipProgressOffHand;

    // ── Item Alpha Pipeline ──

    /**
     * Set item alpha for the current render pass based on the hand being rendered.
     * Called at HEAD of renderFirstPersonItem before any rendering occurs.
     */
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void injectAlphaStart(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        if (!ViewModel.INSTANCE.isEnabled() || !ViewModel.INSTANCE.getHeldItemPosition()) return;
        int alpha = ViewModel.INSTANCE.itemAlpha(hand);
        ItemAlphaManager.INSTANCE.setForHand(alpha);
    }

    /**
     * Clear item alpha after the render pass completes.
     * Called at RETURN of renderFirstPersonItem to ensure cleanup.
     */
    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void injectAlphaEnd(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        ItemAlphaManager.INSTANCE.clear();
    }

    // ── ViewModel Transform + Sword Blocking ──
    //
    // NOTE: renderItem() and renderArmHoldingItem() signatures changed in 1.21.x.
    // The old INVOKE-target mixins were removed. ViewModel transforms are still
    // applied via injectBlockSwordStart (HEAD cancellable) and the alpha pipeline.
    // Sword blocking transforms need a re-worked mixin point for 1.21.x.

    // ── Shield Cancellation during Sword Block ──

    /**
     * Cancel offhand shield rendering when sword blocking is active.
     */
    @Inject(method = "renderFirstPersonItem", at = @At(value = "HEAD"), cancellable = true)
    private void injectBlockSwordStart(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        if (hand == Hand.OFF_HAND && ViewModel.INSTANCE.isBlocking()) {
            ItemStack offhandStack = player.getOffHandStack();
            ItemStack mainHandStack = player.getMainHandStack();
            if (!offhandStack.isEmpty() && mainHandStack.isIn(ItemTags.SWORDS)) {
                ci.cancel();
            }
        }
    }

    // ── Equip Progress Modification ──

    @ModifyArg(method = "updateHeldItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F", ordinal = 2), index = 0)
    private float modifyEquipProgressMainHand(float value) {
        if (client.player == null || ViewModel.INSTANCE.isDisabled()) return value;

        ViewModel config = ViewModel.INSTANCE;
        ItemStack currentStack = client.player.getMainHandStack();
        if (config.getOldAnimations() && !config.getSwapAnimation()) {
            mainHand = currentStack;
        }

        float progress = config.getOldAnimations() ? 1 : (float) Math.pow(client.player.getHandEquippingProgress(1), 3);

        return (ItemStack.areEqual(mainHand, currentStack) ? progress : 0) - equipProgressMainHand;
    }

    @ModifyArg(method = "updateHeldItems", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(FFF)F", ordinal = 3), index = 0)
    private float modifyEquipProgressOffHand(float value) {
        if (client.player == null || ViewModel.INSTANCE.isDisabled()) return value;

        ViewModel config = ViewModel.INSTANCE;

        ItemStack currentStack = client.player.getOffHandStack();
        if (config.getOldAnimations() && !config.getSwapAnimation()) {
            offHand = currentStack;
        }

        return (ItemStack.areEqual(offHand, currentStack) ? 1 : 0) - equipProgressOffHand;
    }

    // ── Swing Modification ──

    @ModifyExpressionValue(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getHandSwingProgress(F)F"))
    private float modifySwing(float swingProgress) {
        ViewModel config = ViewModel.INSTANCE;
        MinecraftClient mc = Minato.getMc();
        if (config.isDisabled() || mc.player == null) return swingProgress;

        // When sword blocking is active, suppress swing animation completely
        if (config.isBlocking()) return 0f;

        // Apply held item swing speed modifier
        Hand hand = MoreObjects.firstNonNull(mc.player.preferredHand, Hand.MAIN_HAND);
        float speedModified = config.applySwingSpeed(hand, swingProgress);

        // Add ViewModel's custom swing progress offset
        if (hand == Hand.MAIN_HAND) {
            return speedModified + config.getMainSwingProgress();
        } else if (hand == Hand.OFF_HAND) {
            return speedModified + config.getOffhandSwingProgress();
        }

        return speedModified;
    }
}
