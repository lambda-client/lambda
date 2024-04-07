package com.lambda.gui.api

import com.lambda.Lambda.mc
import com.lambda.event.EventFlow
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.gui.api.component.core.IComponent
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.Nameable
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

interface LambdaGui : IComponent, Nameable {
    fun onCloseRequest(): Boolean = true

    /**
     * Shows this gui screen
     *
     * No safe context required (TODO: let user open clickgui via main menu)
     */
    fun show() = recordRenderCall {
        mc.setScreen(object : Screen(Text.of(name)) {
            private var screenSize = Vec2d.ZERO

            private val renderListener = listener<RenderEvent.GUI.Scaled> { event ->
                screenSize = event.screenSize
                onRender()
            }

            private val tickListener = listener<TickEvent.Pre> {
                onTick()
            }

            override fun onDisplayed() {
                onShow()
            }

            override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
                // Let's remove background tint
            }

            override fun removed() {
                onHide()

                with(EventFlow.syncListeners) {
                    unsubscribe(renderListener)
                    unsubscribe(tickListener)
                }
            }

            override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
                onKey(KeyCode(keyCode))

                if (keyCode == KeyCode.Escape.key && onCloseRequest()) {
                    this.close()
                }

                return true
            }

            override fun charTyped(chr: Char, modifiers: Int): Boolean {
                onChar(chr)
                return true
            }

            override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
                onMouseClick(Mouse.Button(button), Mouse.Action.Click, rescaleMouse(mouseX, mouseY))
                return true
            }

            override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
                onMouseClick(Mouse.Button(button), Mouse.Action.Release, rescaleMouse(mouseX, mouseY))
                return true
            }

            override fun mouseMoved(mouseX: Double, mouseY: Double) {
                onMouseMove(rescaleMouse(mouseX, mouseY))
            }

            override fun shouldPause() = false

            private fun rescaleMouse(mouseX: Double, mouseY: Double): Vec2d {
                val mcMouse = Vec2d(mouseX, mouseY)
                val mcWindow = Vec2d(mc.window.scaledWidth, mc.window.scaledHeight)

                val uv = mcMouse / mcWindow
                return uv * screenSize
            }
        })
    }
}
