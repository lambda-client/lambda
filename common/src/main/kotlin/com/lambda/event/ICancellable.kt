package com.lambda.event

import java.util.concurrent.atomic.AtomicBoolean

interface ICancellable : CallbackEvent {
    val cancelSignal: AtomicBoolean

    fun cancel() {
        cancelSignal.set(true)
    }

    fun isCanceled() = cancelSignal.get()
}

open class Cancellable : ICancellable {
    override val cancelSignal = AtomicBoolean(false)
}
