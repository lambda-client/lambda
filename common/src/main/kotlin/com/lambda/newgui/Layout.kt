package com.lambda.newgui

import com.lambda.graphics.animation.AnimationTicker
import com.lambda.graphics.gl.Scissor.scissor
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.RenderLayer
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

/**
 * Represents a component for creating complex ui structures.
 *
 * @param useBatching Increases performance by using parent's renderer instead of creating a new one.
 *
 * @param batchChildren Whether allow children to use the renderer of this layout.
 *
 * Warning: use batching if you know what you're doing.
 * Batched elements are always drawn first:
 * ```kotlin
 * // 1st
 * layout(useBatching = true) {}
 *
 * // 3rd
 * layout {}
 *
 * // 2nd
 * layout(useBatching = true) {}
 * ```
 */
open class Layout(
    private val owner: Layout?,
    useBatching: Boolean,
    private val batchChildren: Boolean
) {
    // Rectangle of the component
    open val rect: Rect get() = rectUpdate() + (owner?.rect?.leftTop ?: Vec2d.ZERO)

    // Structure
    val children = mutableListOf<Layout>()
    private var selectedChild: Layout? = null

    // Inputs
    protected var mousePosition = Vec2d.ZERO
    private val isHovered: Boolean get() = mousePosition in rect && (owner?.isHovered ?: true)

    // Graphics
    val animation = AnimationTicker()
    val renderer: RenderLayer = run {
        owner?.let { owner ->
            if (!useBatching || !owner.batchChildren) {
                return@let null
            }

            owner.renderer
        } ?: run {
            owningRenderer = true
            RenderLayer()
        }
    }

    private var owningRenderer = false
    protected open val passInteractions = false

    // Actions
    private var showActions = mutableListOf<() -> Unit>()
    private var hideActions = mutableListOf<() -> Unit>()
    private var tickActions = mutableListOf<() -> Unit>()
    private var renderActions = mutableListOf<RenderLayer.() -> Unit>()
    private var keyPressActions = mutableListOf<(key: KeyCode) -> Unit>()
    private var charTypedActions = mutableListOf<(char: Char) -> Unit>()
    private var mouseClickActions = mutableListOf<(button: Mouse.Button, action: Mouse.Action) -> Unit>()
    private var mouseMoveActions = mutableListOf<(mouse: Vec2d) -> Unit>()
    private var mouseScrollActions = mutableListOf<(delta: Double) -> Unit>()
    private var rectUpdate = { Rect.ZERO }

    /**
     * Sets the action to be performed when the element gets shown.
     *
     * @param action The action to be performed.
     */
    fun onShow(action: () -> Unit) {
        showActions += action
    }

    /**
     * Sets the action to be performed when the element gets hidden.
     *
     * @param action The action to be performed.
     */
    fun onHide(action: () -> Unit) {
        hideActions += action
    }

    /**
     * Sets the action to be performed on each tick.
     *
     * @param action The action to be performed.
     */
    fun onTick(action: () -> Unit) {
        tickActions += action
    }

    /**
     * Sets the action to be performed on each frame.
     *
     * @param action The action to be performed.
     */
    fun onRender(action: RenderLayer.() -> Unit) {
        renderActions += action
    }

    /**
     * Sets the action to be performed when a key gets pressed.
     *
     * @param action The action to be performed.
     */
    fun onKeyPress(action: (key: KeyCode) -> Unit) {
        keyPressActions += action
    }

    /**
     * Sets the action to be performed when user types a char.
     *
     * @param action The action to be performed.
     */
    fun onCharTyped(action: (char: Char) -> Unit) {
        charTypedActions += action
    }

    /**
     * Sets the action to be performed when mouse button gets clicked.
     *
     * @param action The action to be performed.
     */
    fun onMouseClick(action: (button: Mouse.Button, action: Mouse.Action) -> Unit) {
        mouseClickActions += action
    }

    /**
     * Sets the action to be performed when mouse moves.
     *
     * @param action The action to be performed.
     */
    fun onMouseMove(action: (mouse: Vec2d) -> Unit) {
        mouseMoveActions += action
    }

    /**
     * Sets the action to be performed on mouse scroll.
     *
     * @param action The action to be performed.
     */
    fun onMouseScroll(action: (delta: Double) -> Unit) {
        mouseScrollActions += action
    }

    /**
     * Sets the rectangle of this component.
     */
    fun rect(block: () -> Rect) {
        rectUpdate = block
    }

    fun onEvent(e: GuiEvent) {
        // Select an element that's on foreground
        selectedChild = if (mousePosition in rect) children.lastOrNull {
            !it.passInteractions && mousePosition in it.rect
        } else null

        // Update children
        children.forEach { child ->
            if (e is GuiEvent.Render) return@forEach

            if (e is GuiEvent.MouseClick) {
                val newAction = if (child == selectedChild || (child.passInteractions)) e.action else Mouse.Action.Release
                val newEvent = GuiEvent.MouseClick(e.button, newAction, e.mouse)
                child.onEvent(newEvent)
                return@forEach
            }

            child.onEvent(e)
        }

        when (e) {
            is GuiEvent.Show -> { mousePosition = Vec2d.ONE * -1000.0; showActions.forEach { it() } }
            is GuiEvent.Hide -> { hideActions.forEach { it() } }
            is GuiEvent.Tick -> { animation.tick(); tickActions.forEach { it() } }
            is GuiEvent.KeyPress -> { keyPressActions.forEach { it(e.key) } }
            is GuiEvent.CharTyped -> { charTypedActions.forEach { it((e.char)) } }
            is GuiEvent.MouseMove -> { mousePosition = e.mouse; mouseMoveActions.forEach { it(e.mouse) } }
            is GuiEvent.MouseScroll -> { mousePosition = e.mouse; mouseScrollActions.forEach { it(e.delta) } }
            is GuiEvent.MouseClick -> {
                mousePosition = e.mouse
                val action = if (isHovered) e.action else Mouse.Action.Release
                mouseClickActions.forEach { it(e.button, action) }
            }
            is GuiEvent.Render -> scissor(rect) {
                val (pre, post) = children.partition { !it.owningRenderer }

                pre.forEach { it.onEvent(e) }
                renderActions.forEach { it(renderer) }

                if (owningRenderer) {
                    scissor(rect, renderer::render)
                }

                post.forEach { it.onEvent(e) }
            }
        }
    }

    companion object {
        /**
         * Creates an empty [Layout]
         *
         * @param useBatching Increases performance by using parent's renderer instead of creating a new one.
         *
         * @param batchChildren Whether allow children to use the renderer of this layout
         *
         * Check [Layout] description for more info about batching
         */
        @UIBuilder
        fun Layout.layout(
            useBatching: Boolean = false,
            batchChildren: Boolean = false,
            block: Layout.() -> Unit,
        ) = Layout(this, useBatching, batchChildren)
            .apply(children::add).apply(block)
    }
}

@DslMarker
annotation class UIBuilder