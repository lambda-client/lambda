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

package com.lambda.mixin;

import com.lambda.core.TimerManager;
import com.lambda.event.EventFlow;
import com.lambda.event.events.ClientEvent;
import com.lambda.event.events.InventoryEvent;
import com.lambda.event.events.TickEvent;
import com.lambda.gui.DearImGui;
import com.lambda.gui.components.ClickGuiLayout;
import com.lambda.module.modules.movement.BetterFirework;
import com.lambda.module.modules.player.Interact;
import com.lambda.module.modules.player.InventoryMove;
import com.lambda.module.modules.player.PacketMine;
import com.lambda.util.WindowUtils;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.thread.ThreadExecutor;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftClient.class, priority = Integer.MAX_VALUE)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public Screen currentScreen;

    @Shadow
    @Nullable
    public HitResult crosshairTarget;

    @Shadow
    public int itemUseCooldown;

    @Inject(method = "close", at = @At("HEAD"))
    void closeImGui(CallbackInfo ci) {
        DearImGui.INSTANCE.destroy();
    }

    @WrapMethod(method = "render")
    void onLoopTick(boolean tick, Operation<Void> original) {
        com.lambda.graphics.RenderMain.preRender();
        EventFlow.post(TickEvent.Render.Pre.INSTANCE);
        original.call(tick);
        EventFlow.post(TickEvent.Render.Post.INSTANCE);
    }

    @WrapMethod(method = "tick")
    void onTick(Operation<Void> original) {
        EventFlow.post(TickEvent.Pre.INSTANCE);
        original.call();
        EventFlow.post(TickEvent.Post.INSTANCE);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;tick()V"))
    void onNetwork(ClientPlayerInteractionManager instance, Operation<Void> original) {
        EventFlow.post(TickEvent.Network.Pre.INSTANCE);
        original.call(instance);
        EventFlow.post(TickEvent.Network.Post.INSTANCE);
    }

    @Definition(id = "overlay", field = "Lnet/minecraft/client/MinecraftClient;overlay:Lnet/minecraft/client/gui/screen/Overlay;")
    @Expression("this.overlay == null")
    @ModifyExpressionValue(method = "tick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean modifyCurrentScreenNullCheck(boolean original) {
        if (!original || this.currentScreen != null) {
            EventFlow.post(TickEvent.Input.Pre.INSTANCE);
            EventFlow.post(TickEvent.Input.Post.INSTANCE);
        }
        return original;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleInputEvents()V"))
    void onInput(MinecraftClient instance, Operation<Void> original) {
        EventFlow.post(TickEvent.Input.Pre.INSTANCE);
        original.call(instance);
        EventFlow.post(TickEvent.Input.Post.INSTANCE);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sound/SoundManager;tick(Z)V"))
    void onSound(SoundManager instance, boolean paused, Operation<Void> original) {
        EventFlow.post(TickEvent.Sound.Pre.INSTANCE);
        original.call(instance, paused);
        EventFlow.post(TickEvent.Sound.Post.INSTANCE);
    }

    @Inject(at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V", shift = At.Shift.AFTER, remap = false), method = "stop")
    private void onShutdown(CallbackInfo ci) {
        com.lambda.graphics.outline.OutlineRenderer.INSTANCE.cleanup();
        EventFlow.post(new ClientEvent.Shutdown());
    }

    /**
     * Inject after the thread field is set so that {@link ThreadExecutor#getThread}
     * is available
     */
    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;thread:Ljava/lang/Thread;", shift = At.Shift.AFTER, ordinal = 0, opcode = Opcodes.PUTFIELD), method = "run")
    private void onStartup(CallbackInfo ci) {
        EventFlow.post(new ClientEvent.Startup());
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onScreenOpen(@Nullable Screen screen, CallbackInfo ci) {
        if (screen == null)
            return;
        if (screen instanceof ScreenHandlerProvider<?> handledScreen) {
            EventFlow.post(new InventoryEvent.Open(handledScreen.getScreenHandler()));
        }
    }

    @Inject(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;removed()V", shift = At.Shift.AFTER))
    private void onScreenRemove(@Nullable Screen screen, CallbackInfo ci) {
        if (currentScreen == null)
            return;
        if (currentScreen instanceof ScreenHandlerProvider<?> handledScreen) {
            EventFlow.post(new InventoryEvent.Close(handledScreen.getScreenHandler()));
        }
    }

    @WrapOperation(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;unpressAll()V"))
    private void redirectUnPressAll(Operation<Void> original) {
        if (!InventoryMove.getShouldMove()) {
            original.call();
            return;
        }
        KeyBinding.KEYS_BY_ID.values().forEach(bind -> {
            if (!InventoryMove.isKeyMovementRelated(bind.boundKey.getCode())) {
                bind.reset();
            }
        });
    }

    @WrapWithCondition(method = "doAttack()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V"))
    private boolean redirectHandSwing(ClientPlayerEntity instance, Hand hand) {
        if (this.crosshairTarget == null)
            return false;
        return this.crosshairTarget.getType() != HitResult.Type.BLOCK || PacketMine.INSTANCE.isDisabled();
    }

    @ModifyExpressionValue(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;isBreakingBlock()Z"))
    boolean redirectMultiActon(boolean original) {
        if (Interact.INSTANCE.isEnabled() && Interact.getMultiAction())
            return false;
        return original;
    }

    @Inject(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    void injectFastPlace(CallbackInfo ci) {
        if (!Interact.INSTANCE.isEnabled())
            return;

        itemUseCooldown = Interact.getPlaceDelay();
    }

    @WrapMethod(method = "doItemUse")
    void injectItemUse(Operation<Void> original) {
        if (BetterFirework.INSTANCE.isDisabled() || !BetterFirework.onInteract())
            original.call();
    }

    @WrapMethod(method = "doItemPick")
    void injectItemPick(Operation<Void> original) {
        if (BetterFirework.INSTANCE.isDisabled() || !BetterFirework.onPick())
            original.call();
    }

    @WrapMethod(method = "getTargetMillisPerTick")
    float getTargetMillisPerTick(float millis, Operation<Float> original) {
        var length = TimerManager.INSTANCE.getLength();

        if (length == TimerManager.DEFAULT_LENGTH)
            return original.call(millis);
        else
            return (float) TimerManager.INSTANCE.getLength();
    }

    @Inject(method = "updateWindowTitle", at = @At("HEAD"), cancellable = true)
    void updateWindowTitle(CallbackInfo ci) {
        if (!ClickGuiLayout.getSetLambdaWindowTitle())
            return;
        WindowUtils.setLambdaTitle();
        ci.cancel();
    }
}
