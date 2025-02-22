/*
 * Copyright 2024 Lambda
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

package com.lambda.gui

import com.lambda.Lambda.mc
import com.lambda.event.Muteable
import com.lambda.event.events.RenderEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.events.GuiEvent
import com.lambda.gui.component.layout.Layout
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.Nameable
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
        listen<RenderEvent.GUI.Scaled> { event ->
            screenSize = event.screenSize
            layout.onEvent(GuiEvent.Update)
            layout.onEvent(GuiEvent.Render)
        }

        listen<TickEvent.Pre> {
            layout.onEvent(GuiEvent.Tick)
        }
    }

    /**
     * Activates this screen.
     *
     * Closes any currently open screen and schedules a render call to set this instance as the active GUI.
     */
    fun show() {
        mc.currentScreen?.close()

        recordRenderCall {
            mc.setScreen(this)
        }
    }

    /**
     * Called when the screen becomes visible.
     *
     * Triggers the layout's show event to notify that the GUI has been displayed.
     */
    override fun onDisplayed() {
        layout.onEvent(GuiEvent.Show)
    }

    /**
     * Signals that the screen is being removed.
     *
     * Triggers a [GuiEvent.Hide] event in the layout system to handle any necessary cleanup or state updates when the screen is dismissed.
     */
    override fun removed() {
        layout.onEvent(GuiEvent.Hide)
    }

    /**
 * Determines whether the game should pause when this screen is active.
 *
 * Always returns false so that gameplay continues uninterrupted.
 *
 * @return false
 */
override fun shouldPause() = false

    /**
     * Renders the screen without applying the default background tint.
     *
     * This override removes the background tint to ensure the custom layout is displayed as intended.
     *
     * @param context the drawing context used for rendering.
     * @param mouseX the current mouse x-coordinate.
     * @param mouseY the current mouse y-coordinate.
     * @param delta the time elapsed since the last frame.
     */
    override fun render(context: DrawContext?, mouseX: Int, mouseY: Int, delta: Float) {
        // Let's remove background tint
    }

    /**
     * Processes key press events by mapping the input key codes and dispatching them to the GUI layout.
     *
     * This method translates the provided keyCode and scanCode into a virtual key code using a US keyboard mapping,
     * then triggers a corresponding key press event on the layout. When the escape key is pressed, it closes the screen.
     *
     * @return true, indicating the event was handled.
     */
    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        val translated = KeyCode.virtualMapUS(keyCode, scanCode)
        layout.onEvent(GuiEvent.KeyPress(translated))

        if (keyCode == KeyCode.ESCAPE.keyCode) {
            close()
        }

        return true
    }

    /**
     * Processes a character typing event by dispatching it to the layout.
     *
     * @param chr the character that was typed.
     * @param modifiers modifier keys active during the event; currently not used.
     * @return always true, indicating the event was handled.
     */
    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        layout.onEvent(GuiEvent.CharTyped(chr))
        return true
    }

    /**
     * Handles mouse click events by converting the click coordinates to the screen's coordinate system and dispatching a click event to the layout.
     *
     * @return true, indicating that the event was handled.
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        layout.onEvent(GuiEvent.MouseClick(Mouse.Button.fromMouseCode(button), Mouse.Action.Click, rescaleMouse(mouseX, mouseY)))
        return true
    }

    /**
     * Handles mouse release events by dispatching a corresponding event to the layout system.
     *
     * This method rescales the provided mouse coordinates and converts the button code to trigger a mouse click event
     * with a release action.
     *
     * @param mouseX the horizontal coordinate of the mouse pointer
     * @param mouseY the vertical coordinate of the mouse pointer
     * @param button the code of the mouse button that was released
     * @return true indicating the event was handled
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        layout.onEvent(GuiEvent.MouseClick(Mouse.Button.fromMouseCode(button), Mouse.Action.Release, rescaleMouse(mouseX, mouseY)))
        return true
    }

    /**
     * Handles mouse movement events by rescaling window coordinates and dispatching them to the layout.
     *
     * The method converts the given mouse coordinates from window space to the screen's coordinate system
     * and then triggers a corresponding mouse move event in the layout system.
     *
     * @param mouseX the horizontal position of the mouse cursor in window coordinates.
     * @param mouseY the vertical position of the mouse cursor in window coordinates.
     */
    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        layout.onEvent(GuiEvent.MouseMove(rescaleMouse(mouseX, mouseY)))
    }

    /**
     * Processes a mouse scroll event by rescaling the mouse coordinates and dispatching a vertical scroll event to the layout.
     *
     * @param mouseX The x-coordinate of the mouse in window space.
     * @param mouseY The y-coordinate of the mouse in window space.
     * @param horizontalAmount The horizontal scroll amount; this value is currently ignored.
     * @param verticalAmount The vertical scroll delta.
     * @return Always returns true to indicate the event was handled.
     */
    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        layout.onEvent(GuiEvent.MouseScroll(rescaleMouse(mouseX, mouseY), verticalAmount))
        return true
    }

    /**
     * Rescales the provided mouse coordinates from the window's coordinate system to the screen's coordinate system.
     *
     * The function normalizes the mouse position by dividing it by the window dimensions, then scales 
     * it using the current screen size.
     *
     * @param mouseX the x-coordinate of the mouse in window space.
     * @param mouseY the y-coordinate of the mouse in window space.
     * @return the rescaled mouse position as a Vec2d in the screen's coordinate system.
     */
    private fun rescaleMouse(mouseX: Double, mouseY: Double): Vec2d {
        val mcMouse = Vec2d(mouseX, mouseY)
        val mcWindow = Vec2d(mc.window.scaledWidth, mc.window.scaledHeight)

        val uv = mcMouse / mcWindow
        return uv * screenSize
    }
}
