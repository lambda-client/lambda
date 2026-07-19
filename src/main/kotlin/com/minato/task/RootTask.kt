
package com.minato.task

import com.minato.threading.runSafe

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
