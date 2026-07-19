
package com.minato.event.callback

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Used as delegate for [ICancellable] events.
 *
 * A [Cancellable] event is a type of [ICancellable] that can be cancelled.
 * It has a [cancelSignal] which is an [AtomicBoolean] that indicates whether the event has been [cancel]ed.
 *
 * @property cancelSignal The signal that indicates whether the event has been canceled.
 */
open class Cancellable : ICancellable {
    override val cancelSignal = AtomicBoolean(false)
}
