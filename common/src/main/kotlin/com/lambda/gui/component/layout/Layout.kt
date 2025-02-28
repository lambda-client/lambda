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
import com.lambda.gui.component.core.*
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
    var rect
        get() = Rect.basedOn(position, size)
        set(value) { position = value.leftTop; size = value.size }

    // ToDo: impl alignmentLayout: Layout, instead of being able to align to the owner only
    // Position of the component
    var position: Vec2d
        get() = Vec2d(positionX, positionY)
        set(value) { positionX = value.x; positionY = value.y }

    var positionX: Double
        get() = ownerX + (relativePosX + dockingOffsetX).let {
            if (!properties.clampPosition) return@let it
            it.coerceAtMost(ownerWidth - width).coerceAtLeast(0.0)
        }; set(value) { relativePosX = value - ownerX - dockingOffsetX }

    var positionY: Double
        get() = ownerY + (relativePosY + dockingOffsetY).let {
            if (!properties.clampPosition) return@let it
            it.coerceAtMost(ownerHeight - height).coerceAtLeast(0.0)
        }; set(value) { relativePosY = value - ownerY - dockingOffsetY }

    val leftTop get() = position
    val rightTop get() = Vec2d(positionX + width, positionY)
    val rightBottom get() = Vec2d(positionX + width, positionY + height)
    val leftBottom get() = Vec2d(positionX, positionY + height)

    private var relativePosX = 0.0
    private var relativePosY = 0.0

    // Size of the component
    var size: Vec2d
        get() = Vec2d(width, height)
        set(value) { width = value.x; height = value.y }

    var width = 0.0
    var height = 0.0

    // Horizontal alignment
    var horizontalAlignment = HAlign.LEFT; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePosX += delta * (width - ownerWidth)
    }

    private val dockingOffsetX get() = if (horizontalAlignment == HAlign.LEFT) 0.0
    else (ownerWidth - width) * horizontalAlignment.multiplier

    // Vertical alignment
    var verticalAlignment = VAlign.TOP; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePosY += delta * (height - ownerHeight)
    }

    private val dockingOffsetY get() = if (verticalAlignment == VAlign.TOP) 0.0
    else (ownerHeight - height) * verticalAlignment.multiplier

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
    protected open val renderSelf: Boolean get() = width > 1 && height > 1
    protected open val scissorRect get() = rect

    // Inputs
    protected var mousePosition = Vec2d.ZERO
    open val isHovered get() = owner?.let { it.selectedChild == this } ?: true

    // Actions
    private val showActions = mutableListOf<Layout.() -> Unit>()
    private val hideActions = mutableListOf<Layout.() -> Unit>()
    private val tickActions = mutableListOf<Layout.() -> Unit>()
    private val updateActions = mutableListOf<Layout.() -> Unit>()
    private val renderActions = mutableListOf<Layout.() -> Unit>()
    private val keyPressActions = mutableListOf<Layout.(key: KeyCode) -> Unit>()
    private val charTypedActions = mutableListOf<Layout.(char: Char) -> Unit>()
    private val mouseClickActions = mutableListOf<Layout.(button: Mouse.Button, action: Mouse.Action) -> Unit>()
    private val mouseMoveActions = mutableListOf<Layout.(mouse: Vec2d) -> Unit>()
    private val mouseScrollActions = mutableListOf<Layout.(delta: Double) -> Unit>()

    /**
     * Performs the action on this layout
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.use(action: T.() -> Unit) {
        action(this)
    }

    /**
     * Sets the action to be performed when the element gets shown.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onShow(action: T.() -> Unit) {
        showActions += { action() }
    }

    /**
     * Sets the action to be performed when the element gets hidden.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onHide(action: T.() -> Unit) {
        hideActions += { action() }
    }

    /**
     * Sets the action to be performed on each tick.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onTick(action: T.() -> Unit) {
        tickActions += { action() }
    }

    /**
     * Sets the update action to be performed before each frame.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onUpdate(action: T.() -> Unit) {
        updateActions += { action() }
    }

    /**
     * Sets the action to be performed on each frame.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onRender(action: T.() -> Unit) {
        renderActions += { action() }
    }

    /**
     * Sets the action to be performed when a key gets pressed.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onKeyPress(action: T.(key: KeyCode) -> Unit) {
        keyPressActions += { key -> action(key) }
    }

    /**
     * Sets the action to be performed when user types a char.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onCharTyped(action: T.(char: Char) -> Unit) {
        charTypedActions += { char -> action(char) }
    }

    /**
     * Sets the action to be performed when mouse button gets clicked.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onMouseClick(action: T.(button: Mouse.Button, action: Mouse.Action) -> Unit) {
        mouseClickActions += { button, mouseAction ->  action(button, mouseAction) }
    }

    /**
     * Sets the action to be performed when mouse button gets clicked.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onMouseClick(button: Mouse.Button, action: Mouse.Action, block: T.() -> Unit) {
        onMouseClick { butt, act ->
            if (butt == button && act == action) block()
        }
    }

    /**
     * Sets the action to be performed when mouse moves.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onMouseMove(action: T.(mouse: Vec2d) -> Unit) {
        mouseMoveActions += { mouse -> action(mouse) }
    }

    /**
     * Sets the action to be performed on mouse scroll.
     *
     * @param action The action to be performed.
     */
    @LayoutBuilder
    fun <T : Layout> T.onMouseScroll(action: T.(delta: Double) -> Unit) {
        mouseScrollActions += { delta -> action(delta) }
    }

    /**
     * Force overrides drawn x position of the layout
     */
    @LayoutBuilder
    fun overrideX(transform: () -> Double) {
        positionX = transform()

        onUpdate {
            positionX = transform()
        }
    }

    /**
     * Force overrides drawn y position of the layout
     */
    @LayoutBuilder
    fun overrideY(transform: () -> Double) {
        positionY = transform()

        onUpdate {
            positionY = transform()
        }
    }

    /**
     * Force overrides drawn position of the layout
     */
    @LayoutBuilder
    fun overridePosition(x: () -> Double, y: () -> Double) {
        overrideX(x)
        overrideY(y)
    }

    /**
     * Force overrides drawn width of the layout
     */
    @LayoutBuilder
    fun overrideWidth(transform: () -> Double) {
        width = transform()

        onUpdate {
            width = transform()
        }
    }

    /**
     * Force overrides drawn height of the layout
     */
    @LayoutBuilder
    fun overrideHeight(transform: () -> Double) {
        height = transform()

        onUpdate {
            height = transform()
        }
    }

    /**
     * Force overrides drawn size of the layout
     */
    @LayoutBuilder
    fun overrideSize(width: () -> Double, height: () -> Double) {
        overrideWidth(width)
        overrideHeight(height)
    }

    /**
     * Removes this layout from its parent
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
            ownerX = owner?.positionX ?: ownerX
            ownerY = owner?.positionY ?: ownerY
            ownerWidth = owner?.width ?: screenSize.x
            ownerHeight = owner?.height ?: screenSize.y

            // Select an element that's on foreground
            selectedChild = if (isHovered) children.lastOrNull {
                if (it.properties.interactionPassthrough) return@lastOrNull false
                val xh = (mousePosition.x - it.positionX) in 0.0..it.width
                val yh = (mousePosition.y - it.positionY) in 0.0..it.height
                xh && yh
            } else null
        }
    }

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
            is GuiEvent.Render -> {
                if (!renderSelf) return
            }
            is GuiEvent.MouseMove -> {
                mousePosition = e.mouse
                mouseMoveActions.forEach { it(this, e.mouse) }
            }
            is GuiEvent.MouseScroll -> {
                if (!isHovered) return
                mousePosition = e.mouse
                mouseScrollActions.forEach { it(this, e.delta) }
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
                val newAction = if (child.isHovered) e.action else Mouse.Action.Release

                val newEvent = GuiEvent.MouseClick(e.button, newAction, e.mouse)
                child.onEvent(newEvent)
                return@forEach
            }

            child.onEvent(e)
        }

        if (e is GuiEvent.Render) {
            val block = {
                renderActions.forEach { it(this) }
                if (renderSelf) children.forEach { it.onEvent(e) }
            }

            if (!properties.scissor) block()
            else ScissorAdapter.scissor(scissorRect, block)
        }
    }

    companion object {
        /**
         * Creates an empty [Layout].
         *
         * @param block Actions to perform within this component.
         *
         * Check [Layout] description for more info about batching.
         */
        @UIBuilder
        fun Layout.layout(
            block: Layout.() -> Unit = {},
        ) = Layout(this)
            .apply(children::add).apply(block)

        /**
         * Creates new [AnimationTicker].
         *
         * Use it to create and manage animations.
         *
         * It's ok to have multiple tickers per component if you need to tick different animations at different timings.
         *
         * @param register Whether to tick this [AnimationTicker].
         * Otherwise, you will have to tick it manually
         */
        @UIBuilder
        fun Layout.animationTicker(register: Boolean = true) = AnimationTicker().apply {
            if (register) onTick {
                this@apply.tick()
            }
        }

        /**
         * Creates new [Mouse.CursorController].
         *
         * Use it to set the mouse cursor type for various conditions: hovering, resizing, typing etc...
         */
        @UIBuilder
        fun Layout.cursorController(): Mouse.CursorController {
            val con = Mouse.CursorController()
            onHide { con.reset() }
            return con
        }
    }
}
