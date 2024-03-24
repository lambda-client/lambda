package com.lambda.threading

import kotlin.concurrent.thread

inline fun onShutdown(crossinline block: () -> Unit) {
    Runtime.getRuntime().addShutdownHook(thread(start = false) { block() })
}


