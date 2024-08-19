package com.lambda.core.annotations

import kotlin.annotation.AnnotationTarget.*

@RequiresOptIn(
    message = "Only use if you know what you are doing, no support will be provided whatsoever, use at your own risk",
)
@Target(CLASS, FUNCTION, PROPERTY, ANNOTATION_CLASS, CONSTRUCTOR, PROPERTY_SETTER, PROPERTY_GETTER, TYPEALIAS)
internal annotation class InternalApi
