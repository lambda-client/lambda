package com.lambda.event.cancellable

import java.util.concurrent.atomic.AtomicBoolean

open class Cancellable : ICancellable {
    override val cancelSignal = AtomicBoolean(false)
}
