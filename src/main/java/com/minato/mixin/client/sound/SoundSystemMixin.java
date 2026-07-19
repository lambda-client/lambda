
package com.minato.mixin.client.sound;

import com.minato.event.EventFlow;
import com.minato.event.events.ClientEvent;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;", at = @At("HEAD"), cancellable = true)
    public void onPlay(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
        if (EventFlow.post(new ClientEvent.Sound(sound)).isCanceled()) {
            cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
        }
    }
}
