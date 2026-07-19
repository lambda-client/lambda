
package com.minato.mixin.input;

import com.minato.event.EventFlow;
import com.minato.event.events.ButtonEvent;
import com.minato.module.modules.render.Zoom;
import com.minato.util.math.Vec2d;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow private double x;

    @Shadow private double y;

    @WrapMethod(method = "onMouseButton")
    private void onMouseButton(long window, MouseInput input, int action, Operation<Void> original) {
        if (!EventFlow.post(new ButtonEvent.Mouse.Click(input.button(), action, input.modifiers())).isCanceled())
            original.call(window, input, action);
    }

    @WrapMethod(method = "onMouseScroll(JDD)V")
    private void onMouseScroll(long window, double horizontal, double vertical, Operation<Void> original) {
        Vec2d delta = new Vec2d(horizontal, vertical);

        if (!EventFlow.post(new ButtonEvent.Mouse.Scroll(delta)).isCanceled())
            original.call(window, horizontal, vertical);
    }

    @WrapMethod(method = "onCursorPos(JDD)V")
    private void onCursorPos(long window, double x, double y, Operation<Void> original) {
        if (x + y == this.x + this.y) return;

        Vec2d position = new Vec2d(x, y);

        if (!EventFlow.post(new ButtonEvent.Mouse.Move(position)).isCanceled())
            original.call(window, x, y);
    }

    @ModifyExpressionValue(method = "updateMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;smoothCameraEnabled:Z", opcode = Opcodes.GETFIELD))
    private boolean modifySmoothCameraEnabled(boolean original) {
        if (Zoom.INSTANCE.isEnabled() && Zoom.getSmoothMovement()) return true;
        else return original;
    }

    @ModifyExpressionValue(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0))
    private Object modifyGetValue(Object original) {
        if (Zoom.INSTANCE.isEnabled()) return ((Double) original) / Zoom.getTargetZoom();
        else return original;
    }
}
