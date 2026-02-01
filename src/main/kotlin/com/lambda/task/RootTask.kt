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

package com.lambda.task

import com.lambda.threading.runSafe

object RootTask : Task<Unit>() {
	override val name get() = "Root Task"

	@Ta5kBuilder
	inline fun <reified T : Task<*>> T.run(): T {
		execute(this@RootTask)
		return this
	}

	@Ta5kBuilder
	fun Task<*>.run(task: TaskGenerator<Unit>) {
		runSafe {
			task(Unit).execute(this@run)
		}
	}
}
