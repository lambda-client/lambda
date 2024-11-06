package com.lambda.newgui

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.core.Loadable
import com.lambda.newgui.component.core.UIBuilder
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.impl.clickgui.settings.BooleanButton.Companion.booleanSetting
import kotlin.reflect.KClass

object GuiManager : Loadable {
    val typeMap = mutableMapOf<KClass<*>, (owner: Layout, converted: Any) -> Layout>()

    private inline fun <reified T : Any> typeAdapter(noinline block: (Layout, T) -> Layout) {
        typeMap[T::class] = { owner, converted -> block(owner, converted as T) }
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
    inline fun Layout.layoutOf(
        reference: Any,
        block: Layout.() -> Unit = {}
    ): Layout? =
        typeMap[reference::class]?.invoke(this, reference)?.apply(block)
}
