
package com.minato.config.migration.migrations

import com.minato.Minato.LOG
import com.minato.config.categories.GuiCategory
import com.minato.config.migration.MigrationUtils
import com.minato.config.migration.StepConfigMigration

@Suppress("unused")
object GuiConfigMigration : StepConfigMigration() {
	override val category = GuiCategory
	override val latestVersion = 2

	init {
		step(1, 2) { root ->
			val count = MigrationUtils.locateAndMoveMisplacedSettings(category, root)
			LOG.info("Migrated Gui config category schema v1 -> v2: $count settings moved")
		}
	}
}