
package com.minato.config.migration.migrations

import com.minato.Minato.LOG
import com.minato.config.categories.AutomationCategory
import com.minato.config.migration.MigrationUtils
import com.minato.config.migration.StepConfigMigration

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

			LOG.info("Migrated Automation config category schema v1 -> v2: $updateCount settings updated")
		}

		step(2, 3) { root ->
			val count = MigrationUtils.locateAndMoveMisplacedSettings(category, root)
			LOG.info("Migrated Automation config category schema v2 -> v3: $count settings moved")
		}
	}
}