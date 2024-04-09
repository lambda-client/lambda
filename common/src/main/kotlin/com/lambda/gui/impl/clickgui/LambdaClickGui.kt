package com.lambda.gui.impl.clickgui

import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.IListComponent
import com.lambda.gui.api.component.sub.ButtonComponent
import com.lambda.gui.impl.clickgui.windows.TagWindow
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui
import com.lambda.util.Mouse
import com.lambda.util.math.Vec2d

class LambdaClickGui : LambdaGui("Lambda ClickGui", ClickGui), IListComponent<WindowComponent<*>> {
    override val children = mutableListOf<WindowComponent<*>>()

    init {
        children.add(TagWindow())
    }
}