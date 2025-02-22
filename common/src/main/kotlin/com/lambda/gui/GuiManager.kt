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
import com.lambda.core.Loadable
import com.lambda.gui.component.core.UIBuilder
import com.lambda.gui.component.layout.Layout
import com.lambda.gui.impl.clickgui.settings.BooleanButton.Companion.booleanSetting
import kotlin.reflect.KClass

object GuiManager : Loadable {
    val typeMap = mutableMapOf<KClass<*>, (owner: Layout, converted: Any) -> Layout>()

    /**
     * Registers a layout conversion adapter for the specified type T.
     *
     * The provided lambda is stored in a mapping with the key set as the reified type's KClass,
     * enabling type-specific conversions of a Layout. The lambda casts the given instance to T
     * and applies the conversion.
     *
     * @param block A conversion lambda that accepts a Layout and an instance of type T, returning a modified Layout.
     */
    private inline fun <reified T : Any> typeAdapter(noinline block: (Layout, T) -> Layout) {
        typeMap[T::class] = { owner, converted -> block(owner, converted as T) }
    }

    /**
     * Loads and registers built-in GUI type adapters.
     *
     * This method overrides the load function from the Loadable interface to register a BooleanSetting
     * adapter. The adapter maps a Layout to a conversion function that applies the booleanSetting method.
     * It returns a message indicating the total number of loaded GUI type adapters.
     *
     * @return a status message with the count of loaded GUI type adapters.
     */
    override fun load(): String {
        typeAdapter<BooleanSetting> { owner, ref ->
            owner.booleanSetting(ref)
        }

        return "Loaded ${typeMap.size} gui type adapters."
    }

    /**
         * Converts the provided [reference] into a [Layout] using a registered type adapter.
         *
         * This extension function checks the runtime type of [reference] against a registry of layout converters.
         * If an appropriate adapter is found, it is invoked with the current layout and [reference], followed by
         * applying the optional [block] for further configuration.
         *
         * @param reference the object used to determine the conversion adapter based on its runtime type.
         * @param block an optional lambda for further configuring the converted [Layout].
         * @return the converted [Layout] if an adapter for the reference's type exists; otherwise, `null`.
         */
    @UIBuilder
    inline fun Layout.layoutOf(
        reference: Any,
        block: Layout.() -> Unit = {}
    ): Layout? =
        typeMap[reference::class]?.invoke(this, reference)?.apply(block)
}
