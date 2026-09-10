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
import com.lambda.interaction.container.NestedContainer
import com.lambda.interaction.container.containers.ArmorContainer
import com.lambda.interaction.container.containers.CreativeContainer
import com.lambda.interaction.container.containers.CursorContainer
import com.lambda.interaction.container.containers.HotbarContainer
import com.lambda.interaction.container.containers.InventoryContainer
import com.lambda.interaction.container.containers.OffHandContainer
import com.lambda.interaction.container.selection.ContainerSelectionBuilder.Companion.containerSelection
import com.lambda.interaction.handler.handlers.ContainerSearchScope

@ContainerMarker
fun Container.select(
    scope: ContainerSearchScope = ContainerSearchScope.Accessed
) = containerSelection(scope) { ofAny(this@select) }

@ContainerMarker
fun selectContainers(
    vararg containers: Container,
    scope: ContainerSearchScope = ContainerSearchScope.Accessed
) = containerSelection(scope) { ofAny(*containers) }

/**
 * ContainerSelection is a class that holds a predicate for matching containers.
 */
@Suppress("unused")
class ContainerSelection @ContainerMarker internal constructor(
    val selector: (Container) -> Boolean,
    val scope: ContainerSearchScope = ContainerSearchScope.Accessed,
    val containersWhitelist: Collection<Container> = emptyList(),
    val containersBlacklist: Collection<Container> = emptyList(),
    val accessScope: AccessScope = AccessScope.Both,
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
                ?.asSequence()
                ?.filter { it in containers && (accessScope.accessed?.equals(it.isAccessed) != false) }
                ?: containers.asSequence()
        }.filter { it !in containersBlacklist }
            .filter(selector)
            .sortedWith(comparator)
            .toList()

    companion object {
        val ACCESSED = ContainerSelection({ true })
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
        this.containersBlacklist.addAll(selection.containersBlacklist)
        this.accessScope = selection.accessScope
        this.comparator = selection.comparator
    }

    private var selector: (Container) -> Boolean = { true }
    private val containersWhitelist = mutableListOf<Container>()
    private val containersBlacklist = mutableListOf<Container>()
    private var accessScope: AccessScope = AccessScope.Both
    private var comparator: Comparator<Container> = compareBy { it.type }

    fun ofAny(vararg containers: Container) {
        containersWhitelist.addAll(containers)
        appendSelector { container -> container in containersWhitelist }
    }

    fun noneOf(vararg containers: Container) {
        containersBlacklist.addAll(containers)
        appendSelector { container -> container !in containersBlacklist }
    }

    fun ofAnyType(vararg types: ContainerType) {
        appendSelector { container -> types.contains(container.type) }
    }

    fun noneOfType(vararg types: ContainerType) {
        appendSelector { container -> !types.contains(container.type) }
    }

    fun isNested() {
        appendSelector { container -> container is NestedContainer }
    }

    fun notNested() {
        appendSelector { container -> container !is NestedContainer }
    }

    fun matches(containerSelection: ContainerSelection) {
        appendSelector { container -> containerSelection.matches(container) }
    }

    fun noMatch(containerSelection: ContainerSelection) {
        appendSelector { container -> !containerSelection.matches(container) }
    }

    fun hasStack(stackSelection: StackSelection) {
        appendSelector { container -> stackSelection isIn container }
    }

    fun noStack(stackSelection: StackSelection) {
        appendSelector { container -> !stackSelection.isIn(container) }
    }

    fun hasSpace(stackSelection: StackSelection) {
        appendSelector { container -> stackSelection spaceIn container }
    }

    fun noSpace(stackSelection: StackSelection) {
        appendSelector { container -> !stackSelection.spaceIn(container) }
    }

    fun isAccessed() {
        accessScope = AccessScope.Accessed
        appendSelector { it.isAccessed }
    }

    fun notAccessed() {
        accessScope = AccessScope.NotAccessed
        appendSelector { !it.isAccessed }
    }

    fun predicate(predicate: (Container) -> Boolean) {
        appendSelector { predicate(it) }
    }

    fun sortedWith(comparator: Comparator<Container>) {
        this.comparator = comparator
    }

    fun sortedWith(comparatorSupplier: () -> Comparator<Container>) {
        this.comparator = comparatorSupplier()
    }

    private fun appendSelector(selector: (Container) -> Boolean) {
        this.selector = { this.selector(it) && selector(it) }
    }

    private fun build() = ContainerSelection(selector, scope, containersWhitelist, containersBlacklist, accessScope, comparator)

    companion object {
        fun containerSelection(
            scope: ContainerSearchScope = ContainerSearchScope.Accessed,
            builder: ContainerSelectionBuilder.() -> Unit
        ) = ContainerSelectionBuilder(scope).apply(builder).build()

        fun ContainerSelection.mutate(
            scope: ContainerSearchScope = this.scope,
            builder: ContainerSelectionBuilder.() -> Unit = {}
        ) = ContainerSelectionBuilder(this, scope).apply(builder).build()
    }
}

enum class AccessScope(val accessed: Boolean?) {
    Both(null),
    Accessed(true),
    NotAccessed(false)
}