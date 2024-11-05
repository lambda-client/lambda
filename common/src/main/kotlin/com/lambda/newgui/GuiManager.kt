package com.lambda.newgui

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.core.Loadable
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.impl.clickgui.settings.BooleanButton.Companion.booleanSetting
import java.lang.reflect.Type
import kotlin.reflect.KClass

object GuiManager : Loadable {
    val typeMap = mutableMapOf<Type, (owner: Layout, converted: Any) -> Layout>()

    private inline fun <reified T : Any> typeAdapter(noinline block: (Layout, T) -> Layout) {
        typeMap[T::class.java] = { owner, converted -> block(owner, converted as T) }
    }

    override fun load(): String {
        typeAdapter<BooleanSetting> { owner, ref ->
            owner.booleanSetting(ref)
        }

        return "Loaded ${typeMap.size} gui type adapters."
    }

    /**
     * Attempts to convert the given [reference] to the [Layout]
     */
    @UIBuilder
    inline fun <reified T : Any> Layout.layoutOf(reference: T, block: Layout.() -> Unit = {}): Layout? {
        val adapter = typeMap[T::class.java] ?: return null
        return adapter(this, reference).apply(children::add).apply(block)
    }
}