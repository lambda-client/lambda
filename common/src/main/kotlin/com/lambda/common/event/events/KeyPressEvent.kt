package com.lambda.common.event.events

import com.lambda.common.event.Cancellable
import com.lambda.common.event.ICancellable

class KeyPressEvent(val key: Int): ICancellable by Cancellable()
