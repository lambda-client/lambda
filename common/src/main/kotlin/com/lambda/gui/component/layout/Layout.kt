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

package com.lambda.gui.component.layout

import com.lambda.graphics.RenderMain
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.event.events.GuiEvent
import com.lambda.graphics.pipeline.ScissorAdapter
import com.lambda.gui.component.HAlign
import com.lambda.gui.component.VAlign
import com.lambda.gui.component.core.LayoutBuilder
import com.lambda.gui.component.core.UIBuilder
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

/**
 * Represents a component for creating complex ui structures.
 */
open class Layout(
    val owner: Layout?
) {
    val rect get() = Rect.basedOn(renderPosition, renderSize)

    // ToDo: impl alignmentLayout: Layout, instead of being able to align to the owner only
    // Position of the component
    var position: Vec2d
        get() = Vec2d(positionX, positionY)
        set(value) { positionX = value.x; positionY = value.y }

    private var positionX: Double
        get() = ownerX + (relativePosX + dockingOffsetX).let {
            if (!properties.clampPosition) return@let it
            it.coerceAtMost(ownerWidth - renderWidth).coerceAtLeast(0.0)
        }; set(value) { relativePosX = value - ownerX - dockingOffsetX }

    private var positionY: Double
        get() = ownerY + (relativePosY + dockingOffsetY).let {
            if (!properties.clampPosition) return@let it
            it.coerceAtMost(ownerHeight - renderHeight).coerceAtLeast(0.0)
        }; set(value) { relativePosY = value - ownerY - dockingOffsetY }

    val leftTop get() = renderPosition
    val rightTop get() = Vec2d(renderPositionX + renderWidth, renderPositionY)
    val rightBottom get() = Vec2d(renderPositionX + renderWidth, renderPositionY + renderHeight)
    val leftBottom get() = Vec2d(renderPositionX, renderPositionY + renderHeight)

    val renderPosition get() = Vec2d(renderPositionX, renderPositionY)
    val renderPositionX get() = positionXTransform()
    val renderPositionY get() = positionYTransform()
    private var positionXTransform = { positionX }
    private var positionYTransform = { positionY }
    private var relativePosX = 0.0
    private var relativePosY = 0.0

    // Size of the component
    var size: Vec2d
        get() = Vec2d(width, height)
        set(value) { width = value.x; height = value.y }

    val renderSize get() = Vec2d(renderWidth, renderHeight)
    val renderWidth get() = widthTransform()
    val renderHeight get() = heightTransform()
    private var widthTransform = { width }
    private var heightTransform = { height }
    var width = 0.0
    var height = 0.0

    // Horizontal alignment
    var horizontalAlignment = HAlign.LEFT; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePosX += delta * (renderWidth - ownerWidth)
    }

    private val dockingOffsetX get() = if (horizontalAlignment == HAlign.LEFT) 0.0
    else (ownerWidth - renderWidth) * horizontalAlignment.multiplier

    // Vertical alignment
    var verticalAlignment = VAlign.TOP; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePosY += delta * (renderHeight - ownerHeight)
    }

    private val dockingOffsetY get() = if (verticalAlignment == VAlign.TOP) 0.0
    else (ownerHeight - renderHeight) * verticalAlignment.multiplier

    // Use screen limits if [owner] is null
    private var screenSize = Vec2d.ZERO

    // Owner params (cached, due to the nullability of [owner])
    private var ownerX = 0.0
    private var ownerY = 0.0
    private var ownerWidth = 0.0
    private var ownerHeight = 0.0

    /**
     * Configurable properties of the component
     */
    val properties = LayoutProperties()

    // Structure
    val children = mutableListOf<Layout>()
    var selectedChild: Layout? = null
    protected open val renderChildren: Boolean get() = renderWidth > 0 && renderHeight > 0

    // Inputs
    protected var mousePosition = Vec2d.ZERO
    var isHovered = false; get() = field && (owner?.isHovered ?: true)

    // Actions
    private var showActions = mutableListOf<Layout.() -> Unit>()
    private var hideActions = mutableListOf<Layout.() -> Unit>()
    private var tickActions = mutableListOf<Layout.() -> Unit>()
    private var updateActions = mutableListOf<Layout.() -> Unit>()
    private var renderActions = mutableListOf<Layout.() -> Unit>()
    private var keyPressActions = mutableListOf<Layout.(key: KeyCode) -> Unit>()
    private var charTypedActions = mutableListOf<Layout.(char: Char) -> Unit>()
    private var mouseClickActions = mutableListOf<Layout.(button: Mouse.Button, action: Mouse.Action) -> Unit>()
    private var mouseMoveActions = mutableListOf<Layout.(mouse: Vec2d) -> Unit>()
    private var mouseScrollActions = mutableListOf<Layout.(delta: Double) -> Unit>()

    /**
     * Applies the provided configuration block to this layout.
     *
     * This extension function executes the given lambda with the layout as its receiver,
     * enabling convenient DSL-style configuration.
     *
     * @param action the configuration block to be executed on the layout.
     */
    @LayoutBuilder
    fun <T : Layout> T.use(action: T.() -> Unit) {
        action(this).apply {  }
    }

    /**
     * Registers an action to be executed when this layout is shown.
     *
     * The provided lambda is added to the list of callbacks that trigger when the layout becomes visible.
     * The action is executed with the layout as its receiver.
     *
     * @param action the lambda to execute when the layout is shown
     */
    @LayoutBuilder
    fun <T : Layout> T.onShow(action: T.() -> Unit) {
        showActions += { action() }
    }

    /**
     * Registers a callback to be executed when the layout is hidden.
     *
     * The provided lambda is added to this layout's hide event actions, allowing you to define
     * custom behavior that occurs when the layout transitions from visible to hidden.
     *
     * @param action the lambda to execute when the layout is hidden
     */
    @LayoutBuilder
    fun <T : Layout> T.onHide(action: T.() -> Unit) {
        hideActions += { action() }
    }

    /**
     * Registers an action to be executed on every tick event for the layout.
     *
     * Multiple tick actions can be registered, and each will be invoked during the tick update cycle.
     *
     * @param action a lambda function with receiver that specifies the operation to perform on each tick.
     */
    @LayoutBuilder
    fun <T : Layout> T.onTick(action: T.() -> Unit) {
        tickActions += { action() }
    }

    /**
     * Registers an update action to be executed before each frame.
     *
     * The provided lambda is appended to the layout's update actions and is invoked during each update cycle,
     * allowing dynamic modifications to the layout prior to rendering.
     *
     * @param action the lambda specifying the update behavior.
     */
    @LayoutBuilder
    fun <T : Layout> T.onUpdate(action: T.() -> Unit) {
        updateActions += { action() }
    }

    /**
     * Registers a callback to be executed on every render frame.
     *
     * This function adds the provided lambda to the layout's render actions, ensuring it is invoked
     * during each frame when rendering the layout.
     *
     * @param action The lambda to execute during the layout's render cycle.
     */
    @LayoutBuilder
    fun <T : Layout> T.onRender(action: T.() -> Unit) {
        renderActions += { action() }
    }

    /**
     * Registers a callback to handle key press events on the layout.
     *
     * When a key press event occurs, the provided [action] is executed with the [KeyCode] corresponding
     * to the pressed key.
     *
     * @param action Lambda to be invoked on key press, receiving a [KeyCode] as its parameter.
     */
    @LayoutBuilder
    fun <T : Layout> T.onKeyPress(action: T.(key: KeyCode) -> Unit) {
        keyPressActions += { key -> action(key) }
    }

    /**
     * Registers an action to be executed when a character is typed.
     *
     * The provided lambda is invoked with the typed character whenever a character input event occurs.
     *
     * @param action a lambda function executed with the character input.
     */
    @LayoutBuilder
    fun <T : Layout> T.onCharTyped(action: T.(char: Char) -> Unit) {
        charTypedActions += { char -> action(char) }
    }

    /**
     * Registers a mouse click event handler on this layout.
     *
     * The provided lambda is executed when a mouse button is clicked, receiving both the mouse button and the associated click action.
     *
     * @param action the callback to invoke on a mouse click event, with the clicked button and action as parameters.
     */
    @LayoutBuilder
    fun <T : Layout> T.onMouseClick(action: T.(button: Mouse.Button, action: Mouse.Action) -> Unit) {
        mouseClickActions += { button, mouseAction ->  action(button, mouseAction) }
    }

    /**
     * Registers an action to execute when the mouse moves over the layout.
     *
     * The provided lambda is invoked with the current mouse position (a Vec2d), allowing custom behavior in response to mouse movement.
     *
     * @param action A lambda that processes the mouse position during a mouse move event.
     */
    @LayoutBuilder
    fun <T : Layout> T.onMouseMove(action: T.(mouse: Vec2d) -> Unit) {
        mouseMoveActions += { mouse -> action(mouse) }
    }

    /**
     * Registers a mouse scroll event handler for the layout.
     *
     * The provided lambda receives the scroll delta, allowing you to define custom behavior in response
     * to mouse scroll input.
     *
     * @param action the lambda to be executed on a mouse scroll event, with the scroll delta as its parameter.
     */
    @LayoutBuilder
    fun <T : Layout> T.onMouseScroll(action: T.(delta: Double) -> Unit) {
        mouseScrollActions += { delta -> action(delta) }
    }

    /**
     * Sets a custom lambda to override the layout's computed x position.
     *
     * The provided lambda is invoked during rendering to determine the x coordinate,
     * allowing for custom horizontal positioning that bypasses the default layout logic.
     *
     * @param transform a lambda returning the x coordinate for the layout.
     */
    @LayoutBuilder
    fun overrideX(transform: () -> Double) {
        positionXTransform = transform
    }

    /**
     * Overrides the layout's y-coordinate during rendering.
     *
     * Sets a transformation function to compute a forced y position for the layout, bypassing its default positioning.
     *
     * @param transform A lambda that calculates and returns the new y-coordinate.
     */
    @LayoutBuilder
    fun overrideY(transform: () -> Double) {
        positionYTransform = transform
    }

    /**
     * Overrides the layout's drawn position by applying custom transformation functions.
     *
     * The provided lambda functions compute new x and y coordinates that will be used during rendering,
     * effectively bypassing the layout's default position calculation.
     *
     * @param x lambda function returning the new x-coordinate.
     * @param y lambda function returning the new y-coordinate.
     */
    @LayoutBuilder
    fun overridePosition(x: () -> Double, y: () -> Double) {
        positionXTransform = x
        positionYTransform = y
    }

    /**
     * Overrides the layout's drawn width with a custom computed value.
     *
     * This function sets a lambda that computes the width during rendering, enabling dynamic adjustments.
     *
     * @param transform A lambda that returns the desired width as a Double.
     */
    @LayoutBuilder
    fun overrideWidth(transform: () -> Double) {
        widthTransform = transform
    }

    /**
     * Overrides the layout's drawn height using a custom transform function.
     *
     * @param transform A lambda returning the new height value to be applied during rendering.
     */
    @LayoutBuilder
    fun overrideHeight(transform: () -> Double) {
        heightTransform = transform
    }

    /**
     * Overrides the layout's rendered dimensions.
     *
     * By supplying custom lambda expressions for width and height, this function
     * forces the layout to use the specified dimensions instead of its default size.
     *
     * @param width Lambda that returns the new width value.
     * @param height Lambda that returns the new height value.
     */
    @LayoutBuilder
    fun overrideSize(width: () -> Double, height: () -> Double) {
        widthTransform = width
        heightTransform = height
    }

    /**
     * Expands the layout to match its parent's bounding rectangle.
     *
     * This method sets override functions for the layout's x-position, y-position, width, and height,
     * causing the layout to fill its parent's render area. By default, if a parent exists, the layout adopts
     * the parent's render properties; otherwise, it falls back to the layout's existing dimensions.
     *
     * @param overrideX Lambda that computes the x-coordinate. Defaults to the parent's render x-position or
     *        the layout's own x value if no parent is present.
     * @param overrideY Lambda that computes the y-coordinate. Defaults to the parent's render y-position or
     *        the layout's own y value if no parent is present.
     * @param overrideWidth Lambda that computes the width. Defaults to the parent's render width or
     *        the layout's own width if no parent is present.
     * @param overrideHeight Lambda that computes the height. Defaults to the parent's render height or
     *        the layout's own height if no parent is present.
     */
    @LayoutBuilder
    fun fillParent(
        overrideX: () -> Double = { owner?.renderPositionX ?: ownerX },
        overrideY: () -> Double = { owner?.renderPositionY ?: ownerY },
        overrideWidth: () -> Double = { owner?.renderWidth ?: ownerWidth },
        overrideHeight: () -> Double = { owner?.renderHeight ?: ownerHeight }
    ) {
        overrideX(overrideX)
        overrideY(overrideY)
        overrideWidth(overrideWidth)
        overrideHeight(overrideHeight)
    }

    /**
     * Removes this layout from its parent.
     *
     * This function removes the layout from its parent's list of children, ensuring that it is not a root layout and preventing duplicate removals.
     * It will throw an IllegalStateException if the layout has no owner (i.e., is a root layout) or if it has already been removed.
     *
     * @throws IllegalStateException if the layout is a root layout or if it has been destroyed previously.
     */
    fun destroy() {
        check(owner != null) {
            "Unable to destroy root layout. Owner is null."
        }

        check(owner.children.remove(this)) {
            "destroy() called twice. The layout was already removed"
        }
    }

    init {
        onUpdate { // Update the layout
            screenSize = RenderMain.screenSize

            // Update relative position and bounds
            ownerX = owner?.renderPositionX ?: ownerX
            ownerY = owner?.renderPositionY ?: ownerY
            ownerWidth = owner?.renderWidth ?: screenSize.x
            ownerHeight = owner?.renderHeight ?: screenSize.y

            // Update hover state (don't mark as hovered if hovered pixel is outside the owner)
            val xh = (mousePosition.x - renderPositionX) in 0.0..renderWidth
            val yh = (mousePosition.y - renderPositionY) in 0.0..renderHeight
            isHovered = xh && yh

            // Select an element that's on foreground
            selectedChild = if (isHovered) children.lastOrNull {
                !it.properties.interactionPassthrough && mousePosition in it.rect
            } else null
        }
    }

    /**
     * Processes a GUI event by updating the layout's state and dispatching the event to registered actions and child layouts.
     *
     * Depending on the event type, it updates internal properties such as mouse position, triggers specific action lists
     * (e.g., show, hide, tick, key press, char typed, mouse move, mouse scroll, and mouse click), and propagates the event
     * to its children. For mouse click events, the action is adjusted based on the child's hover or selection state.
     * In the case of a render event, it executes render actions and conditionally applies scissor clipping before rendering children.
     *
     * @param e the GUI event to process
     */
    fun onEvent(e: GuiEvent) {
        // Update self
        when (e) {
            is GuiEvent.Show -> {
                mousePosition = Vec2d.ONE * -1000.0
                showActions.forEach { it(this) }
            }
            is GuiEvent.Hide -> {
                hideActions.forEach { it(this) }
            }
            is GuiEvent.Tick -> {
                tickActions.forEach { it(this) }
            }
            is GuiEvent.KeyPress -> {
                keyPressActions.forEach { it(this, e.key) }
            }
            is GuiEvent.CharTyped -> {
                charTypedActions.forEach { it(this, e.char) }
            }
            is GuiEvent.Update -> {
                updateActions.forEach { it(this) }
            }
            is GuiEvent.Render -> {}
            is GuiEvent.MouseMove -> {
                mousePosition = e.mouse
                mouseMoveActions.forEach { it(this, e.mouse) }
            }
            is GuiEvent.MouseScroll -> {
                mousePosition = e.mouse

                if (isHovered) {
                    mouseScrollActions.forEach { it(this, e.delta) }
                }
            }
            is GuiEvent.MouseClick -> {
                mousePosition = e.mouse
                val action = if (isHovered) e.action else Mouse.Action.Release
                mouseClickActions.forEach { it(this, e.button, action) }
            }
        }

        // Update children
        children.forEach { child ->
            if (e is GuiEvent.Render) return@forEach
            if (e is GuiEvent.MouseClick) {
                val hovered = child == selectedChild || (child.isHovered && child.properties.interactionPassthrough)
                val newAction = if (hovered) e.action else Mouse.Action.Release

                val newEvent = GuiEvent.MouseClick(e.button, newAction, e.mouse)
                child.onEvent(newEvent)
                return@forEach
            }

            child.onEvent(e)
        }

        if (e is GuiEvent.Render) {
            val block = {
                renderActions.forEach { it(this) }
                if (renderChildren) children.forEach { it.onEvent(e) }
            }

            if (!properties.scissor) block()
            else ScissorAdapter.scissor(rect, block)
        }
    }

    companion object {
        /**
             * Creates a new child [Layout] instance within the current layout.
             *
             * The new layout is added to the parent's children list and configured using the provided lambda.
             *
             * @param block Optional lambda to configure the newly created [Layout].
             */
        @UIBuilder
        fun Layout.layout(
            block: Layout.() -> Unit = {},
        ) = Layout(this)
            .apply(children::add).apply(block)

        /**
         * Creates an [AnimationTicker] for the layout.
         *
         * The ticker manages animation updates and, if registered (default), is automatically
         * ticked on the layout's tick events. Otherwise, you must invoke its tick() method manually.
         * Multiple tickers can be attached to a layout to handle animations with distinct timings.
         *
         * @param register whether the ticker should be automatically ticked on each layout tick.
         * @return the newly created [AnimationTicker] instance.
         */
        @UIBuilder
        fun Layout.animationTicker(register: Boolean = true) = AnimationTicker().apply {
            if (register) onTick {
                this@apply.tick()
            }
        }

        /**
         * Creates a [Mouse.CursorController] for managing the mouse cursor state.
         *
         * Use the returned controller to specify the cursor appearance for various UI conditions such as hovering,
         * resizing, or typing. The controller is automatically reset when the layout is hidden.
         */
        @UIBuilder
        fun Layout.cursorController(): Mouse.CursorController {
            val con = Mouse.CursorController()
            onHide { con.reset() }
            return con
        }
    }
}
