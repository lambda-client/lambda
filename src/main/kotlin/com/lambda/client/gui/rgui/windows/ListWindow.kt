package com.lambda.client.gui.rgui.windows

import com.lambda.client.commons.extension.sumByFloat
import com.lambda.client.gui.AbstractLambdaGui
import com.lambda.client.gui.rgui.Component
import com.lambda.client.gui.rgui.InteractiveComponent
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.module.modules.client.CustomFont
import com.lambda.client.module.modules.client.GuiColors
import com.lambda.client.util.TickTimer
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.math.Vec2f
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11.*
import kotlin.math.max
import kotlin.math.min

open class ListWindow(
    name: String,
    posX: Float,
    posY: Float,
    width: Float,
    height: Float,
    saveToConfig: SettingGroup,
    vararg childrenIn: Component,
    val drawHandle: Boolean = false
) : TitledWindow(name, posX, posY, width, height, saveToConfig) {
    val children = ArrayList<Component>()

    override val minWidth = 80.0f
    override val minHeight = 200.0f
    override val maxWidth = 200.0f
    override val maxHeight get() = mc.displayHeight.toFloat()
    override val resizable: Boolean get() = hoveredChild == null

    var hoveredChild: Component? = null
        private set(value) {
            if (value == field) return
            (field as? InteractiveComponent)?.onLeave(AbstractLambdaGui.getRealMousePos())
            (value as? InteractiveComponent)?.onHover(AbstractLambdaGui.getRealMousePos())
            field = value
        }

    private val scrollTimer = TickTimer()
    private var scrollSpeed = 0.0f

    var scrollProgress = 0.0f
        set(value) {
            prevScrollProgress = field
            field = value
        }
    var prevScrollProgress = 0.0f
    private val renderScrollProgress
        get() = prevScrollProgress + (scrollProgress - prevScrollProgress) * mc.renderPartialTicks

    private var doubleClickTime = -1L

    init {
        children.addAll(childrenIn)
        updateChild()
    }

    fun addAll(all: Collection<Component>) {
        synchronized(this) {
            children.addAll(all)
        }
    }

    fun add(c: Component) {
        synchronized(this) {
            children.add(c)
        }
    }

    fun remove(c: Component) {
        synchronized(this) {
            children.remove(c)
        }
    }

    fun clear() {
        synchronized(this) {
            children.clear()
        }
    }

    private fun updateChild() {
        synchronized(this) {
            var y = (if (draggableHeight != height) draggableHeight else 0.0f) + ClickGUI.verticalMargin

            children
                .filter { it.visible }
                .forEach {
                    it.posX = ClickGUI.horizontalMargin
                    it.posY = y
                    it.width = width - ClickGUI.horizontalMargin * 2
                    y += it.height + ClickGUI.verticalMargin
                }
        }
    }

    override fun onDisplayed() {
        super.onDisplayed()
        children.forEach { it.onDisplayed() }
    }

    override fun onClosed() {
        super.onClosed()
        children.forEach { it.onClosed() }
    }

    override fun onGuiInit() {
        super.onGuiInit()
        children.forEach { it.onGuiInit() }
        updateChild()
    }

    override fun onResize() {
        super.onResize()
        updateChild()
    }

    override fun onTick() {
        super.onTick()
        if (children.isEmpty()) return

        val lastVisible = children.lastOrNull { it.visible }
        val maxScrollProgress = lastVisible?.let { max(it.posY + it.height + ClickGUI.verticalMargin + ClickGUI.resizeBar - height, 0.01f) }
            ?: draggableHeight

        var newProgress = scrollProgress + scrollSpeed

        if (!ClickGUI.scrollRubberband) {
            newProgress = newProgress.coerceIn(0.0f, maxScrollProgress)
        }

        scrollProgress = newProgress
        scrollSpeed *= 0.5f

        if (scrollTimer.tick(100L, false)) {
            if (scrollProgress < 0) {
                scrollSpeed = scrollProgress * -ClickGUI.scrollRubberbandSpeed
            } else if (scrollProgress > maxScrollProgress) {
                scrollSpeed = (scrollProgress - maxScrollProgress) * -ClickGUI.scrollRubberbandSpeed
            }
        }

        updateChild()
        children.forEach { it.onTick() }
    }

    override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)

        if (drawHandle) {
            val handleText = "....."
            val scale = 0.75f
            val posX = renderWidth / 2 - FontRenderAdapter.getStringWidth(handleText, scale) / 2
            val posY = renderHeight - 5 - FontRenderAdapter.getFontHeight(scale) / 2
            val color = with(GuiColors.text) {
                ColorHolder(r, g, b, (a * 0.6f).toInt())
            }
            FontRenderAdapter.drawString(handleText, posX, posY, CustomFont.shadow, color, scale)
        }

        synchronized(this) {
            renderChildren {
                it.onRender(vertexHelper, absolutePos.plus(it.renderPosX, it.renderPosY - renderScrollProgress))
            }
        }
    }

    override fun onPostRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onPostRender(vertexHelper, absolutePos)

        synchronized(this) {
            renderChildren {
                it.onPostRender(vertexHelper, absolutePos.plus(it.renderPosX, it.renderPosY - renderScrollProgress))
            }
        }
    }

    fun containsName(name: String): Boolean = children.any { it.name == name }

    private fun renderChildren(renderBlock: (Component) -> Unit) {
        GlStateUtils.scissor(
            ((renderPosX + ClickGUI.horizontalMargin) * ClickGUI.getScaleFactor()).toInt(),
            mc.displayHeight - ((renderPosY + renderHeight - ClickGUI.resizeBar) * ClickGUI.getScaleFactor()).toInt(),
            ((renderWidth - ClickGUI.horizontalMargin) * ClickGUI.getScaleFactor()).toInt(),
            ((renderHeight - draggableHeight - ClickGUI.resizeBar) * ClickGUI.getScaleFactor()).toInt().coerceAtLeast(0)
        )
        glEnable(GL_SCISSOR_TEST)
        glTranslatef(0.0f, -renderScrollProgress, 0.0f)

        children.filter {
            it.visible
                && it.renderPosY + it.renderHeight - renderScrollProgress > draggableHeight
                && it.renderPosY - renderScrollProgress < renderHeight
        }.forEach {
            glPushMatrix()
            glTranslatef(it.renderPosX, it.renderPosY, 0.0f)
            renderBlock(it)
            glPopMatrix()
        }

        glDisable(GL_SCISSOR_TEST)
    }

    override fun onMouseInput(mousePos: Vec2f) {
        super.onMouseInput(mousePos)
        val relativeMousePos = mousePos.minus(posX, posY - renderScrollProgress)
        if (Mouse.getEventDWheel() != 0) {
            scrollTimer.reset()
            scrollSpeed -= Mouse.getEventDWheel() * 0.1f
            updateHovered(relativeMousePos)
        }
        if (mouseState != MouseState.DRAG) {
            updateHovered(relativeMousePos)
        }
        if (!minimized) (hoveredChild as? InteractiveComponent)?.let {
            it.onMouseInput(getRelativeMousePos(mousePos, it))
        }
    }

    private fun updateHovered(relativeMousePos: Vec2f) {
        hoveredChild = if (relativeMousePos.y < draggableHeight + scrollProgress
            || relativeMousePos.y > renderHeight + scrollProgress - ClickGUI.resizeBar
            || relativeMousePos.x < ClickGUI.horizontalMargin
            || relativeMousePos.x > renderWidth - ClickGUI.horizontalMargin
        ) null
        else children.firstOrNull { it.visible && relativeMousePos.y in it.posY..it.posY + it.height }
    }

    override fun onLeave(mousePos: Vec2f) {
        super.onLeave(mousePos)
        hoveredChild = null
    }

    override fun onClick(mousePos: Vec2f, buttonId: Int) {
        super.onClick(mousePos, buttonId)

        handleDoubleClick(mousePos, buttonId)

        if (!minimized) (hoveredChild as? InteractiveComponent)?.let {
            it.onClick(getRelativeMousePos(mousePos, it), buttonId)
        }
    }

    override fun onRelease(mousePos: Vec2f, buttonId: Int) {
        super.onRelease(mousePos, buttonId)
        if (!minimized) (hoveredChild as? InteractiveComponent)?.let {
            it.onRelease(getRelativeMousePos(mousePos, it), buttonId)
        }
    }

    override fun onDrag(mousePos: Vec2f, clickPos: Vec2f, buttonId: Int) {
        super.onDrag(mousePos, clickPos, buttonId)
        if (!minimized) (hoveredChild as? InteractiveComponent)?.let {
            it.onDrag(getRelativeMousePos(mousePos, it), clickPos, buttonId)
        }
    }

    override fun onKeyInput(keyCode: Int, keyState: Boolean) {
        super.onKeyInput(keyCode, keyState)
        if (!minimized) (hoveredChild as? InteractiveComponent)?.onKeyInput(keyCode, keyState)
    }

    private fun handleDoubleClick(mousePos: Vec2f, buttonId: Int) {
        if (!visible || buttonId != 0 || mousePos.y - posY >= draggableHeight) {
            doubleClickTime = -1L
            return
        }

        val currentTime = System.currentTimeMillis()

        doubleClickTime = if (currentTime - doubleClickTime > 500L) {
            currentTime
        } else {
            val sum = children.filter(Component::visible).sumByFloat { it.height + ClickGUI.verticalMargin }
            val targetHeight = sum + draggableHeight + ClickGUI.verticalMargin + ClickGUI.resizeBar
            val maxHeight = scaledDisplayHeight - 2.0f

            height = min(targetHeight, scaledDisplayHeight - 2.0f)
            posY = min(posY, maxHeight - targetHeight)

            -1L
        }
    }

    private fun getRelativeMousePos(mousePos: Vec2f, component: InteractiveComponent) =
        mousePos.minus(posX, posY - renderScrollProgress).minus(component.posX, component.posY)
}