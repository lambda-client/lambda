package com.lambda.newgui.component.layout

import com.lambda.graphics.RenderMain
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.graphics.gl.Scissor.scissor
import com.lambda.gui.api.GuiEvent
import com.lambda.gui.api.RenderLayer
import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.VAlign
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.coerceIn

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
    val owner: Layout?,
    useBatching: Boolean,
    private val batchChildren: Boolean,
) {
    /**
     * The rectangle of this component
     */
    var rect
        get() = Rect.basedOn(actualPosition, actualSize)
        set(value) { position = value.leftTop; size = value.size }

    private val actualPosition get() = positionOverride()
    private val actualSize get() = sizeOverride()

    /**
     * Relative position of the component
     */
    var relativePos = Vec2d.ZERO

    /**
     * The position of the component
     *
     * Note: actual position could be overridden using [overridePosition], to get actual position use [rect].leftTop instead
     */
    var position: Vec2d
        get() = ownerRect.leftTop + relativeToAbs(relativePos).let {
            if (!properties.clampPosition) it
            else it.coerceIn(
                0.0, ownerRect.size.x - actualSize.x,
                0.0, ownerRect.size.y - actualSize.y
            )
        }; set(value) { relativePos = absToRelative(value - ownerRect.leftTop) }

    /**
     * The size of this component
     *
     * Note: actual size could be overridden using [overrideSize], to get actual size use [rect].size instead
     */
    var size = Vec2d.ZERO

    /**
     * Horizontal alignment
     */
    var horizontalAlignment = HAlign.LEFT; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePos += Vec2d.RIGHT * delta * (actualSize.x - ownerRect.size.x)
    }

    /**
     * Vertical alignment
     */
    var verticalAlignment = VAlign.TOP; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePos += Vec2d.BOTTOM * delta * (actualSize.y - ownerRect.size.y)
    }

    // Rect-related properties
    private var screenSize = Vec2d.ZERO
    private val ownerRect get() = owner?.rect ?: Rect(Vec2d.ZERO, screenSize)
    private val dockingOffset get() = (ownerRect.size - actualSize) * Vec2d(horizontalAlignment.multiplier, verticalAlignment.multiplier)
    private fun relativeToAbs(posIn: Vec2d) = posIn + dockingOffset
    private fun absToRelative(posIn: Vec2d) = posIn - dockingOffset

    /**
     * Configurable properties of the component
     */
    val properties = LayoutProperties()

    // Structure
    val children = mutableListOf<Layout>()
    protected var selectedChild: Layout? = null

    // Inputs
    protected var mousePosition = Vec2d.ZERO
    protected val isHovered: Boolean get() = mousePosition in rect && (owner?.isHovered ?: true)

    // Graphics
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
    private var positionOverride: (() -> Vec2d) = { position }
    private var sizeOverride: (() -> Vec2d) = { size }

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
     * Overrides the drawn position of the component
     */
    fun overridePosition(transform: () -> Vec2d) {
        positionOverride = transform
    }

    /**
     * Overrides the drawn size of the component
     */
    fun overrideSize(transform: () -> Vec2d) {
        sizeOverride = transform
    }

    fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Render) {
            screenSize = RenderMain.screenSize
        }

        // Select an element that's on foreground
        selectedChild = if (isHovered) children.lastOrNull {
            !it.properties.interactionPassthrough && mousePosition in it.rect
        } else null

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

        when (e) {
            is GuiEvent.Show -> { mousePosition = Vec2d.ONE * -1000.0; showActions.forEach { it() } }
            is GuiEvent.Hide -> { hideActions.forEach { it() } }
            is GuiEvent.Tick -> { tickActions.forEach { it() } }
            is GuiEvent.KeyPress -> { keyPressActions.forEach { it(e.key) } }
            is GuiEvent.CharTyped -> { charTypedActions.forEach { it((e.char)) } }
            is GuiEvent.MouseMove -> { mousePosition = e.mouse; mouseMoveActions.forEach { it(e.mouse) } }
            is GuiEvent.MouseScroll -> {
                mousePosition = e.mouse

                if (isHovered) {
                    mouseScrollActions.forEach { it(e.delta) }
                }
            }
            is GuiEvent.MouseClick -> {
                mousePosition = e.mouse
                val action = if (isHovered) e.action else Mouse.Action.Release
                mouseClickActions.forEach { it(e.button, action) }
            }
            is GuiEvent.Render -> {
                val drawAction = {
                    val drawChildren = rect.size.let { it.x > 0.1 && it.y > 0.1 }

                    // ToDo: clipping filter to increase performance
                    // filter { it.rect in this.rect }
                    val partition by lazy { children.partition { !it.owningRenderer } }

                    renderActions.forEach { it(renderer) }

                    if (drawChildren) {
                        partition.first.forEach { it.onEvent(e) }
                    }

                    if (owningRenderer) {
                        renderer.render()
                    }

                    if (drawChildren) {
                        partition.second.forEach { it.onEvent(e) }
                    }
                }

                if (properties.scissor) {
                    scissor(rect, drawAction)
                } else drawAction()
            }
        }
    }

    companion object {
        /**
         * Creates an empty [Layout].
         *
         * @param useBatching Whether to use parent's renderer.
         *
         * @param batchChildren Whether allow children to use the renderer of this layout.
         *
         * @param block Actions to perform within this component.
         *
         * Check [Layout] description for more info about batching.
         */
        @UIBuilder
        fun Layout.layout(
            useBatching: Boolean = false,
            batchChildren: Boolean = false,
            block: Layout.() -> Unit = {},
        ) = Layout(this, useBatching, batchChildren)
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
            if (register) onTick(this::tick)
        }

        /**
         * Creates new [Mouse.CursorController].
         *
         * Use it to set the mouse cursor type for various conditions: hovering, resizing, typing etc...
         */
        @UIBuilder
        @Suppress("UNUSED_EXPRESSION")
        fun Layout.cursorController(): Mouse.CursorController {
            this // hack ide to let me make that ui-related only
            return Mouse.CursorController()
        }
    }
}
