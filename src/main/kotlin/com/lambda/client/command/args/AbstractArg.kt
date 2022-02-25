package com.lambda.client.command.args

import com.lambda.client.commons.interfaces.Nameable

/**
 * Base of an Argument type, extends this to make new argument type
 *
 * @param T type of this argument
 */
abstract class AbstractArg<T : Any> : Nameable {

    /**
     * Type name of this argument type, used by [toString]
     */
    protected open val typeName = javaClass.simpleName.removeSuffix("Arg")

    /**
     * Argument tree for building up the arguments
     */
    protected val argTree = ArrayList<AbstractArg<*>>()

    /**
     * ID of this argument
     */
    val identifier by lazy { ArgIdentifier<T>(name) }

    /**
     * Get a immutable copy of [argTree]
     */
    fun getArgTree() = argTree.toList()

    /**
     * Check if [string] matches with this argument
     */
    internal open suspend fun checkType(string: String?) = convertToType(string) != null

    /**
     * Convert [string] to the the argument type [T]
     */
    internal abstract suspend fun convertToType(string: String?): T?

    /**
     * Appends a new [AbstractArg], copy the [argTree]
     *
     * @param arg [AbstractArg] to append
     */
    fun <T : Any> append(arg: AbstractArg<T>): AbstractArg<T> {
        if (this is FinalArg<*>) {
            throw IllegalArgumentException("${this.javaClass.simpleName} can't be appended")
        }

        arg.argTree.addAll(this.argTree)
        arg.argTree.add(this)
        return arg
    }

    /**
     * Used for printing argument help
     */
    override fun toString(): String {
        return "<$name:${typeName}>"
    }

}
