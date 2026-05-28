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
import com.lambda.config.categories.AutomationCategory
import com.lambda.config.migration.MigrationUtils
import com.lambda.config.migration.StepConfigMigration

@Suppress("unused")
object AutomationConfigMigration : StepConfigMigration() {
	override val category = AutomationCategory
	override val latestVersion = 3

	init {
		step(1, 2) { root ->
			var updateCount = 0
			root.propertyStream().forEach { configPair ->
				root.get(configPair.key).takeIf { it.isObject }?.asObject()?.let { config ->
					config.get("Limit Timeframe")?.let { limitTimeframe ->
						if (!limitTimeframe.isInt) return@forEach
						if (limitTimeframe.asInt() < 50) {
							config.put("Limit Timeframe", 310)
							updateCount++
						}
					}
				}
			}

			Log.info("Migrated Automation config category schema v1 -> v2: $updateCount settings updated")
		}

		step(2, 3) { root ->
			val count = MigrationUtils.locateAndMoveMisplacedSettings(category, root)
			Log.info("Migrated Automation config category schema v2 -> v3: $count settings moved")
		}
	}
}