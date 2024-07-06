package com.lambda.gui.api

import com.lambda.Lambda.mc
import com.lambda.event.Muteable
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.gui.api.component.core.IComponent
import com.lambda.gui.impl.AbstractClickGui
import com.lambda.module.Module
import com.lambda.threading.runSafe
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.Nameable
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.mojang.blaze3d.systems.RenderSystem.recordRenderCall
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

abstract class LambdaGui(
    override val name: String,
    private val owner: Module? = null
) : Screen(Text.of(name)), IComponent, Nameable, Muteable {
    var screenSize = Vec2d.ZERO
    override val rect get() = Rect(Vec2d.ZERO, screenSize)

    val isOpen get() = mc.currentScreen == this
    override val isMuted: Boolean get() = !isOpen
    private var closingAction: (() -> Unit)? = null

    val animation = AnimationTicker()

    init {
        listener<RenderEvent.GUI.Scaled> { event ->
            screenSize = event.screenSize
            onEvent(GuiEvent.Render())
        }

        listener<TickEvent.Pre> {
            animation.tick()
            onEvent(GuiEvent.Tick())
        }
    }

    /**
     * Shows this gui screen
     *
     * No safe context required (TODO: let user open clickgui via main menu)
     */
    fun show() {
        owner?.enable()
        if (isOpen) return

        when (val screen = mc.currentScreen) {
            is AbstractClickGui -> {
                screen.close()

                screen.setCloseTask {
                    mc.setScreen(this)
                }
            }

            else -> {
                screen?.close()

                recordRenderCall {
                    mc.setScreen(this)
                }
            }
        }
    }

    final override fun onDisplayed() {
        onEvent(GuiEvent.Show())
    }

    override fun removed() {
        onEvent(GuiEvent.Hide())

        runSafe {
            // quick crashfix (is there any other way to prevent gui being closed twice?)
            mc.currentScreen = null
            owner?.disable()
            mc.currentScreen = this@LambdaGui

            closingAction?.let {
                recordRenderCall(it)
                closingAction = null
            }
        }
    }

    fun setCloseTask(block: () -> Unit) {
        closingAction = block
    }

    final override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
        // Let's remove background tint
    }

    final override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val translated = KeyCode.virtualMapUS(keyCode, scanCode)
        onEvent(GuiEvent.KeyPress(translated))

        if (keyCode == KeyCode.ESCAPE.keyCode) {
            close()
        }

        return true
    }

    final override fun charTyped(chr: Char, modifiers: Int): Boolean {
        onEvent(GuiEvent.CharTyped(chr))
        return true
    }

    final override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        onEvent(GuiEvent.MouseClick(Mouse.Button(button), Mouse.Action.Click, rescaleMouse(mouseX, mouseY)))
        return true
    }

    final override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        onEvent(GuiEvent.MouseClick(Mouse.Button(button), Mouse.Action.Release, rescaleMouse(mouseX, mouseY)))
        return true
    }

    final override fun mouseMoved(mouseX: Double, mouseY: Double) {
        onEvent(GuiEvent.MouseMove(rescaleMouse(mouseX, mouseY)))
    }

    final override fun shouldPause() = false

    private fun rescaleMouse(mouseX: Double, mouseY: Double): Vec2d {
        val mcMouse = Vec2d(mouseX, mouseY)
        val mcWindow = Vec2d(mc.window.scaledWidth, mc.window.scaledHeight)

        val uv = mcMouse / mcWindow
        return uv * screenSize
    }
}
