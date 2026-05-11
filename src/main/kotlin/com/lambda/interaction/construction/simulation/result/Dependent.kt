/*
 * Copyright 2026 Lambda
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

package com.lambda.interaction.construction.simulation.result

/**
 * Represents a [BuildResult] that depends on another [BuildResult].
 */
interface Dependent {
    val dependency: BuildResult
    val lastDependency: BuildResult

    companion object {
        val Dependent.iterator
            get() = generateSequence(dependency) { (it as? Dependent)?.dependency }
    }

    class Nested(override val dependency: BuildResult) : Dependent {
        override val lastDependency = iterator.last()
    }
}