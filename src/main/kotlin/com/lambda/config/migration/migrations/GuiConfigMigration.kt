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

import com.lambda.Lambda.LOG
import com.lambda.config.categories.GuiCategory
import com.lambda.config.migration.MigrationUtils
import com.lambda.config.migration.StepConfigMigration

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