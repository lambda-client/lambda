package com.lambda.mixin.accessor.gui;

import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = GuiScreen.class)
public interface AccessorGuiScreen {

    @Accessor("eventButton")
    void setEventButton(final int eventButton);
}
