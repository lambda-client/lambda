
package com.minato.event.events

import com.minato.event.Event
import com.minato.event.callback.Cancellable
import net.minecraft.client.gui.hud.MessageIndicator
import net.minecraft.network.message.MessageSignatureData
import net.minecraft.text.Text

sealed class ChatEvent {
	class Send(var message: String) : Event, Cancellable()

	class Receive(
		var message: Text,
		var signature: MessageSignatureData?,
		var indicator: MessageIndicator?,
	) : Event, Cancellable()
}