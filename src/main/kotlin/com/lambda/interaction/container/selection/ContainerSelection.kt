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

package com.lambda.interaction.container.selection

import com.lambda.interaction.container.Container
import com.lambda.interaction.container.ContainerMarker
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.containers.ArmorContainer
import com.lambda.interaction.container.containers.CreativeContainer
import com.lambda.interaction.container.containers.CursorContainer
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.container.containers.OffHandContainer
import com.lambda.interaction.container.selection.ContainerSelectionBuilder.Companion.selectContainer
import com.lambda.interaction.handler.handlers.ContainerSearchScope

@ContainerMarker
fun Container.select(
    scope: ContainerSearchScope = ContainerSearchScope.Accessed
) = selectContainer(scope) { ofAny(this@select) }

@ContainerMarker
fun selectContainers(
    vararg containers: Container,
    scope: ContainerSearchScope = ContainerSearchScope.Accessed
) = selectContainer(scope) { ofAny(*containers) }

/**
 * ContainerSelection is a class that holds a predicate for matching containers.
 */
@Suppress("unused")
class ContainerSelection @ContainerMarker internal constructor(
    val selector: (Container) -> Boolean,
    val scope: ContainerSearchScope = ContainerSearchScope.Accessed,
    val containersWhitelist: Collection<Container> = emptyList(),
    val accessedOnly: Boolean = false,
    val comparator: Comparator<Container> = compareBy { it.type }
) {
    @ContainerMarker
    fun bestMatch(containers: Iterable<Container>) = filter(containers).firstOrNull()

    @ContainerMarker
    fun matches(container: Container): Boolean = selector(container)

    @ContainerMarker
    fun filter(containers: Iterable<Container>) =
        run {
            containersWhitelist
                .takeIf { it.isNotEmpty() }
                ?.filter { it in containers && (!accessedOnly || it.isAccessed) }
                ?: containers
        }.filter(selector)
            .sortedWith(comparator)

    companion object {
        val EVERYTHING = ContainerSelection({ true })
        val NOTHING = ContainerSelection({ false })
        val HOTBAR_AND_INVENTORY = selectContainers(HotbarContainer, InventoryContainer)
        val PLAYER =
            selectContainers(
                HotbarContainer,
                InventoryContainer,
                OffHandContainer,
                CursorContainer,
                ArmorContainer,
                CreativeContainer
            )
    }
}

@Suppress("unused")
@ContainerMarker
class ContainerSelectionBuilder @ContainerMarker private constructor(
    private val scope: ContainerSearchScope
) {
    @ContainerMarker
    private constructor(
        selection: ContainerSelection,
        scope: ContainerSearchScope
    ) : this(scope) {
        this.selector = selection.selector
        this.containersWhitelist.addAll(selection.containersWhitelist)
        this.accessedOnly = selection.accessedOnly
        this.comparator = selection.comparator
    }

    private var selector: (Container) -> Boolean = { true }
    private var containersWhitelist = mutableListOf<Container>()
    private var accessedOnly = false
    private var comparator: Comparator<Container> = compareBy { it.type }
    private var invertNewSelectors = false

    fun ofAny(vararg container: Container) {
        containersWhitelist.addAll(container)
        appendSelector { container -> container in containersWhitelist }
    }

    fun ofAnyType(vararg types: ContainerType) {
        appendSelector { container -> types.contains(container.type) }
    }

    fun noneOfType(vararg types: ContainerType) {
        appendSelector { container -> !types.contains(container.type) }
    }

    fun matchesSlots(stackSelection: StackSelection) {
        appendSelector { container -> stackSelection.filter(container.slots).isNotEmpty() }
    }

    fun matchesStacks(stackSelection: StackSelection) {
        appendSelector { container -> stackSelection.filter(container.stacks).isNotEmpty() }
    }

    fun matches(containerSelection: ContainerSelection) {
        appendSelector { container -> containerSelection.matches(container) }
    }

    fun isAccessed() {
        accessedOnly = true
        appendSelector { it.isAccessed }
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

    private fun build() = ContainerSelection(selector, scope, containersWhitelist, accessedOnly, comparator)

    companion object {
        fun selectContainer(
            scope: ContainerSearchScope = ContainerSearchScope.Accessed,
            builder: ContainerSelectionBuilder.() -> Unit
        ) = ContainerSelectionBuilder(scope).apply(builder).build()

        fun ContainerSelection.mutate(
            scope: ContainerSearchScope = this.scope,
            builder: ContainerSelectionBuilder.() -> Unit = {}
        ) = ContainerSelectionBuilder(this, scope).apply(builder).build()
    }
}