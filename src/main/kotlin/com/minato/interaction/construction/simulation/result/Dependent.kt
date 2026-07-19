
package com.minato.interaction.construction.simulation.result

/**
 * Represents a [BuildResult] that depends on another [BuildResult].
 */
interface Dependent {
    val dependency: BuildResult
    val lastDependency: BuildResult

    companion object {
        val Dependent.iterator
            get() = generateSequence(dependency) { (it as? Dependent)?.dependency }
    }

    class Nested(override val dependency: BuildResult) : Dependent {
        override val lastDependency = iterator.last()
    }
}