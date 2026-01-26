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

package com.lambda.mixin.client.sound;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ClientEvent;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    @WrapMethod(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;")
    public SoundSystem.PlayResult onPlay(SoundInstance sound, Operation<SoundSystem.PlayResult> original) {
        if (EventFlow.post(new ClientEvent.Sound(sound)).isCanceled())
            return SoundSystem.PlayResult.NOT_STARTED;

        return original.call(sound);
    }
}
