
package com.minato.event.events

import com.minato.event.callback.Cancellable
import com.minato.event.callback.ICancellable
import com.minato.interaction.material.container.MaterialContainer
import net.minecraft.screen.slot.Slot

sealed class ContainerEvent {
	data class Transfer(
		val fromSlot: Slot,
		val toSlot: Slot,
		val from: MaterialContainer,
		val to: MaterialContainer
	) : ICancellable by Cancellable()
}