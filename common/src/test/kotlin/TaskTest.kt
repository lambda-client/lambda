import com.lambda.context.ClientContext
import com.lambda.context.SafeContext
import com.lambda.task.RootTask
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
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
        clientContextMock = Mockito.mockConstruction(ClientContext::class.java) { mock, _ ->
            `when`(mock.toSafe()).thenReturn(mockSafeContext)
        }
    }

    @AfterEach
    fun tearDown() {
        clientContextMock.close()
        RootTask.clear()
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