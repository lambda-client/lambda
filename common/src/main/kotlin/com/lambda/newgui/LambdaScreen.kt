package com.lambda.newgui

import com.lambda.Lambda.mc
import com.lambda.event.Muteable
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.RenderMain
import com.lambda.gui.api.GuiEvent
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.Nameable
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

/**
 * Represents a "tunnel" between the [Layout] system and minecraft's [Screen]
 */
class LambdaScreen(
    override val name: String,
    val layout: ScreenLayout
) : Screen(Text.of(name)), Nameable, Muteable {
    override val isMuted: Boolean get() = !isOpen

    private var screenSize = Vec2d.ZERO
    val isOpen get() = mc.currentScreen == this

    init {
        listener<RenderEvent.GUI.Scaled> { event ->
            screenSize = event.screenSize
            layout.onEvent(GuiEvent.Render())
        }

        listener<TickEvent.Pre> {
            layout.onEvent(GuiEvent.Tick())
        }
    }

    fun show() {
        mc.currentScreen?.close()

        recordRenderCall {
            mc.setScreen(this)
        }
    }

    override fun onDisplayed() {
        layout.onEvent(GuiEvent.Show())
    }

    override fun removed() {
        layout.onEvent(GuiEvent.Hide())
    }

    override fun shouldPause() = false

    override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
        // Let's remove background tint
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val translated = KeyCode.virtualMapUS(keyCode, scanCode)
        layout.onEvent(GuiEvent.KeyPress(translated))

        if (keyCode == KeyCode.ESCAPE.keyCode) {
            close()
        }

        return true
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        layout.onEvent(GuiEvent.CharTyped(chr))
        return true
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        layout.onEvent(GuiEvent.MouseClick(Mouse.Button(button), Mouse.Action.Click, rescaleMouse(mouseX, mouseY)))
        return true
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        layout.onEvent(GuiEvent.MouseClick(Mouse.Button(button), Mouse.Action.Release, rescaleMouse(mouseX, mouseY)))
        return true
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        layout.onEvent(GuiEvent.MouseMove(rescaleMouse(mouseX, mouseY)))
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        layout.onEvent(GuiEvent.MouseScroll(rescaleMouse(mouseX, mouseY), verticalAmount))
        return true
    }

    private fun rescaleMouse(mouseX: Double, mouseY: Double): Vec2d {
        val mcMouse = Vec2d(mouseX, mouseY)
        val mcWindow = Vec2d(mc.window.scaledWidth, mc.window.scaledHeight)

        val uv = mcMouse / mcWindow
        return uv * screenSize
    }
}
