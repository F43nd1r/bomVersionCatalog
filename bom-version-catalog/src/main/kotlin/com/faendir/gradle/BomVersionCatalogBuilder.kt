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
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.management.VersionCatalogBuilderInternal
import java.util.function.Supplier
import javax.inject.Inject

open class BomVersionCatalogBuilder @Inject constructor(
    private val name: String,
    objects: ObjectFactory,
    providers: ProviderFactory,
    dependencyResolutionServicesSupplier: Supplier<DependencyResolutionServices>,
    private val container: MutableVersionCatalogContainer
) : VersionCatalogBuilderInternal {
    private val delegate = container.create(name).also { container.remove(it) } as VersionCatalogBuilderInternal
    private val imports: MutableList<Import> = mutableListOf()
    private val existingAliases = mutableListOf<String>()
    private val bomDownloader = BomDownloader(delegate.name, objects, providers, dependencyResolutionServicesSupplier, delegate::withContext)
    override fun getName(): String = delegate.name

    override fun getDescription(): Property<String> = delegate.description

    override fun from(dependencyNotation: Any) = delegate.from(dependencyNotation)

    override fun version(name: String, versionSpec: Action<in MutableVersionConstraint>): String = delegate.version(name, versionSpec)

    override fun version(name: String, version: String): String = delegate.version(name, version)

    override fun alias(alias: String): VersionCatalogBuilder.AliasBuilder = delegate.alias(alias)

    override fun bundle(name: String, aliases: MutableList<String>) = delegate.bundle(name, aliases)

    override fun getLibrariesExtensionName(): String = delegate.librariesExtensionName

    override fun build(): DefaultVersionCatalog {
        container.remove(this)
        val intermediateBuilder = container.create(name).also { container.remove(it) } as VersionCatalogBuilderInternal
        container.add(this)
        val catalog = delegate.build()
        catalog.versionAliases.forEach { alias -> intermediateBuilder.version(alias) { catalog.getVersion(alias).version.copyTo(it) } }
        catalog.dependencyAliases.forEach { alias ->
            val dependency = catalog.getDependencyData(alias)
            intermediateBuilder.alias(alias).to(dependency.group, dependency.name).apply {
                if (dependency.versionRef != null) {
                    versionRef(dependency.versionRef!!)
                } else {
                    version { dependency.version.copyTo(it) }
                }
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
                builder.addDependency(Dependency(dependency.group ?: "", dependency.name, dependency.version ?: ""), dependency.version ?: "")
            })
    }

    private fun processBom(builder: VersionCatalogBuilderInternal, bom: Bom) {
        bom.parent?.let { parent -> bomDownloader.download(parent) { processBom(builder, it) } }
        bom.properties.forEach { (name, value) -> builder.version(name.toVersionName(), value) }
        bom.dependencyManagement?.dependencies?.forEach { dep ->
            val dependency = dep.copy(groupId = eval(dep.groupId, { if (it == "project.groupId") bom.groupId ?: "" else bom.properties.getValue(it) }, { it }))
            if (dependency.scope == "import" && dependency.type == "pom") {
                bomDownloader.download(dependency.copy(version = eval(dependency.version, { bom.properties.getValue(it) }, { it }))) { processBom(builder, it) }
            }
            builder.addDependency(dependency, bom.version ?: "")
        }
    }

    private fun VersionCatalogBuilderInternal.addDependency(dependency: Dependency, projectVersion: String): String {
        val alias: String = dependency.groupId.toCamelCase() + "-" + dependency.artifactId.toCamelCase() +
                if (dependency.artifactId.endsWithAny("bundle", "bundles", "version", "versions", "plugin", "plugins", ignoreCase = true)) "0" else ""
        if (!existingAliases.contains(alias)) {
            existingAliases.add(alias)
            val dep = alias(alias).to(dependency.groupId, dependency.artifactId)
            eval(dependency.version, { if (it == "project.version") dep.version(projectVersion) else dep.versionRef(it.toVersionName()) }, { dep.version(it) })
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

private fun <T> eval(value: String, processRef: (String) -> T, processDirect: (String) -> T): T {
    val match = Regex("\\$\\{(.*)}").matchEntire(value)
    return if (match != null) {
        processRef(match.groupValues[1])
    } else {
        processDirect(value)
    }
}

private fun String.endsWithAny(vararg suffixes: String, ignoreCase: Boolean = false) = suffixes.any { endsWith(it, ignoreCase) }

private fun String.toVersionName() = removeSuffix(".version").toCamelCase()

private fun VersionConstraint.copyTo(receiver: MutableVersionConstraint) {
    requiredVersion.takeIf { it.isNotEmpty() }?.let { receiver.require(it) }
    strictVersion.takeIf { it.isNotEmpty() }?.let { receiver.strictly(it) }
    preferredVersion.takeIf { it.isNotEmpty() }?.let { receiver.prefer(it) }
    receiver.reject(*rejectedVersions.toTypedArray())
}