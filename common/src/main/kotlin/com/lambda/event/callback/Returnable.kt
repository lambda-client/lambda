package com.lambda.event.callback

open class Returnable<T>(
    val defaultValue: T
) : IReturnable<T> {
    override var returnValue: T = defaultValue
}