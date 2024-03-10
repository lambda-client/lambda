package com.lambda.event.events

import com.lambda.event.cancellable.Cancellable
import com.lambda.event.cancellable.ICancellable

class KeyPressEvent(val key: Int) : ICancellable by Cancellable()