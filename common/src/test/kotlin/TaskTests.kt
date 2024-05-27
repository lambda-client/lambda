import com.lambda.task.Task.Companion.emptyTask
import kotlin.test.assertEquals

internal class TaskTests {
    fun testSum() {
        val task = emptyTask("1").onSuccess { task, unit ->
            emptyTask("2").onSuccess { task, unit ->
                emptyTask("3").onSuccess { task, unit ->
                    emptyTask("4")
                }
            }
        }
    }
}