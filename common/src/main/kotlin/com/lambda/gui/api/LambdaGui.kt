package com.lambda.gui.api

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow.syncListeners
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.UnsafeListener
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.gui.api.component.core.IComponent
import com.lambda.module.Module
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.Nameable
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

@Suppress("LeakingThis")
abstract class LambdaGui(
    override val name: String,
    private val owner: Module? = null
) : Screen(Text.of(name)), IComponent, Nameable {
    private var screenSize = Vec2d.ZERO
    val animation = AnimationTicker()

    private val renderListener = UnsafeListener(0, this, false) { event ->
        event as RenderEvent.GUI.Scaled
        screenSize = event.screenSize
        onRender()
    }

    private val tickListener = UnsafeListener(0, this, false) {
        animation.tick()
        onTick()
    }

    /**
     * Shows this gui screen
     *
     * No safe context required (TODO: let user open clickgui via main menu)
     */
    fun show() {
        mc.currentScreen?.close()

        recordRenderCall { // wait for the previous screen to be closed
            mc.setScreen(this)
        }
    }

    final override fun onDisplayed() {
        onShow()

        with(syncListeners) {
            subscribe<RenderEvent.GUI.Scaled>(renderListener)
            subscribe<TickEvent.Pre>(tickListener)
        }
    }

    final override fun removed() {
        onHide()

        // quick crashfix (is there any other way to prevent gui being closed twice?)
        mc.currentScreen = null
        owner?.disable()
        mc.currentScreen = this

        with(syncListeners) {
            unsubscribe(renderListener)
            unsubscribe(tickListener)
        }
    }

    final override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
        // Let's remove background tint
    }

    final override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        onKey(KeyCode(keyCode))

        if (keyCode == KeyCode.Escape.key) {
            close()
        }

        return true
    }

    final override fun charTyped(chr: Char, modifiers: Int): Boolean {
        onChar(chr)
        return true
    }

    final override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        onMouseClick(Mouse.Button(button), Mouse.Action.Click, rescaleMouse(mouseX, mouseY))
        return true
    }

    final override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        onMouseClick(Mouse.Button(button), Mouse.Action.Release, rescaleMouse(mouseX, mouseY))
        return true
    }

    final override fun mouseMoved(mouseX: Double, mouseY: Double) {
        onMouseMove(rescaleMouse(mouseX, mouseY))
    }

    final override fun shouldPause() = false

    private fun rescaleMouse(mouseX: Double, mouseY: Double): Vec2d {
        val mcMouse = Vec2d(mouseX, mouseY)
        val mcWindow = Vec2d(mc.window.scaledWidth, mc.window.scaledHeight)

        val uv = mcMouse / mcWindow
        return uv * screenSize
    }
}
