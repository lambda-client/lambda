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
import com.lambda.event.callback.ICancellable
import net.minecraft.block.entity.SignBlockEntity
import net.minecraft.client.gui.screen.Screen

sealed class GuiEvent {
    /**
     * Triggered when a new ImGui frame is created and the client
     * is allowed to submit any command from this point until [EndImguiFrame].
     */
    data object NewImguiFrame : Event

    /**
     * Triggered when the previous ImGui frame is ended and the client
     * is able to perform OpenGL calls.
     *
     * By default, the game's framebuffer is bound.
     */
    data object EndImguiFrame : Event

    /**
     * Triggered when the sign editor GUI is opened. Can be canceled.
     */
    data class SignEditorOpen(
        var sign: SignBlockEntity,
        var front: Boolean
    ) : ICancellable by Cancellable()

    data class ScreenOpen(val screen: Screen?) : ICancellable by Cancellable()
}
