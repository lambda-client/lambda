package com.lambda.gui.impl.clickgui

import com.lambda.Lambda.mc
import com.lambda.graphics.animation.Animation.Companion.exp
import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.ChildComponent
import com.lambda.gui.api.component.core.list.IListComponent
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

abstract class AbstractClickGui(name: String = "ClickGui") : LambdaGui(name, ClickGui), IListComponent<ChildComponent> {
    override val children = mutableListOf<ChildComponent>()
    protected var hoveredChild: ChildComponent? = null

    private var closing = false
    var guiAnimation by animation.exp(0.0, 1.0, {
        if (closing) ClickGui.closeSpeed else ClickGui.openSpeed
    }) { !closing }; private set

    override fun onShow() {
        super<IListComponent>.onShow()
        hoveredChild = null
        closing = false
        guiAnimation = 0.0
    }

    override fun onTick() {
        super<IListComponent>.onTick()
        if (closing && guiAnimation < 0.01) mc.setScreen(null)
    }

    override fun onRender() {
        super<IListComponent>.onRender()

        // only one window can be hovered at the same time
        children.forEach {
            it.accessible = false
        }

        if (!closing) hoveredChild?.accessible = true
    }

    override fun onMouseClick(button: Mouse.Button, action: Mouse.Action, mouse: Vec2d) {
        // move hovered window into foreground
        (hoveredChild as? WindowComponent<*>)?.let {
            children.remove(it)
            children.add(it)
        }

        super<IListComponent>.onMouseClick(button, action, mouse)
    }

    override fun onMouseMove(mouse: Vec2d) {
        hoveredChild = children.lastOrNull { child ->
            mouse in child.rect
        }

        super<IListComponent>.onMouseMove(mouse)
    }

    override fun close() {
        closing = true
    }
}