package com.lambda.newgui

import com.lambda.core.Loadable
import com.lambda.module.Module
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.impl.clickgui.ModuleLayout.Companion.moduleLayout

object GuiManager : Loadable {
    val typeMap = mutableMapOf<Class<*>, (owner: Layout, converted: Any) -> Layout>()

    private inline fun <reified T> typeAdapter(noinline block: (Layout, T) -> Layout) {
        typeMap[T::class.java] = { owner, converted -> block(owner, converted as T) }
    }

    override fun load(): String {
        // Example, not meant to be used
        typeAdapter<Module> { owner, ref ->
            owner.moduleLayout(ref)
        }

        return super.load()
    }

    /**
     * Attempts to convert the given [reference] to the [Layout]
     *
     * Or throws [IllegalStateException] if there's no registered ui adapter for the type of the [reference]
     */
    @UIBuilder
    inline fun <reified T : Any> Layout.layoutOf(reference: T, block: Layout.() -> Unit = {}): Layout {
        val clazz = T::class.java
        val adapter = typeMap[clazz] ?: throw IllegalArgumentException("Unable to convert ${clazz.simpleName} to a layout")
        return adapter(this, reference).apply(children::add).apply(block)
    }
}