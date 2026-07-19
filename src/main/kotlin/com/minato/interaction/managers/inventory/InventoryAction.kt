
package com.minato.interaction.managers.inventory

import com.minato.context.SafeContext

/**
 * Represents a type of action. Inventory actions are strictly inventory-related.
 * Other actions can be external actions that happen some time within the sequence of inventory actions
 * to avoid having to use multiple requests.
 */
sealed interface InventoryAction {
	val action: SafeContext.() -> Unit

	class Inventory(override val action: SafeContext.() -> Unit) : InventoryAction
	class Player(override val action: SafeContext.() -> Unit) : InventoryAction
	class Other(override val action: SafeContext.() -> Unit) : InventoryAction
}