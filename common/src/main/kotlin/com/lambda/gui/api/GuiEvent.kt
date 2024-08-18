package com.lambda.gui.api

import com.lambda.event.Event
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d
import net.minecraft.world.gen.feature.DeltaFeature

abstract class GuiEvent : Event {
    class Show : GuiEvent()
    class Hide : GuiEvent()
    class Tick : GuiEvent()
    class Render : GuiEvent()
    class KeyPress(val key: KeyCode) : GuiEvent()
    class CharTyped(val char: Char) : GuiEvent()
    class MouseClick(val button: Mouse.Button, val action: Mouse.Action, val mouse: Vec2d) : GuiEvent()
    class MouseMove(val mouse: Vec2d) : GuiEvent()
    class MouseScroll(val mouse: Vec2d, val delta: Double) : GuiEvent()
}