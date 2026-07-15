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

package com.lambda.interaction.inventory

import com.lambda.interaction.inventory.container.Container

/**
 * ContainerSelection is a class that holds a predicate for matching MaterialContainers.
 */
@Suppress("unused")
class ContainerSelection(
    val selector: (Container) -> Boolean,
    val comparator: Comparator<Container> = compareBy { it.rank }
) {
    @ContainerMarker
    fun bestMatch(containers: Iterable<Container>) = filter(containers).firstOrNull()

    @ContainerMarker
    fun matches(container: Container): Boolean = selector(container)

    @ContainerMarker
    fun filter(containers: Iterable<Container>) =
        containers
            .filter(selector)
            .sortedWith(comparator)

    companion object {
        val EVERYTHING = ContainerSelection({ true })
        val NOTHING = ContainerSelection({ false })
    }
}

@Suppress("unused")
@ContainerMarker
class ContainerSelectionBuilder private constructor() {
    private var selector: (Container) -> Boolean = { true }
    private var comparator: Comparator<Container> = compareBy { it.rank }
    private var invertNewSelectors = false

    fun ofAnyType(vararg types: Container.Rank) {
        appendSelector { container -> types.contains(container.rank) }
    }

    fun noneOfType(vararg types: Container.Rank) {
        appendSelector { container -> !types.contains(container.rank) }
    }

    fun matches(stackSelection: StackSelection) {
        appendSelector { container -> stackSelection.filter(container.slots).isNotEmpty() }
    }

    fun matches(containerSelection: ContainerSelection) {
        appendSelector { container -> containerSelection.matches(container) }
    }

    fun custom(predicate: (Container) -> Boolean) {
        appendSelector { predicate(it) }
    }

    fun inverted(block: () -> Boolean) {
        invertNewSelectors = true
        block()
        invertNewSelectors = false
    }

    fun sortedWith(comparator: Comparator<Container>) {
        this.comparator = comparator
    }

    fun sortedWith(comparatorSupplier: () -> Comparator<Container>) {
        this.comparator = comparatorSupplier()
    }

    private fun appendSelector(selector: (Container) -> Boolean) {
        val invert = invertNewSelectors
        val currentSelector = this.selector
        this.selector = { currentSelector(it) && selector(it) xor invert }
    }

    private fun build() = ContainerSelection(selector, comparator)

    companion object {
        fun selectContainer(
            builder: ContainerSelectionBuilder.() -> Unit
        ) = ContainerSelectionBuilder().apply(builder).build()
    }
}