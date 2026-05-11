/*
 * Copyright 2026 Lambda
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

import com.google.common.base.MoreObjects;
import com.lambda.Lambda;
import com.lambda.module.modules.render.ViewModel;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
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

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V"))
    private void injectRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        if (ViewModel.INSTANCE.isEnabled()) ViewModel.INSTANCE.transform(item, hand, matrices);
    }

    @Inject(method = "renderFirstPersonItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderArmHoldingItem(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;IFFLnet/minecraft/util/Arm;)V"))
    private void injectRenderArmHoldingItem(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue orderedRenderCommandQueue, int light, CallbackInfo ci) {
        if (ViewModel.INSTANCE.isEnabled()) ViewModel.INSTANCE.transform(item, hand, matrices);
    }

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

    @ModifyExpressionValue(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getHandSwingProgress(F)F"))
    private float modifySwing(float swingProgress) {
        ViewModel config = ViewModel.INSTANCE;
        MinecraftClient mc = Lambda.getMc();
        if (config.isDisabled() || mc.player == null) return swingProgress;
        Hand hand = MoreObjects.firstNonNull(mc.player.preferredHand, Hand.MAIN_HAND);

        if (hand == Hand.MAIN_HAND) {
            return swingProgress + config.getMainSwingProgress();
        } else if (hand == Hand.OFF_HAND) {
            return swingProgress + config.getOffhandSwingProgress();
        }

        return swingProgress;
    }
}
