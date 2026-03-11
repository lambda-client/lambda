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

package com.lambda.event.events

import com.lambda.event.Event
import com.lambda.event.callback.Cancellable
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