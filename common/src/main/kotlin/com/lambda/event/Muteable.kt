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

package com.lambda.event

/**
 * The [Muteable] interface represents any listening object that may need
 * to temporarily stop listening to events.
 *
 * Implementing this interface allows an object to
 * control its listening state through the [isMuted] property.
 * When [isMuted] is `true`, the object will not receive or process events.
 *
 * This can be useful in scenarios where an object's event handling behavior should be paused, for example,
 * when the object is in a certain state or when a specific condition is met.
 *
 * @property isMuted A flag indicating whether the object is currently muted.
 */
interface Muteable {
    val isMuted: Boolean
}
