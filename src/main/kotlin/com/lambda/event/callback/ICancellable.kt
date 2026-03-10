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

package com.lambda.event.callback

import com.lambda.event.CallbackEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Representing a cancellable [CallbackEvent] in the event system.
 *
 * An [ICancellable] event is a type of [CallbackEvent] that can be canceled using [cancel].
 * It has a [cancelSignal] which is an [AtomicBoolean] that indicates whether the event has been canceled.
 *
 * @property cancelSignal The signal that indicates whether the event has been canceled.
 */
interface ICancellable : CallbackEvent {
    val cancelSignal: AtomicBoolean

    /**
     * Cancels the event.
     */
    fun cancel() {
        cancelSignal.set(true)
    }

    /**
     * Checks whether the event has been canceled.
     */
    fun isCanceled() = cancelSignal.get()
}
