
package com.minato.interaction.construction.simulation

import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.results.GenericResult

@SimDsl
interface Results<T : BuildResult> {
    fun SimInfo.result(result: GenericResult) = addResult(result)
    fun SimInfo.result(result: T) = addResult(result)

    private fun SimInfo.addResult(result: BuildResult) {
        concurrentResults.add(
            dependencyStack
                .asReversed()
                .fold(result) { acc, dependent ->
                    with(dependent) { dependentUpon(acc) }
                }
        )
    }
}