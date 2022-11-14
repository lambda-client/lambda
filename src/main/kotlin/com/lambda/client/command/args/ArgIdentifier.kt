package com.lambda.client.command.args

import com.lambda.client.util.interfaces.Nameable

/**
 * The ID for an argument
 */
@Suppress("UNUSED")
data class ArgIdentifier<T : Any>(override val name: String) : Nameable
