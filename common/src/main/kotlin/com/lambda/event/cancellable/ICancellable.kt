package com.lambda.event.cancellable

import com.lambda.event.CallbackEvent
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Representing a cancellable [CallbackEvent] in the event system.
 *
 * An [ICancellable] event is a type of [CallbackEvent] that can be canceled using [cancel].
 * It has a [cancelSignal] which is an [AtomicBoolean] that indicates whether the event has been canceled.
 *
 * @property cancelSignal The signal that indicates whether the event has been canceled.
 */
interface ICancellable : CallbackEvent {
    val cancelSignal: AtomicBoolean

    /**
     * Cancels the event.
     */
    fun cancel() {
        cancelSignal.set(true)
    }

    /**
     * Checks whether the event has been canceled.
     */
    fun isCanceled() = cancelSignal.get()
}