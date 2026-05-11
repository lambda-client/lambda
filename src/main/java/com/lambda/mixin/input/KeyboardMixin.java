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

package com.lambda.mixin.input;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ButtonEvent;
import com.lambda.module.modules.player.InventoryMove;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @WrapMethod(method = "onKey")
    private void onKey(long window, int action, KeyInput input, Operation<Void> original) {
        EventFlow.post(new ButtonEvent.Keyboard.Press(input.key(), input.scancode(), action, input.modifiers()));
        original.call(window, action, input);
        int key = input.key();
        if (!InventoryMove.getShouldMove() || !InventoryMove.isKeyMovementRelated(key)) return;
        InputUtil.Key fromCode = InputUtil.fromKeyCode(input);
        KeyBinding.setKeyPressed(fromCode, action != 0);
    }

    @WrapMethod(method = "onChar")
    private void onChar(long window, CharInput input, Operation<Void> original) {
        char[] chars = Character.toChars(input.codepoint());

        for (char c : chars)
            EventFlow.post(new ButtonEvent.Keyboard.Char(c));

        original.call(window, input);
    }
}
