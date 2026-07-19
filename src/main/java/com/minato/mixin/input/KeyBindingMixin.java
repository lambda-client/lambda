
package com.minato.mixin.input;

import com.minato.module.modules.movement.Speed;
import com.minato.module.modules.movement.Sprint;
import com.minato.module.modules.movement.TargetStrafe;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Objects;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
    @ModifyReturnValue(method = "isPressed", at = @At("RETURN"))
    boolean modifyIsPressed(boolean original) {
        KeyBinding instance = (KeyBinding) (Object) this;
        if (!Objects.equals(instance.getId(), "key.sprint")) return original;

        if (Sprint.INSTANCE.isEnabled()) return true;
        if (Speed.INSTANCE.isEnabled() && Speed.getMode() == Speed.Mode.GrimStrafe) return true;
        if (TargetStrafe.INSTANCE.isEnabled() && TargetStrafe.isActive()) return true;
        return original;
    }
}
