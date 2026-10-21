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

import com.lambda.task.Task
import com.lambda.task.tasks.wrappers.then
import com.lambda.task.tasks.wrappers.thenAction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskTreeTest {
    private class TestTask(
        override val name: String,
        override val isInvisible: Boolean = false,
    ) : Task<Unit>()

    @Test
    fun `invisible tasks are hidden and their children are promoted`() {
        val root = TestTask("Root")
        val wrapper = TestTask("Wrapper", isInvisible = true)
        val child = TestTask("Child")
        root.subTasks += wrapper
        wrapper.subTasks += child

        val tree = root.taskTree()

        assertFalse(tree.contains("Wrapper"))
        assertTrue(tree.lineSequence().any { it.startsWith("    Child") })
    }

    @Test
    fun `invisible tasks are shown when requested`() {
        val root = TestTask("Root")
        val wrapper = TestTask("Wrapper", isInvisible = true)
        val child = TestTask("Child")
        root.subTasks += wrapper
        wrapper.subTasks += child

        val tree = root.taskTree(showInvisible = true)

        assertTrue(tree.lineSequence().any { it.startsWith("    Wrapper") })
        assertTrue(tree.lineSequence().any { it.startsWith("        Child") })
    }

    @Test
    fun `chaining utilities are invisible`() {
        val leaf = TestTask("Leaf")

        assertTrue((leaf then { TestTask("Next") }).isInvisible)
        assertTrue(leaf.thenAction { }.isInvisible)
    }
}
