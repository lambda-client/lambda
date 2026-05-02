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

package com.lambda.config.groups

import com.lambda.util.Describable
import com.lambda.util.NamedEnum

interface InteractConfig : ActionConfig {
	val rotate: Boolean
	val airPlace: AirPlaceMode
	val axisRotateSetting: Boolean
	val axisRotate get() = rotate && airPlace.isEnabled && axisRotateSetting
	val interactConfirmationMode: InteractConfirmationMode
	val interactDelay: Int
	val interactionsPerTick: Int
	val swing: Boolean
	val swingType: BuildConfig.SwingType
	val sounds: Boolean

	@Suppress("unused")
	enum class AirPlaceMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		None("None", "Do not attempt air placements; only place against valid supports."),
		Standard("Standard", "Try common air-place techniques for convenience; moderate compatibility."),
		Grim("Grim", "Use grim specific air placing.");

		val isEnabled get() = this != None
	}

	enum class InteractConfirmationMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		None("No confirmation", "Interact immediately without waiting for the server; possible desync."),
		PlaceThenAwait("Interact now, confirm later", "Interact immediately, then wait for server confirmation to verify."),
		AwaitThenPlace("Confirm first, then Interact", "Wait for server response before interacting; safest, adds a short delay.")
	}
}