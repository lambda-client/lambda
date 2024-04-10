package com.lambda.gui.impl.clickgui

import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.WindowComponent
import com.lambda.gui.api.component.core.list.IListComponent
import com.lambda.module.modules.client.ClickGui

object LambdaClickGui : LambdaGui("ClickGui", ClickGui), IListComponent<WindowComponent<*>> {
    override val children: MutableList<WindowComponent<*>> get() = GuiConfigurable.windows.value
}