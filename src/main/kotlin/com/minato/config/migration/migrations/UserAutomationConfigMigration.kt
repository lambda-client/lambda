
package com.minato.config.migration.migrations

import com.minato.Minato.LOG
import com.minato.config.categories.UserAutomationCategory
import com.minato.config.migration.MigrationUtils
import com.minato.config.migration.StepConfigMigration

@Suppress("unused")
object UserAutomationConfigMigration : StepConfigMigration() {
	override val category = UserAutomationCategory
	override val latestVersion = 2

	init {
		step(1, 2) { root ->
			val count = MigrationUtils.locateAndMoveMisplacedSettings(category, root)
			LOG.info("Migrated User Automation config category schema v1 -> v2: $count settings moved")
		}
	}
}