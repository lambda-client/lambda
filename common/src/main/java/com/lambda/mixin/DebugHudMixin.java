package com.lambda.mixin;

import com.lambda.Lambda;
import net.minecraft.client.gui.hud.DebugHud;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugHud.class)
public class DebugHudMixin {
    @Inject(method = "getRightText", at = @At(value = "TAIL"))
    private void onGetRightText(CallbackInfoReturnable<List<String>> cir) {

        if (Lambda.getMc().crosshairTarget == null) return;
        HitResult hitResult = Lambda.getMc().crosshairTarget;
        List<String> list = cir.getReturnValue();

        list.add("");
        list.add(Formatting.UNDERLINE + "Lambda");
        list.add("Hitpos: " + hitResult.getPos());
        list.add("Type: " + hitResult.getType());

        if (hitResult instanceof BlockHitResult blockHitResult) {
            list.add("Side: " + blockHitResult.getSide());
            list.add("Blockpos: " + blockHitResult.getBlockPos());
        }

        if (hitResult instanceof EntityHitResult entityHitResult) {
            list.add("Entity: " + entityHitResult.getEntity().getName().getString());
        }
    }
}
