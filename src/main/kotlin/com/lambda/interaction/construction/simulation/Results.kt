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

package com.lambda.interaction.construction.simulation

import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.results.GenericResult

@SimDsl
interface Results<T : BuildResult> {
    fun SimInfo.result(result: GenericResult) = addResult(result)
    fun SimInfo.result(result: T) = addResult(result)

    private fun SimInfo.addResult(result: BuildResult) {
        concurrentResults.add(
            dependencyStack
                .asReversed()
                .fold(result) { acc, dependent ->
                    with(dependent) { dependentUpon(acc) }
                }
        )
    }
}