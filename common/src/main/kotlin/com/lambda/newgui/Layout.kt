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
 * Represents a component for creating complex ui structures
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
    private var lastMouse = Vec2d.ZERO
    private val isHovered: Boolean get() = lastMouse in rect && (owner?.isHovered ?: true)

    // Graphics
    val animation = AnimationTicker()
    val renderer: RenderLayer
    private var owningRenderer = false

    init {
        val parentRenderer = owner?.renderer
        val parentAcceptsBatching = owner?.batchChildren ?: false

        renderer = if (!useBatching || !parentAcceptsBatching || parentRenderer == null) {
            owningRenderer = true
            RenderLayer()
        } else {
            parentRenderer
        }
    }

    // Actions
    private var showAction = {}
    private var hideAction = {}
    private var tickAction = {}
    private var renderAction: RenderLayer.() -> Unit = {}
    private var keyPressAction: (key: KeyCode) -> Unit = {}
    private var charTypedAction: (char: Char) -> Unit = {}
    private var mouseClickAction: (button: Mouse.Button, action: Mouse.Action) -> Unit = { _, _ -> }
    private var mouseMoveAction: (mouse: Vec2d) -> Unit = {}
    private var mouseScrollAction: (delta: Double) -> Unit = {}
    private var rectUpdate = { Rect.ZERO }

    /**
     * Sets the action to be performed when the element gets shown.
     *
     * @param action The action to be performed.
     */
    fun onShow(action: () -> Unit) {
        showAction = action
    }

    /**
     * Sets the action to be performed when the element gets hidden.
     *
     * @param action The action to be performed.
     */
    fun onHide(action: () -> Unit) {
        hideAction = action
    }

    /**
     * Sets the action to be performed on each tick.
     *
     * @param action The action to be performed.
     */
    fun onTick(action: () -> Unit) {
        tickAction = action
    }

    /**
     * Sets the action to be performed on each frame.
     *
     * @param action The action to be performed.
     */
    fun onRender(action: RenderLayer.() -> Unit) {
        renderAction = action
    }

    /**
     * Sets the action to be performed when a key gets pressed.
     *
     * @param action The action to be performed.
     */
    fun onKeyPress(action: (key: KeyCode) -> Unit) {
        keyPressAction = action
    }

    /**
     * Sets the action to be performed when user types a char.
     *
     * @param action The action to be performed.
     */
    fun onCharTyped(action: (char: Char) -> Unit) {
        charTypedAction = action
    }

    /**
     * Sets the action to be performed when mouse button gets clicked.
     *
     * @param action The action to be performed.
     */
    fun onMouseClick(action: (button: Mouse.Button, action: Mouse.Action) -> Unit) {
        mouseClickAction = action
    }

    /**
     * Sets the action to be performed when mouse moves.
     *
     * @param action The action to be performed.
     */
    fun onMouseMove(action: (mouse: Vec2d) -> Unit) {
        mouseMoveAction = action
    }

    /**
     * Sets the action to be performed on mouse scroll.
     *
     * @param action The action to be performed.
     */
    fun onMouseScroll(action: (delta: Double) -> Unit) {
        mouseScrollAction = action
    }

    /**
     * Sets the rectangle of this component.
     */
    fun rect(block: () -> Rect) {
        rectUpdate = block
    }

    fun onEvent(e: GuiEvent) {
        // Select an element that's on foreground
        selectedChild = if (lastMouse in rect) children.lastOrNull {
            lastMouse in it.rect
        } else null

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

        when (e) {
            is GuiEvent.Show -> { lastMouse = Vec2d.ONE * -1000.0; showAction() }
            is GuiEvent.Hide -> { hideAction() }
            is GuiEvent.Tick -> { animation.tick(); tickAction() }
            is GuiEvent.KeyPress -> { keyPressAction(e.key) }
            is GuiEvent.CharTyped -> { charTypedAction(e.char) }
            is GuiEvent.MouseMove -> { lastMouse = e.mouse; mouseMoveAction(e.mouse) }
            is GuiEvent.MouseScroll -> { lastMouse = e.mouse; mouseScrollAction(e.delta) }
            is GuiEvent.MouseClick -> {
                lastMouse = e.mouse
                val action = if (selectedChild == null) e.action else Mouse.Action.Release
                mouseClickAction(e.button, action)
            }
            is GuiEvent.Render -> {
                val (pre, post) = children.partition { !it.owningRenderer }

                // Add drawables from this layout
                renderAction(renderer)

                // Add children's drawables over
                pre.forEach { it.onEvent(e) }

                scissor(rect) {
                    // Perform a drawcall
                    if (owningRenderer) {
                        renderer.render()
                    }

                    // Draw children with custom renderers
                    post.forEach { it.onEvent(e) }
                }
            }
        }
    }
}