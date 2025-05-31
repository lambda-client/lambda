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

package com.lambda.event.callback

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Used as delegate for [ICancellable] events.
 *
 * A [Cancellable] event is a type of [ICancellable] that can be cancelled.
 * It has a [cancelSignal] which is an [AtomicBoolean] that indicates whether the event has been [cancel]ed.
 *
 * @property cancelSignal The signal that indicates whether the event has been canceled.
 */
open class Cancellable : ICancellable {
    override val cancelSignal = AtomicBoolean(false)
}
