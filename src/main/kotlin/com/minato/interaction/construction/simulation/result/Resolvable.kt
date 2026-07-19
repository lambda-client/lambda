
package com.minato.interaction.construction.simulation.result

import com.minato.context.AutomatedSafeContext
import com.minato.task.Task

/**
 * Represents a [BuildResult] with a resolvable [Task]
 */
interface Resolvable {
    context(task: Task<*>, _: AutomatedSafeContext)
    fun resolve()
}
