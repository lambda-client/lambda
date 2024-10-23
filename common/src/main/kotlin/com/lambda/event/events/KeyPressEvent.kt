/*
 * Copyright 2024 Lambda
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

import com.lambda.event.EventFlow
import com.lambda.event.callback.Cancellable
import com.lambda.event.callback.ICancellable
import com.lambda.util.KeyCode

/**
 * A class representing a [KeyPressEvent] in the event system ([EventFlow]).
 *
 * A [KeyPressEvent] is a type of event that is triggered when a key is pressed.
 * It implements [ICancellable] interface, which means the event can be cancelled.
 *
 * @property keyCode The key code of the key that was pressed.
 * @property scanCode The scan code of the key that was pressed.
 * @property action The action that was performed on the key.
 * @property modifiers The modifiers that were active when the key was pressed.
 */
data class KeyPressEvent(
    val keyCode: Int,
    val scanCode: Int,
    val action: Int,
    val modifiers: Int,
) : ICancellable by Cancellable() {
    val translated: KeyCode
        get() = KeyCode.virtualMapUS(keyCode, scanCode)
}
