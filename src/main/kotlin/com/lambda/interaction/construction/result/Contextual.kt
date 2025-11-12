/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.construction.result

import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.request.hotbar.HotbarManager

/**
 * Represents a result holding a [BuildContext].
 */
interface Contextual : ComparableResult<Rank> {
    val context: BuildContext

    override fun compareResult(other: ComparableResult<Rank>) =
        when (other) {

            is Contextual -> compareByDescending<Contextual> {
                it.context.hotbarIndex == HotbarManager.serverSlot
            }.thenBy {
                it.compareBy.rank
            }.compare(this, other)

            else -> compareBy.rank.compareTo(other.compareBy.rank)
        }
}