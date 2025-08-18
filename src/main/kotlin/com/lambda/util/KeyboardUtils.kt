/*
 * Copyright 2025 Lambda
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

package com.lambda.util

import com.lambda.context.SafeContext
import com.lambda.core.Loadable
import com.lambda.event.events.KeyboardEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import net.minecraft.client.util.InputUtil

object KeyboardUtils : Loadable {
    var lastEvent: KeyboardEvent.Press = KeyboardEvent.Press(0, 0, -1, 0)

    /**
     * Returns whether any of the key-codes (not scan-codes) are being pressed
     */
    fun SafeContext.isKeyPressed(vararg keys: Int) =
        keys.any { InputUtil.isKeyPressed(mc.window.handle, it) }

    init {
        // hacking imgui jni lib rn because it's missing a lot of native functions including i/o stuff
        listenUnsafe<KeyboardEvent.Press> { lastEvent = it }
    }
}
