package com.lambda.newgui.impl.clickgui

import com.lambda.module.tag.ModuleTag
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.window.Window
import com.lambda.newgui.component.window.WindowContent
import com.lambda.util.math.Vec2d

class ModuleWindow(
    owner: Layout,
    val tag: ModuleTag, // todo: tag system
    initialPosition: Vec2d
) : Window(owner, tag.name, initialPosition, minimizing = Minimizing.Absolute, autoResize = AutoResize.ByConfig) {
    init {
        onTick {
            val modules = content.children.filterIsInstance<ModuleLayout>()

            modules.forEachIndexed { i, it ->
                it.isLast = modules.lastIndex == i
            }
        }
    }

    companion object {
        /**
         * Creates a [ModuleWindow]
         */
        @UIBuilder
        fun Layout.moduleWindow(
            tag: ModuleTag,
            position: Vec2d = Vec2d.ZERO,
            block: WindowContent.() -> Unit = {}
        ) = ModuleWindow(this, tag, position).apply(children::add).apply {
            block(this.content)
        }
    }
}