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
    val rect get() = Rect.basedOn(renderPosition, renderSize)

    var position: Vec2d
        get() = Vec2d(positionX, positionY)
        set(value) { positionX = value.x; positionY = value.y }

    var positionX: Double
        get() = ownerX + (relativePosX + dockingOffsetX).let {
            if (!properties.clampPosition) return@let it
            it.coerceAtMost(ownerWidth - renderWidth).coerceAtLeast(0.0)
        }; set(value) { relativePosX = value - ownerX - dockingOffsetX }

    var positionY: Double
        get() = ownerY + (relativePosY + dockingOffsetY).let {
            if (!properties.clampPosition) return@let it
            it.coerceAtMost(ownerHeight - renderHeight).coerceAtLeast(0.0)
        }; set(value) { relativePosY = value - ownerY - dockingOffsetY }

    var size: Vec2d
        get() = Vec2d(width, height)
        set(value) { width = value.x; height = value.y }

    val leftTop get() = position
    val rightTop get() = Vec2d(renderPositionX + renderWidth, renderPositionY)
    val rightBottom get() = Vec2d(renderPositionX + renderWidth, renderPositionY + renderHeight)
    val leftBottom get() = Vec2d(renderPositionX, renderPositionY + renderHeight)

    var width = 0.0
    var height = 0.0

    val renderPosition get() = Vec2d(renderPositionX, renderPositionY)
    val renderPositionX get() = positionXTransform()
    val renderPositionY get() = positionYTransform()
    private var positionXTransform = { positionX }
    private var positionYTransform = { positionY }

    val renderSize get() = Vec2d(renderWidth, renderHeight)
    val renderWidth get() = widthTransform()
    val renderHeight get() = heightTransform()
    private var widthTransform = { width }
    private var heightTransform = { height }

    private var relativePosX = 0.0
    private var relativePosY = 0.0

    var horizontalAlignment = HAlign.LEFT; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePosX += delta * (renderWidth - ownerWidth)
    }

    var verticalAlignment = VAlign.TOP; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePosY += delta * (renderHeight - ownerHeight)
    }

    private var screenSize = Vec2d.ZERO

    private var ownerX = 0.0
    private var ownerY = 0.0

    private var ownerWidth = 0.0
    private var ownerHeight = 0.0

    private val dockingOffsetX get() = if (horizontalAlignment == HAlign.LEFT) 0.0
    else (ownerWidth - renderWidth) * horizontalAlignment.multiplier

    private val dockingOffsetY get() = if (verticalAlignment == VAlign.TOP) 0.0
    else (ownerHeight - renderHeight) * verticalAlignment.multiplier

    /**
     * Configurable properties of the component
     */
    val properties = LayoutProperties()

    // Structure
    val children = mutableListOf<Layout>()
    protected var selectedChild: Layout? = null

    // Inputs
    protected var mousePosition = Vec2d.ZERO
    var isHovered = false; get() = field && (owner?.isHovered ?: true)

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
     * Force overrides drawn x position of the layout
     */
    fun overrideX(transform: () -> Double) {
        positionXTransform = transform
    }

    /**
     * Force overrides drawn y position of the layout
     */
    fun overrideY(transform: () -> Double) {
        positionYTransform = transform
    }

    /**
     * Force overrides drawn position of the layout
     */
    fun overridePosition(x: () -> Double, y: () -> Double) {
        positionXTransform = x
        positionYTransform = y
    }

    /**
     * Force overrides drawn width of the layout
     */
    fun overrideWidth(transform: () -> Double) {
        widthTransform = transform
    }

    /**
     * Force overrides drawn height of the layout
     */
    fun overrideHeight(transform: () -> Double) {
        heightTransform = transform
    }

    /**
     * Force overrides drawn size of the layout
     */
    fun overrideSize(width: () -> Double, height: () -> Double) {
        widthTransform = width
        heightTransform = height
    }

    fun onEvent(e: GuiEvent) {
        if (e is GuiEvent.Render) {
            screenSize = RenderMain.screenSize

            ownerX = owner?.renderPositionX ?: ownerX
            ownerY = owner?.renderPositionY ?: ownerY

            ownerWidth = owner?.renderWidth ?: screenSize.x
            ownerHeight = owner?.renderHeight ?: screenSize.y

            val xh = (mousePosition.x - renderPositionX) in 0.0..renderWidth
            val yh = (mousePosition.y - renderPositionY) in 0.0..renderHeight
            isHovered = xh && yh
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
                    val drawChildren = renderWidth > 0.1 && renderHeight > 0.1

                    val partition by lazy {
                        children.partition { !it.owningRenderer }
                    }

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
            this // hack ide to let me make this ui-related only
            return Mouse.CursorController()
        }
    }
}
