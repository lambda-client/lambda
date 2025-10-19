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

package com.lambda.mixin.input;

import com.lambda.event.EventFlow;
import com.lambda.event.events.KeyboardEvent;
import com.lambda.module.modules.player.InventoryMove;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Keyboard;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @WrapMethod(method = "onKey")
    private void onKey(long window, int key, int scancode, int action, int modifiers, Operation<Void> original) {
        EventFlow.post(new KeyboardEvent.Press(key, scancode, action, modifiers));
        original.call(window, key, scancode, action, modifiers);
    }

    @Inject(method = "onKey", at = @At("RETURN"))
    private void onKeyTail(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (!InventoryMove.getShouldMove() || !InventoryMove.isKeyMovementRelated(key)) return;
        InputUtil.Key fromCode = InputUtil.fromKeyCode(key, scancode);
        KeyBinding.setKeyPressed(fromCode, action != 0);
    }

    @WrapMethod(method = "onChar")
    private void onChar(long window, int codePoint, int modifiers, Operation<Void> original) {
        char[] chars = Character.toChars(codePoint);

        for (char c : chars)
            EventFlow.post(new KeyboardEvent.Char(c));

        original.call(window, codePoint, modifiers);
    }
}
