
package com.minato.brigadier

import com.minato.util.CommunicationUtils
import com.mojang.brigadier.Command
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.text.Text

typealias CommandActionWithResult<S> = CommandContext<S>.() -> CommandResult
typealias CommandAction<S> = CommandContext<S>.() -> Unit

/**
 * Representation of possible results of running a command.
 */
sealed class CommandResult {
    /**
     * Representation of successful completion with the return value of [result].
     */
    class Success(
        val result: Int = Command.SINGLE_SUCCESS,
    ) : CommandResult()

    /**
     * Representation of the command failing with the specified error [message].
     */
    class Failure(
        val message: Text,
    ) : CommandResult()

    companion object {
        /**
         * Creates a [CommandResult.Success] with the given [result].
         */
        fun success(result: Int = Command.SINGLE_SUCCESS): Success {
            return Success(result)
        }

        fun failure(message: String): Failure {
            return Failure(CommunicationUtils.LogLevel.Error.text(message))
        }

        /** Creates a [CommandResult.Failure] with the given throwable [t]. */
        fun failure(t: Throwable): Failure {
            return failure(t.message ?: "An error occurred")
        }

        /**
         * Creates a [CommandResult.Failure] with the given error [message].
         */
        fun failure(message: Text): Failure {
            return Failure(message)
        }
    }
}

/**
 * Sets the action the command will take when executed.
 *
 * If [command] returns [CommandResult.Success], the command will return with [CommandResult.Success.result].
 *
 * If [command] returns [CommandResult.Failure],
 * the command will throw a [CommandException] containing the returned [CommandResult.Failure.message].
 *
 * @see ArgumentBuilder.executes
 */
@BrigadierDsl
fun <S> ArgumentBuilder<S, *>.executeWithResult(command: CommandActionWithResult<S>) {
    executes {
        when (val result = command(it)) {
            is CommandResult.Success -> result.result
            is CommandResult.Failure -> throw CommandException(result.message)
        }
    }
}

/**
 * Sets the action the command will take when executed.
 *
 * If the command does not throw an exception,
 * it succeeds with [Command.SINGLE_SUCCESS].
 *
 * To indicate possible failure more explicitly or
 * specify the resulting value, use [executeWithResult]
 * and return a [CommandResult] from [command].
 *
 * @see ArgumentBuilder.executes
 */
@BrigadierDsl
fun <S> ArgumentBuilder<S, *>.execute(command: CommandAction<S>) {
    executeWithResult {
        command(this)
        CommandResult.Success()
    }
}

class CommandException(val info: Text) : RuntimeException(info.string)
