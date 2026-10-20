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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class InteractionDeterminismLintTest {
	private val roots = listOf(
		"src/main/kotlin/com/lambda/interaction/managers",
		"src/main/kotlin/com/lambda/interaction/handlers",
		"src/main/kotlin/com/lambda/interaction/construction",
	)

	private val banned = Regex("""System\.(currentTimeMillis|nanoTime)|Instant\.now|TimeSource|LocalDateTime\.now|kotlin\.random|java\.util\.Random\b|Math\.random|ThreadLocalRandom|createRandom\(|Random\.create\(""")

	private val allowed = mapOf(
		"RotationManager.kt" to "MouseHand(System.nanoTime())",
		"BreakManager.kt" to "SoundInstance.createRandom()",
	)

	@Test
	fun `interaction code reads no wall clock and draws no unseeded randomness`() {
		val files = roots.flatMap { root -> File(root).walk().filter { it.extension == "kt" }.toList() }
		assertTrue(files.size > 20, "scanned only ${files.size} files")
		val offenders = files.flatMap { file ->
			file.readLines().withIndex()
				.filter { (_, line) -> banned.containsMatchIn(line) && allowed[file.name]?.let { it in line } != true }
				.map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
		}
		assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
	}
}
