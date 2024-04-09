package com.lambda.gui.impl.clickgui

import com.lambda.gui.api.LambdaGui
import com.lambda.gui.api.component.core.list.IListComponent
import com.lambda.gui.impl.clickgui.buttons.ModuleButton
import com.lambda.gui.impl.clickgui.windows.TagWindow
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.ClickGui

class LambdaClickGui : LambdaGui("Lambda ClickGui", ClickGui), IListComponent<TagWindow> {
    override val children = mutableListOf<TagWindow>()

    init {
        TagWindow(this).apply {
            children.addAll(
                ModuleRegistry.modules.map {
                    ModuleButton(it, this)
                }
            )
        }.apply(children::add)
    }
}