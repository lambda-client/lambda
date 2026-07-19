
package com.minato.mixin.render;

import com.minato.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.fog.StatusEffectFogModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(StatusEffectFogModifier.class)
public abstract class StatusEffectFogModifierMixin {
    @Shadow
    public abstract RegistryEntry<StatusEffect> getStatusEffect();

    @ModifyReturnValue(method = "shouldApply", at = @At("RETURN"))
    boolean modifyShouldApply(boolean original) {
        if (NoRender.INSTANCE.isDisabled()) return original;

        if ((NoRender.getNoBlindness() && getStatusEffect() == StatusEffects.BLINDNESS) ||
                (NoRender.getNoDarkness() && getStatusEffect() == StatusEffects.DARKNESS))
            return false;

        return original;
    }
}
