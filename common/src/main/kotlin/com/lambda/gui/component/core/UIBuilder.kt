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

package com.lambda.gui.component.core

import com.lambda.gui.component.layout.Layout
import com.lambda.util.math.MathUtils.toInt

@DslMarker
annotation class UIBuilder

@DslMarker
annotation class LayoutBuilder

@DslMarker
annotation class UIRenderPr0p3rty

fun <T : Layout> T.insertLayout(
    owner: Layout,
    base: Layout,
    next: Boolean
) = apply {
    val index = owner.children.indexOf(base)
    check(index != -1 && base.owner == owner) { "Given layout belongs to different owner" }
    owner.children.add(index + next.toInt(), this)
}
