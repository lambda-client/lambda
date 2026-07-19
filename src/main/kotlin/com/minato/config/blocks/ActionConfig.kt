
package com.minato.config.blocks

import com.minato.event.events.TickEvent
import com.minato.util.Describable
import com.minato.util.NamedEnum

interface ActionConfig {
	val sorter: SortMode
	val tickStageMask: Collection<TickEvent>

	enum class SortMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		Closest("Closest", "Breaks blocks closest to the player eye position"),
		Farthest("Farthest", "Breaks blocks farthest from the player eye position"),
		Tool("Tool", "Breaks blocks with priority given to those with tools matching the current selected"),
		Rotation("Rotation", "Breaks blocks closest to the player rotation"),
		Random("Random", "Breaks blocks in a random order")
	}
}