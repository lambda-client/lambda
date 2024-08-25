package com.lambda.util.reflections

import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import java.net.URL

@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@DslMarker
annotation class ReflectionsDsl

/**
 * A DSL class to configure the Reflections library for runtime scanning.
 * This class allows you to specify packages, scanners, URLs, filters, class loaders, and other options
 * to build a Reflections configuration.
 */
@ReflectionsDsl
class ReflectionConfigDsl {
    private val packages = mutableListOf<String>()
    private val scanners = mutableListOf<Scanners>()
    private val urls = mutableListOf<URL>()
    private var inputsFilter: (String) -> Boolean = { true }
    private val classLoaders = mutableListOf<ClassLoader>()
    private var parallel = false
    private var shouldExpandSuperTypes = true

    /**
     * Specifies the packages to be scanned.
     *
     * @param packages A vararg of package names to be included in the scanning process.
     * @return The current instance of [ReflectionConfigDsl] for method chaining.
     */
    fun forPackages(vararg packages: String): ReflectionConfigDsl {
        this.packages += packages
        return this
    }

    /**
     * Adds scanners to the configuration.
     *
     * @param scanners A vararg of [Scanners] to be used for scanning classes, methods, etc.
     * @return The current instance of [ReflectionConfigDsl] for method chaining.
     */
    fun addScanners(vararg scanners: Scanners): ReflectionConfigDsl {
        this.scanners += scanners
        return this
    }

    /**
     * Adds URLs to the configuration.
     *
     * @param urls A vararg of [URL] to be included in the scanning process.
     * @return The current instance of [ReflectionConfigDsl] for method chaining.
     */
    fun addUrls(vararg urls: URL): ReflectionConfigDsl {
        this.urls += urls
        return this
    }

    /**
     * Sets a filter for input names.
     *
     * @param filter A lambda function that takes a [String] and returns a [Boolean], indicating whether the input should be included.
     * @return The current instance of [ReflectionConfigDsl] for method chaining.
     */
    fun filterInputsBy(filter: (String) -> Boolean): ReflectionConfigDsl {
        inputsFilter = filter
        return this
    }

    /**
     * Adds class loaders to the configuration.
     *
     * @param classLoaders A vararg of [ClassLoader] to be used in the scanning process.
     * @return The current instance of [ReflectionConfigDsl] for method chaining.
     */
    fun addClassLoaders(vararg classLoaders: ClassLoader): ReflectionConfigDsl {
        this.classLoaders += classLoaders
        return this
    }

    /**
     * Enables parallel scanning.
     *
     * @return The current instance of [ReflectionConfigDsl] for method chaining.
     */
    fun parallel(): ReflectionConfigDsl {
        parallel = true
        return this
    }

    /**
     * Sets whether to expand super types during scanning.
     *
     * @param value A [Boolean] indicating whether to expand super types.
     * @return The current instance of [ReflectionConfigDsl] for method chaining.
     */
    fun expandSuperTypes(value: Boolean): ReflectionConfigDsl {
        shouldExpandSuperTypes = value
        return this
    }

    /**
     * Builds a [ConfigurationBuilder] based on the current configuration.
     *
     * @return A [ConfigurationBuilder] configured with the specified packages, scanners, URLs, filters, and class loaders.
     */
    fun build(): ConfigurationBuilder = ConfigurationBuilder()
        .forPackages(*packages.toTypedArray())
        .addUrls(*urls.toTypedArray())
        .filterInputsBy(inputsFilter)
        .addClassLoaders(*classLoaders.toTypedArray())
        .setExpandSuperTypes(shouldExpandSuperTypes)
}

/**
 * Configures and retrieves a [ConfigurationBuilder]
 *
 * @param block A DSL block used to configure the [ReflectionConfigDsl].
 * @return A [ConfigurationBuilder] configured with the specified options.
 */
inline fun getConfiguration(block: (@ReflectionsDsl ReflectionConfigDsl).() -> Unit): ConfigurationBuilder =
    ReflectionConfigDsl().apply(block).build()
