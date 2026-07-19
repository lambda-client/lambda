
package com.minato.interaction.material.container

import com.minato.context.AutomatedSafeContext
import com.minato.task.Task
import com.minato.task.TaskGenerator

interface ExternalContainer {
	context(_: AutomatedSafeContext)
	fun accessThen(exitAfter: Boolean = true, taskGenerator: TaskGenerator<Unit>): Task<*>?
}