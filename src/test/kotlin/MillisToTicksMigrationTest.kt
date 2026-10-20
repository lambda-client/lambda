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

import com.lambda.config.migration.MigrationUtils
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import kotlin.test.Test
import kotlin.test.assertEquals

class MillisToTicksMigrationTest {
	private val mapper = JsonMapper.builder().build()

	@Test
	fun `stored milliseconds become ticks wherever the setting sits`() {
		val root = mapper.readTree(
			"""{"Default":{"Settings":{"Build":{"Packet Limits":{"Limit Timeframe":310}}}},"Mine":{"Settings":{"Build":{"Limit Timeframe":1500}}}}""",
		) as ObjectNode
		assertEquals(2, MigrationUtils.millisToTicks(root, "Limit Timeframe", 1..30))
		assertEquals(6, root.at("/Default/Settings/Build/Packet Limits/Limit Timeframe").asInt())
		assertEquals(30, root.at("/Mine/Settings/Build/Limit Timeframe").asInt())
	}

	@Test
	fun `values already in ticks are left alone`() {
		val root = mapper.readTree("""{"Default":{"Settings":{"Build":{"Limit Timeframe":6}}}}""") as ObjectNode
		assertEquals(0, MigrationUtils.millisToTicks(root, "Limit Timeframe", 1..30))
		assertEquals(6, root.at("/Default/Settings/Build/Limit Timeframe").asInt())
	}
}
