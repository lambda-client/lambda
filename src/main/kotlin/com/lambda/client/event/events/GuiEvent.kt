package com.lambda.client.event.events

import com.lambda.client.event.Event
import net.minecraft.client.gui.GuiScreen

abstract class GuiEvent(var screen: GuiScreen?) : Event {
    class Displayed(screen: GuiScreen?) : GuiEvent(screen)
    class Closed(screen: GuiScreen?) : GuiEvent(screen)
}