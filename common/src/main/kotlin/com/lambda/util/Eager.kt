package com.lambda.util

/**
 * The [Eager] annotation is used to mark classes that should be initialized eagerly at startup.
 *
 * This annotation is typically used with object declarations in Kotlin,
 * which are lazily initialized by default.
 * By annotating an object with `@Eager`, you indicate that the object
 * should be initialized as soon as the program starts, rather than waiting until the object is first accessed.
 *
 * This annotation is also used to mark abstract classes whose subclasses should all be initialized eagerly.
 * When an abstract class is annotated with `@Eager`, all its subclasses are also initialized eagerly.
 *
 * Note: This annotation requires the use of reflection to find and initialize the annotated classes.
 * Therefore, it should be used judiciously to avoid potential performance issues.
 */
@Target(AnnotationTarget.CLASS)
annotation class Eager