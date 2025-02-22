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

package com.lambda.interaction.material

import com.lambda.interaction.material.container.MaterialContainer

/**
 * ContainerSelection is a class that holds a predicate for matching MaterialContainers.
 * It can be combined using "and", "or", etc.
 */
class ContainerSelection {
	private var selector: (MaterialContainer) -> Boolean = { true }

	/**
	 * Tests whether the provided container matches this selection.
	 */
	@ContainerSelectionDsl
	fun matches(container: MaterialContainer): Boolean = selector(container)

	/**
	 * Returns a function that matches containers having at least one stack
	 * which matches the given StackSelection.
	 */
	@ContainerSelectionDsl
	fun matches(stackSelection: StackSelection): (MaterialContainer) -> Boolean =
		{ container -> container.matchingStacks(stackSelection).isNotEmpty() }

	/**
	 * Returns a function that checks whether a given MaterialContainer matches the criteria
	 * defined in the provided ContainerSelection.
	 */
	@ContainerSelectionDsl
	fun matches(containerSelection: ContainerSelection): (MaterialContainer) -> Boolean =
		{ container -> containerSelection.matches(container) }

	/**
	 * Returns a function that matches containers whose rank is any of the types provided.
	 */
	@ContainerSelectionDsl
	fun ofAnyType(vararg types: MaterialContainer.Rank): (MaterialContainer) -> Boolean =
		{ container -> types.contains(container.rank) }

	/**
	 * Returns a function that matches containers whose rank is not any of the types provided.
	 */
	@ContainerSelectionDsl
	fun noneOfType(vararg types: MaterialContainer.Rank): (MaterialContainer) -> Boolean =
		{ container -> !types.contains(container.rank) }

	/**
	 * Returns a function that combines two container predicates using logical AND.
	 */
	@ContainerSelectionDsl
	infix fun ((MaterialContainer) -> Boolean).and(other: (MaterialContainer) -> Boolean): (MaterialContainer) -> Boolean =
		{ container -> this(container) && other(container) }

	/**
	 * Returns a function that combines two container predicates using logical OR.
	 */
	@ContainerSelectionDsl
	infix fun ((MaterialContainer) -> Boolean).or(other: (MaterialContainer) -> Boolean): (MaterialContainer) -> Boolean =
		{ container -> this(container) || other(container) }

	/**
	 * Returns a function that negates the current selection predicate.
	 */
	@ContainerSelectionDsl
	fun ((MaterialContainer) -> Boolean).negate(): (MaterialContainer) -> Boolean =
		{ container -> !this(container) }

	companion object {
		@DslMarker
		annotation class ContainerSelectionDsl

		@ContainerSelectionDsl
		fun selectContainer(
			block: ContainerSelection.() -> (MaterialContainer) -> Boolean
		): ContainerSelection =
			ContainerSelection().apply { selector = block() }
	}
}