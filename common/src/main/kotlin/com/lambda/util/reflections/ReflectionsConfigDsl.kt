/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
 *
 * Important!!
 * Be aware that using Reflections can lead to unpredictable and hard-to-debug issues.
 * Furthermore, the library we're relying on is outdated and no longer maintained.
 * For more reliable methods, refer to Reflections.kt.
 */
@ReflectionsDsl
class ReflectionConfigDsl {
    private val packages = mutableListOf<String>()
    private val scanners = mutableListOf<Scanners>()
    private val urls = mutableListOf<URL>()
    private val classLoaders = mutableListOf<ClassLoader>()

    private var shouldExpandSuperTypes = true

    /**
     * Specifies the packages to be scanned.
     *
     * @param packages A vararg of package names to be included in the scanning process.
     */
    fun forPackages(vararg packages: String) {
        this.packages += packages
    }

    /**
     * Adds scanners to the configuration.
     *
     * @param scanners A vararg of [Scanners] to be used for scanning classes, methods, etc.
     */
    fun addScanners(vararg scanners: Scanners) {
        this.scanners += scanners
    }

    /**
     * Adds URLs to the configuration.
     *
     * @param urls A vararg of [URL] to be included in the scanning process.
     */
    fun addUrls(vararg urls: URL) {
        this.urls += urls
    }

    /**
     * Adds class loaders to the configuration.
     *
     * @param classLoaders A vararg of [ClassLoader] to be used in the scanning process.
     */
    fun addClassLoaders(vararg classLoaders: ClassLoader) {
        this.classLoaders += classLoaders
    }

    /**
     * Expand super types during the scan
     */
    fun expandSuperTypes(shouldExpand: Boolean) {
        shouldExpandSuperTypes = shouldExpand
    }

    /**
     * Builds a [ConfigurationBuilder] based on the current configuration.
     */
    fun build(): ConfigurationBuilder = ConfigurationBuilder()
        .forPackages(*packages.toTypedArray())
        .addUrls(*urls.toTypedArray())
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
