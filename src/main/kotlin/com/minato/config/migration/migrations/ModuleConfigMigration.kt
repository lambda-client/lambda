
package com.minato.config.migration.migrations

import com.minato.Minato.LOG
import com.minato.config.categories.ModuleCategory
import com.minato.config.migration.MigrationUtils
import com.minato.config.migration.StepConfigMigration

@Suppress("unused")
object ModuleConfigMigration : StepConfigMigration() {
	override val category = ModuleCategory
	override val latestVersion = 3

	init {
		step(1, 2) { root ->
			val count = MigrationUtils.locateAndMoveMisplacedSettings(category, root)
			LOG.info("Migrated Module config category schema v1 -> v2: $count settings moved")
		}

		step(2, 3) { _ ->
			// AutoUpdater module has been removed; orphan its config in place.
			LOG.info("Module config category schema v2 -> v3 (AutoUpdater removed)")
		}
	}
}