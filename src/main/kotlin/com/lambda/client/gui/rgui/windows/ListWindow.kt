package com.lambda.client.gui.rgui.windows

import com.lambda.client.gui.AbstractLambdaGui
import com.lambda.client.gui.rgui.Component
import com.lambda.client.gui.rgui.InteractiveComponent
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.util.TickTimer
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.math.Vec2f
import com.lambda.client.commons.extension.ceilToInt
import com.lambda.client.commons.extension.floorToInt
import com.lambda.client.commons.extension.sumByFloat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    vararg childrenIn: Component
) : TitledWindow(name, posX, posY, width, height, saveToConfig) {
    val children = ArrayList<Component>()
    private val contentMutex = Mutex()

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

    private var scrollProgress = 0.0f
        set(value) {
            prevScrollProgress = field
            field = value
        }
    private var prevScrollProgress = 0.0f
    private val renderScrollProgress
        get() = prevScrollProgress + (scrollProgress - prevScrollProgress) * mc.renderPartialTicks

    private var doubleClickTime = -1L

    init {
        children.addAll(childrenIn)
        updateChild()
    }

    fun addAll(all: Collection<Component>) {
        runBlocking {
            contentMutex.withLock {
                children.addAll(all)
            }
        }
    }

    fun add(c: Component) {
        runBlocking {
            contentMutex.withLock {
                children.add(c)
            }
        }
    }

    fun remove(c: Component) {
        runBlocking {
            contentMutex.withLock {
                children.remove(c)
            }
        }
    }

    fun clear() {
        runBlocking {
            contentMutex.withLock {
                children.clear()
            }
        }
    }

    private fun updateChild() {
        runBlocking {
            contentMutex.withLock {
                var y = (if (draggableHeight != height) draggableHeight else 0.0f) + ClickGUI.entryMargin
                for (child in children) {
                    if (!child.visible) continue
                    child.posX = ClickGUI.entryMargin * 1.618f
                    child.posY = y
                    child.width = width - ClickGUI.entryMargin * 3.236f
                    y += child.height + ClickGUI.entryMargin
                }
            }
        }
    }

    override fun onDisplayed() {
        super.onDisplayed()
        for (child in children) child.onDisplayed()
    }

    override fun onClosed() {
        super.onClosed()
        for (child in children) child.onClosed()
    }

    override fun onGuiInit() {
        super.onGuiInit()
        for (child in children) child.onGuiInit()
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
        val maxScrollProgress = lastVisible?.let { max(it.posY + it.height + ClickGUI.entryMargin - height, 0.01f) }
            ?: draggableHeight

        scrollProgress = (scrollProgress + scrollSpeed)
        scrollSpeed *= 0.5f
        if (scrollTimer.tick(100L, false)) {
            if (scrollProgress < 0.0) {
                scrollSpeed = scrollProgress * -0.25f
            } else if (scrollProgress > maxScrollProgress) {
                scrollSpeed = (scrollProgress - maxScrollProgress) * -0.25f
            }
        }

        updateChild()
        for (child in children) child.onTick()
    }

    override fun onRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onRender(vertexHelper, absolutePos)

        runBlocking {
            contentMutex.withLock {
                renderChildren {
                    it.onRender(vertexHelper, absolutePos.plus(it.renderPosX, it.renderPosY - renderScrollProgress))
                }
            }
        }
    }

    override fun onPostRender(vertexHelper: VertexHelper, absolutePos: Vec2f) {
        super.onPostRender(vertexHelper, absolutePos)

        runBlocking {
            contentMutex.withLock {
                renderChildren {
                    it.onPostRender(vertexHelper, absolutePos.plus(it.renderPosX, it.renderPosY - renderScrollProgress))
                }
            }
        }
    }

    fun containsName(name: String): Boolean =
        children.any {
            it.name == name
        }

    private fun renderChildren(renderBlock: (Component) -> Unit) {
        GlStateUtils.scissor(
            ((renderPosX + ClickGUI.entryMargin * 1.618) * ClickGUI.getScaleFactor() - 0.5f).floorToInt(),
            mc.displayHeight - ((renderPosY + renderHeight) * ClickGUI.getScaleFactor() - 0.5f).floorToInt(),
            ((renderWidth - ClickGUI.entryMargin * 3.236) * ClickGUI.getScaleFactor() + 1.0f).ceilToInt(),
            ((renderHeight - draggableHeight) * ClickGUI.getScaleFactor() + 1.0f).ceilToInt()
        )
        glEnable(GL_SCISSOR_TEST)
        glTranslatef(0.0f, -renderScrollProgress, 0.0f)

        for (child in children) {
            if (!child.visible) continue
            if (child.renderPosY + child.renderHeight - renderScrollProgress < draggableHeight) continue
            if (child.renderPosY - renderScrollProgress > renderHeight) continue
            glPushMatrix()
            glTranslatef(child.renderPosX, child.renderPosY, 0.0f)
            renderBlock(child)
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
        hoveredChild = if (relativeMousePos.y < draggableHeight || relativeMousePos.x < ClickGUI.entryMargin || relativeMousePos.x > renderWidth - ClickGUI.entryMargin) null
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
            val sum = children.filter(Component::visible).sumByFloat { it.height + ClickGUI.entryMargin }
            val targetHeight = max(height, sum + draggableHeight + ClickGUI.entryMargin)
            val maxHeight = scaledDisplayHeight - 2.0f

            height = min(targetHeight, scaledDisplayHeight - 2.0f)
            posY = min(posY, maxHeight - targetHeight)

            -1L
        }
    }

    private fun getRelativeMousePos(mousePos: Vec2f, component: InteractiveComponent) =
        mousePos.minus(posX, posY - renderScrollProgress).minus(component.posX, component.posY)
}