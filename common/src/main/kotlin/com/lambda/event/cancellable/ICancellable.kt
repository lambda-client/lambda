package com.lambda.event.cancellable

import com.lambda.event.CallbackEvent
import java.util.concurrent.atomic.AtomicBoolean

interface ICancellable : CallbackEvent {
    val cancelSignal: AtomicBoolean

    fun cancel() {
        cancelSignal.set(true)
    }

    fun isCanceled() = cancelSignal.get()
}