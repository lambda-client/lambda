/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.world.snapshot

data class HazardRules(
	val avoidFire: Boolean = true,
	val avoidCacti: Boolean = true,
	val sneakOnMagma: Boolean = true,
) {
	companion object {
		val AVOID_ALL = HazardRules()

		val IGNORE_ALL = HazardRules(avoidFire = false, avoidCacti = false, sneakOnMagma = false)
	}
}
