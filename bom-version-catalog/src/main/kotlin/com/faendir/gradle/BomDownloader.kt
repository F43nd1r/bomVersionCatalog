package com.faendir.gradle

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.attributes.Category
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.model.ObjectFactory
import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.FileUtils
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.util.function.Supplier

class BomDownloader(private val name: String,
                    private val objects: ObjectFactory,
                    private val dependencyResolutionServicesSupplier: Supplier<DependencyResolutionServices>,
                    private val withContext: (String, Runnable) -> Unit) {
    private var count = 0
    private val drs by lazy { dependencyResolutionServicesSupplier.get() }
    private val xml = XmlMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).registerModule(KotlinModule.Builder().build())

    fun download(dependency: com.faendir.gradle.Dependency, process: (Bom) -> Unit) : Dependency {
        return download("${dependency.groupId}:${dependency.artifactId}:${dependency.version}", process)
    }

    fun download(dependencyNotation: Any, process: (Bom) -> Unit): Dependency {
        val cnf = createResolvableConfiguration(drs)
        val dependency = drs.dependencyHandler.create(dependencyNotation)
        if (dependency is ExternalDependency) {
            dependency.addArtifact(DefaultDependencyArtifact(dependency.getName(), "pom", "pom", null, null))
        }
        cnf.dependencies.add(dependency)
        cnf.incoming.artifacts.forEach {
            withContext("bom " + it.variant.owner) { process(loadBom(it.file)) }
        }
        return dependency
    }

    private fun loadBom(modelFile: File): Bom {
        if (!FileUtils.hasExtensionIgnoresCase(modelFile.name, "pom")) {
            throw IllegalArgumentException("File ${modelFile.name} isn't supported")
        }
        if (!modelFile.exists()) {
            throw IllegalStateException("Import of external catalog file failed because File '$modelFile' doesn't exist")
        }
        return try {
            xml.readValue(modelFile.bufferedReader(), Bom::class.java)
        } catch (e: IOException) {
            throw UncheckedIOException(e)
        }
    }

    private fun createResolvableConfiguration(drs: DependencyResolutionServices): Configuration {
        val cnf = drs.configurationContainer.create("incomingBomFor${name.capitalized()}${count++}")
        cnf.resolutionStrategy.activateDependencyLocking()
        cnf.attributes { it.attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.REGULAR_PLATFORM)) }
        cnf.isCanBeResolved = true
        cnf.isCanBeConsumed = false
        return cnf
    }
}