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

package com.lambda.config.migration.migrations

import com.lambda.Lambda.Log
import com.lambda.config.migration.StepConfigMigration

@Suppress("unused")
object AutomationConfigMigration : StepConfigMigration() {
	override val configName = "automation"
	override val latestVersion = 2

	init {
		step(1, 2) {
			var updateCount = 0
			entrySet().forEach { configPair ->
				getAsJsonObject(configPair.key)?.let { config ->
					config.get("Limit Timeframe")?.let { limitTimeframe ->
						if (limitTimeframe.asInt < 50) {
							config.addProperty("Limit Timeframe", 310)
							updateCount++
						}
					}
				}
			}

			Log.info("Migrated Automation config schema v1 -> v2: $updateCount settings updated")
		}
	}
}