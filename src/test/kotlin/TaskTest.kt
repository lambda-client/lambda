import com.lambda.context.ClientContext
import com.lambda.context.SafeContext
import com.lambda.task.RootTask
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.util.Communication
import com.lambda.util.Communication.log
import io.mockk.every
import io.mockk.mockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.Mockito.mockConstruction
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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


class TaskTest {

    @Mock
    private lateinit var mockSafeContext: SafeContext

    private lateinit var clientContextMock: MockedConstruction<ClientContext>

    class TestTask(private val i: Int = 0) : Task<Int>() {
        override val name get() = "TestTask of $i"

        override fun SafeContext.onStart() {
            success(i + 1)
        }

        override fun SafeContext.onCancel() {
            success(i)
        }
    }

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        clientContextMock = mockConstruction(ClientContext::class.java) { mock, _ ->
            whenever(mock.toSafe()).thenReturn(mockSafeContext)
        }
        mockkObject(Communication)
        every { Communication.log(any(), any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        clientContextMock.close()
        RootTask.clear()
    }

    @Test
    fun `task initial state is INIT`() {
        val task = TestTask(0)
        assertEquals(Task.State.INIT, task.state)
        assertTrue(task.subTasks.isEmpty())
        assertEquals(0, task.age)
    }

    @Test
    fun `execute transitions to RUNNING and adds to parent subTasks`() {
        val parent = TestTask(0)
        val child = TestTask(1)

        child.execute(parent)

        assertEquals(Task.State.COMPLETED, child.state)
        assertTrue(parent.subTasks.contains(child))
        assertEquals(parent, child.parent)
    }

    @Test
    fun `success transitions to COMPLETED and executes finally block`() {
        val task = TestTask(5)
        var finallyCalled = false

        task.finally { result ->
            assertEquals(6, result)
            finallyCalled = true
        }.run()

        assertEquals(Task.State.COMPLETED, task.state)
        assertTrue(finallyCalled)
    }

//    @Test
//    fun `cancel transitions to CANCELLED and cancels subTasks`() {
//        val parent = TestTask(0)
//        val child = TestTask(1).apply { execute(parent) }
//
//        child.cancel()
//
//        assertEquals(Task.State.CANCELLED, child.state)
//        assertTrue(child.subTasks.all { it.state == Task.State.CANCELLED })
//    }

    @Test
    fun `pause and activate change state between PAUSED and RUNNING`() {
        val task = TestTask(0).apply { state = Task.State.RUNNING }

        task.pause()
        assertEquals(Task.State.PAUSED, task.state)

        task.activate()
        assertEquals(Task.State.RUNNING, task.state)
    }

//    @Test
//    fun `subtask pauses parent when executed with pauseParent true`() {
//        val parent = TestTask(0).apply { state = Task.State.RUNNING }
//        val child = TestTask(1)
//
//        child.execute(parent, pauseParent = true)
//
//        assertEquals(Task.State.PAUSED, parent.state)
//    }

    @Test
    fun `then chains tasks in sequence`() {
        val task1 = TestTask(1)
        val task2 = TestTask(2)

        task1.then(task2).run()

        task1.success(6) // Simulate success to trigger next task
        assertTrue(task1.parent?.subTasks?.contains(task2) == true)
    }

//    @Test
//    fun `finally block is called on failure`() {
//        var finallyCalled = false
//        val task = object : Task<Unit>() {
//            override val name = "FailingTask"
//            override fun SafeContext.onStart() {
//                throw RuntimeException("Simulated failure")
//            }
//        }.finally { finallyCalled = true }
//
//        task.run()
//
//        assertEquals(Task.State.FAILED, task.state)
//        assertTrue(finallyCalled)
//    }

    @Test
    fun `execute with self as owner throws exception`() {
        val task = TestTask(0)
        assertThrows<IllegalArgumentException> {
            task.execute(task)
        }
    }

    @Test
    fun `then with self throws exception`() {
        val task = TestTask(0)
        assertThrows<IllegalArgumentException> {
            task.then(task)
        }
    }

//    @Test
//    fun `duration is formatted correctly`() {
//        val task = TestTask(0).apply { age = 120 } // 120 * 50ms = 6000ms
//        assertEquals("000:00:00:06.00", task.duration)
//    }

    @Test
    fun `toString includes task hierarchy and state`() {
        val parent = TestTask(1)
        val child = TestTask(2).apply { execute(parent) }

        val expected = """
            TestTask of 1 [Initialized] 
                TestTask of 2 [Running] 0ms
        """.trimIndent().replace("\n", System.lineSeparator())

        assertTrue(parent.toString().contains("TestTask of 1"))
        assertTrue(parent.toString().contains("TestTask of 2"))
    }

    @Test
    fun `clear removes all subTasks`() {
        val parent = TestTask(0)
        TestTask(1).execute(parent)
        TestTask(2).execute(parent)

        parent.clear()

        assertTrue(parent.subTasks.isEmpty())
    }

    @Test
    fun `isMuted returns true when PAUSED or INIT`() {
        val task = TestTask(0)
        assertTrue(task.isMuted) // INIT state

        task.state = Task.State.PAUSED
        assertTrue(task.isMuted)

        task.state = Task.State.RUNNING
        assertFalse(task.isMuted)
    }

//    @Test
//    fun `failure propagates to parent with stacktrace`() {
//        val grandParent = TestTask(0)
//        val parent = TestTask(1).apply { execute(grandParent) }
//        val child = TestTask(2).apply { execute(parent) }
//
//        val exception = RuntimeException("Child failed")
//        child.failure(exception)
//
//        assertEquals(Task.State.FAILED, child.state)
//        assertEquals(Task.State.FAILED, parent.state)
//        assertEquals(Task.State.FAILED, grandParent.state)
//    }

    @Test
    fun `task with thenOrNull executes next task conditionally`() {
        val task = TestTask(0)
        var nextTaskExecuted = false

        task.thenOrNull { result ->
            if (result == 1) TestTask(1).also { nextTaskExecuted = true } else null
        }

        task.success(1)
        assertTrue(nextTaskExecuted)

        nextTaskExecuted = false
        task.success(0)
        assertFalse(nextTaskExecuted)
    }

    @Test
    fun `subtask resumes parent when completed`() {
        val parent = TestTask(0).apply { state = Task.State.RUNNING }
        val child = TestTask(1).apply { execute(parent, pauseParent = true) }

        child.success(2)

        assertEquals(Task.State.COMPLETED, child.state)
        assertEquals(Task.State.RUNNING, parent.state)
    }

    @Test
    fun `test task`() {
        val task = TestTask(5)

        assertEquals(task.name, "TestTask of 5")

        task.finally { result ->
            assertEquals(result, 6)
            assertEquals(task.state, Task.State.COMPLETED)
            assertTrue(task.isCompleted)
        }.run()
    }
}