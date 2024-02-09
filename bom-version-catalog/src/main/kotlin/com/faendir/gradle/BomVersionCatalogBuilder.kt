@file:Suppress("UnstableApiUsage")

package com.faendir.gradle

import com.faendir.gradle.Property.*
import net.pearx.kasechange.toCamelCase
import org.gradle.api.Action
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.initialization.dsl.VersionCatalogBuilder
import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.catalog.DefaultVersionCatalog
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.internal.management.VersionCatalogBuilderInternal
import java.util.function.Supplier
import javax.inject.Inject

open class BomVersionCatalogBuilder @Inject constructor(
    private val name: String,
    dependencyResolutionServicesSupplier: Supplier<DependencyResolutionServices>,
    private val container: MutableVersionCatalogContainer,
    objects: ObjectFactory,
) : VersionCatalogBuilderInternal {
    private val delegate = container.create(name).also { container.remove(it) } as VersionCatalogBuilderInternal
    private val imports: MutableList<Import> = mutableListOf()
    private val existingAliases = mutableListOf<String>()
    private val bomDownloader = BomDownloader(delegate.name, objects, dependencyResolutionServicesSupplier, delegate::withContext)
    override fun getName(): String = delegate.name

    override fun getDescription(): Property<String> = delegate.description

    override fun from(dependencyNotation: Any) = delegate.from(dependencyNotation)

    override fun version(name: String, versionSpec: Action<in MutableVersionConstraint>): String = delegate.version(name, versionSpec)

    override fun version(name: String, version: String): String = delegate.version(name, version)

    override fun library(alias: String, group: String, artifact: String): VersionCatalogBuilder.LibraryAliasBuilder = delegate.library(alias, group, artifact)

    override fun library(alias: String, groupArtifactVersion: String) = delegate.library(alias, groupArtifactVersion)

    override fun plugin(alias: String, id: String): VersionCatalogBuilder.PluginAliasBuilder = delegate.plugin(alias, id)

    override fun bundle(name: String, aliases: MutableList<String>) = delegate.bundle(name, aliases)

    override fun getLibrariesExtensionName(): String = delegate.librariesExtensionName

    override fun build(): DefaultVersionCatalog {
        container.remove(this)
        val intermediateBuilder = container.create(name).also { container.remove(it) } as VersionCatalogBuilderInternal
        container.add(this)
        val catalog = delegate.build()
        catalog.versionAliases.forEach { alias -> intermediateBuilder.version(alias) { catalog.getVersion(alias).version.copyTo(it) } }
        catalog.libraryAliases.forEach { alias ->
            val library = catalog.getDependencyData(alias)
            intermediateBuilder.library(alias, library.group, library.name).apply {
                library.versionRef?.let { versionRef(it) } ?: version { library.version.copyTo(it) }
            }
        }
        catalog.pluginAliases.forEach { alias ->
            val plugin = catalog.getPlugin(alias)
            intermediateBuilder.plugin(alias, catalog.getPlugin(alias).id).apply {
                plugin.versionRef?.let { versionRef(it) } ?: version { plugin.version.copyTo(it) }
            }
        }
        catalog.bundleAliases.forEach { alias -> intermediateBuilder.bundle(alias, catalog.getBundle(alias).components) }
        maybeImportBomCatalogs(catalog, intermediateBuilder)
        return intermediateBuilder.build()
    }

    override fun withContext(context: String?, action: Runnable?) = delegate.withContext(context, action)

    open fun fromBom(dependencyNotation: Any) {
        imports.add(DirectImport(dependencyNotation))
    }

    open fun fromBomAlias(alias: String) {
        imports.add(AliasImport(alias))
    }

    private fun maybeImportBomCatalogs(catalog: DefaultVersionCatalog, builder: VersionCatalogBuilderInternal) {
        if (imports.isEmpty()) {
            return
        }
        builder.bundle("bom",
            imports.map { import ->
                val dependency = bomDownloader.download(import.getNotation(catalog)) { processBom(builder, it) }
                builder.addDependency(Dependency(dependency.group ?: "", dependency.name, dependency.version ?: ""), NoContextPropertyEvaluator)
            })
    }

    private fun processBom(builder: VersionCatalogBuilderInternal, bom: Bom): Map<String, String> {
        val allProperties = mutableMapOf<String, String>()
        bom.parent?.let { parent -> bomDownloader.download(parent) { allProperties.putAll(processBom(builder, it)) } }
        val propertyEvaluator = BomPropertyEvaluator(bom, allProperties)
        val bomProperties = bom.properties.mapValues { (_, value) -> propertyEvaluator.evaluateRecursively(value) }
        bomProperties.forEach { (name, value) -> builder.version(name.toVersionName(), value) }
        allProperties.putAll(bomProperties)

        bom.dependencyManagement?.dependencies?.forEach { dep ->
            val dependency = dep.copy(groupId = propertyEvaluator.evaluateRecursively(dep.groupId))
            if (dependency.scope == "import" && dependency.type == "pom") {
                val version = propertyEvaluator.evaluateRecursively(dependency.version)
                if (version.isNotBlank()) {
                    bomDownloader.download(dependency.copy(version = version)) { processBom(builder, it) }
                }
            }
            builder.addDependency(dependency, propertyEvaluator)
        }
        return allProperties
    }

    private fun VersionCatalogBuilderInternal.addDependency(dependency: Dependency, propertyEvaluator: PropertyEvaluator): String {
        val alias: String = dependency.groupId.toCamelCase() + "-" + dependency.artifactId.toCamelCase()
        if (!existingAliases.contains(alias)) {
            existingAliases.add(alias)
            val library = library(alias, dependency.groupId, dependency.artifactId)
            when (val version = propertyEvaluator.evaluate(dependency.version)) {
                is Interpolation -> library.versionRef(version.expression.toVersionName())
                is Literal -> library.version(version.value)
                is Mixed -> library.version(propertyEvaluator.evaluateRecursively(version))
            }
        }
        return alias
    }
}

private interface Import {
    fun getNotation(catalog: DefaultVersionCatalog): Any
}

private class DirectImport(val notation: Any) : Import {
    override fun getNotation(catalog: DefaultVersionCatalog): Any = notation

}

private class AliasImport(val alias: String) : Import {
    override fun getNotation(catalog: DefaultVersionCatalog): Any {
        val data = catalog.getDependencyData(alias) ?: throw IllegalArgumentException("Unknown bom alias $alias")
        return "${data.group}:${data.name}:${(data.versionRef?.let { catalog.getVersion(it).version } ?: data.version).requiredVersion}"
    }
}

private fun String.toVersionName() = removeSuffix(".version").toCamelCase()

private fun VersionConstraint.copyTo(receiver: MutableVersionConstraint) {
    branch?.takeIf { it.isNotEmpty() }?.let { receiver.branch = it }
    requiredVersion.takeIf { it.isNotEmpty() }?.let { receiver.require(it) }
    strictVersion.takeIf { it.isNotEmpty() }?.let { receiver.strictly(it) }
    preferredVersion.takeIf { it.isNotEmpty() }?.let { receiver.prefer(it) }
    receiver.reject(*rejectedVersions.toTypedArray())
}