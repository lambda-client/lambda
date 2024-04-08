package com.lambda.gui.api.component

import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.graphics.animation.AnimationTicker
import com.lambda.graphics.gl.Scissor.scissor
import com.lambda.graphics.renderer.gui.font.IFontEntry
import com.lambda.gui.api.component.core.IComponent
import com.lambda.gui.api.component.core.list.IListComponent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.layer.RenderLayer
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.KeyCode
import com.lambda.util.Mouse
import com.lambda.util.math.MathUtils.toInt
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d

abstract class WindowComponent <T : ChildComponent> : InteractiveComponent(), IListComponent<T> {
    abstract val title: String

    abstract var width: Double
    abstract var height: Double

    var position = Vec2d.ZERO

    private var isOpen = true
    private var dragOffset: Vec2d? = null
    private val padding get() = ClickGui.windowPadding

    final override val rect get() = Rect.basedOn(position, width, renderHeight + titleBarHeight)
    val contentRect get() = rect.shrink(padding).moveFirst(Vec2d(0.0, titleBarHeight - padding))

    private val titleBar get() = Rect.basedOn(rect.leftTop, rect.size.x, titleBarHeight)
    private val titleBarHeight get() = titleFont.height + 2 + padding * 2
    private val titleFont: IFontEntry

    private val layer = RenderLayer()
    private val animation = AnimationTicker()

    override val children = mutableListOf<T>()
    val subLayer = RenderLayer(true)

    private val renderHeight by animation.exp({ 0.0 }, { height + padding * 2 * isOpen.toInt() }, 0.5, ::isOpen)

    init {
        // Background
        layer.rect.build {
            position = rect
            roundRadius = ClickGui.windowRadius
            color(ClickGui.backgroundColor)
        }

        // Title
        titleFont = layer.font.build {
            text = title
            position = titleBar.center - widthVec * 0.5
        }
    }

    override fun onShow() {
        super<InteractiveComponent>.onShow()
        super<IListComponent>.onShow()

        dragOffset = null
    }

    override fun onHide() {
        super<IListComponent>.onHide()
    }

    override fun onTick() {
        animation.tick()

        children.forEach { child ->
            child.visible = isChildAccessible(child)
        }

        children
            .filter(ChildComponent::visible)
            .forEach(IComponent::onTick)
    }

    override fun onRender() {
        layer.render()

        scissor(contentRect) {
            subLayer.render()
            super<IListComponent>.onRender()
        }
    }

    override fun onMouseMove(mouse: Vec2d) {
        super<InteractiveComponent>.onMouseMove(mouse)
        super<IListComponent>.onMouseMove(mouse)

        dragOffset?.let {
            position = mouse - it
        }
    }

    override fun onKey(key: KeyCode) {
        super<IListComponent>.onKey(key)
    }

    override fun onChar(char: Char) {
        super<IListComponent>.onChar(char)
    }

    override fun onMouseClick(button: Mouse.Button, action: Mouse.Action, mouse: Vec2d) {
        super<InteractiveComponent>.onMouseClick(button, action, mouse)

        dragOffset = null

        if (mouse in titleBar && action == Mouse.Action.Click) {
            when(button) {
                Mouse.Button.Left -> dragOffset = mouse - position
                Mouse.Button.Right -> isOpen = !isOpen
            }
        }

        super<IListComponent>.onMouseClick(button, action, mouse)
    }

    override fun isChildAccessible(child: T) =
        child.rect in contentRect
}