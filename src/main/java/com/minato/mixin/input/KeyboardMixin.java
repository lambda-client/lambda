
package com.minato.mixin.input;

import com.minato.event.EventFlow;
import com.minato.event.events.ButtonEvent;
import com.minato.module.modules.player.InventoryMove;
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
