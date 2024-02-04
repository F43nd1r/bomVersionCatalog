@file:Suppress("UnstableApiUsage")

package com.faendir.gradle

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
    private val importPredicates: MutableList<(String) -> Boolean> = mutableListOf()
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
        importPredicates.forEach { predicate ->
            catalog.libraryAliases.filter(predicate).forEach(::fromBomAlias)
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

    open fun fromBomAliasesFilter(predicate: (String) -> Boolean) {
        importPredicates.add(predicate)
    }

    private fun maybeImportBomCatalogs(catalog: DefaultVersionCatalog, builder: VersionCatalogBuilderInternal) {
        if (imports.isEmpty()) {
            return
        }
        builder.bundle("bom",
            imports.map { import ->
                val dependency = bomDownloader.download(import.getNotation(catalog)) { processBom(builder, it) }
                builder.addDependency(Dependency(dependency.group ?: "", dependency.name, dependency.version ?: ""), dependency.version ?: "")
            })
    }

    private fun processBom(builder: VersionCatalogBuilderInternal, bom: Bom) {
        bom.parent?.let { parent -> bomDownloader.download(parent) { processBom(builder, it) } }
        bom.properties.forEach { (name, value) -> builder.version(name.toVersionName(), value) }
        bom.dependencyManagement?.dependencies?.forEach { dep ->
            val dependency = dep.copy(groupId = evalBomVersion(dep.groupId, { (if (it == "project.groupId") bom.groupId else bom.properties[it]) ?: "" }, { it }))
            if (dependency.scope == "import" && dependency.type == "pom") {
                val version = evalBomVersion(dependency.version, { bom.properties[it] }, { it })
                if (version != null) {
                    bomDownloader.download(dependency.copy(version = version)) { processBom(builder, it) }
                }
            }
            builder.addDependency(dependency, bom.version ?: "")
        }
    }

    private fun VersionCatalogBuilderInternal.addDependency(dependency: Dependency, projectVersion: String): String {
        val alias: String = dependency.groupId.toCamelCase() + "-" + dependency.artifactId.toCamelCase()
        if (!existingAliases.contains(alias)) {
            existingAliases.add(alias)
            val library = library(alias, dependency.groupId, dependency.artifactId)
            evalBomVersion(
                dependency.version,
                { if (it == "project.version") library.version(projectVersion) else library.versionRef(it.toVersionName()) },
                { library.version(it) })
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

private fun <T> evalBomVersion(value: String, processRef: (String) -> T, processDirect: (String) -> T): T {
    val match = Regex("\\$\\{(.*)}").matchEntire(value)
    return if (match != null) {
        processRef(match.groupValues[1])
    } else {
        processDirect(value)
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