package com.lambda.client.gui.rgui

import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.setting.GuiConfig
import com.lambda.client.setting.configs.AbstractConfig
import com.lambda.client.util.math.Vec2f

open class InteractiveComponent(
    name: String,
    posX: Float,
    posY: Float,
    width: Float,
    height: Float,
    settingGroup: SettingGroup,
    config: AbstractConfig<out Nameable> = GuiConfig
) : Component(name, posX, posY, width, height, settingGroup, config) {

    // Interactive info
    protected var lastMousePos = Vec2f.ZERO
    var mouseState = MouseState.NONE
        private set(value) {
            prevState = field
            lastStateUpdateTime = System.currentTimeMillis()
            field = value
        }
    protected var prevState = MouseState.NONE; private set
    protected var lastStateUpdateTime = System.currentTimeMillis(); private set

    override fun onGuiInit() {
        super.onGuiInit()
        mouseState = MouseState.NONE
        prevState = MouseState.NONE
        lastStateUpdateTime = System.currentTimeMillis()
    }

    // Interactive methods
    open fun onMouseInput(mousePos: Vec2f) {
        lastMousePos = mousePos
    }

    open fun onHover(mousePos: Vec2f) {
        mouseState = MouseState.HOVER
    }

    open fun onLeave(mousePos: Vec2f) {
        mouseState = MouseState.NONE
    }

    open fun onClick(mousePos: Vec2f, buttonId: Int) {
        mouseState = MouseState.CLICK
    }

    open fun onRelease(mousePos: Vec2f, buttonId: Int) {
        mouseState = if (isInComponent(mousePos)) MouseState.HOVER
        else MouseState.NONE
    }

    open fun onDrag(mousePos: Vec2f, clickPos: Vec2f, buttonId: Int) {
        mouseState = MouseState.DRAG
    }

    open fun onKeyInput(keyCode: Int, keyState: Boolean) {

    }

    private fun isInComponent(mousePos: Vec2f) = mousePos.x in 0.0f..width && mousePos.y in 0.0f..height

    enum class MouseState {
        NONE, HOVER, CLICK, DRAG
    }
}