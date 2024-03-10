package com.lambda.event.events

import com.lambda.event.Cancellable
import com.lambda.event.ICancellable

class KeyPressEvent(val key: Int): ICancellable by Cancellable()