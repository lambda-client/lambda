/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui

import com.lambda.config.settings.comparable.BooleanSetting
import com.lambda.config.settings.comparable.EnumSetting
import com.lambda.config.settings.FunctionSetting
import com.lambda.core.Loadable
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.settings.BooleanButton.Companion.booleanSetting
import com.lambda.gui.impl.clickgui.settings.EnumSelector.Companion.enumSetting
import com.lambda.gui.impl.clickgui.settings.UnitButton.Companion.unitSetting
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

        typeAdapter<EnumSetting<*>> { owner, ref ->
            owner.enumSetting(ref)
        }

        typeAdapter<FunctionSetting<Unit>> { owner, ref ->
            owner.unitSetting(ref)
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
