package com.faendir.gradle

import net.pearx.kasechange.toCamelCase
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.catalog.DefaultVersionCatalog
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.management.VersionCatalogBuilderInternal
import java.util.function.Supplier
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class BomVersionCatalogBuilder @Inject constructor(
    objects: ObjectFactory,
    providers: ProviderFactory,
    dependencyResolutionServicesSupplier: Supplier<DependencyResolutionServices>,
    private val delegate: VersionCatalogBuilderInternal
) : VersionCatalogBuilderInternal by delegate {
    private val imports: MutableList<Import> = mutableListOf()
    private val existingAliases = mutableListOf<String>()
    private val bomDownloader = BomDownloader(delegate.name, objects, providers, dependencyResolutionServicesSupplier, delegate::withContext)

    override fun build(): DefaultVersionCatalog {
        maybeImportBomCatalogs()
        return delegate.build()
    }

    open fun fromBom(dependencyNotation: Any) {
        imports.add(Import(dependencyNotation))
    }

    private fun maybeImportBomCatalogs() {
        if (imports.isEmpty()) {
            return
        }
        bundle("bom",
            imports.map { import ->
                val dependency = bomDownloader.download(import.notation, this::processBom)
                addDependency(Dependency(dependency.group ?: "", dependency.name, dependency.version ?: ""), dependency.version ?: "")
            })
    }

    private fun processBom(bom: Bom) {
        bom.parent?.let { bomDownloader.download(it, this::processBom) }
        bom.properties.forEach { (name, value) -> version(name.toVersionName(), value) }
        bom.dependencyManagement?.dependencies?.forEach { dep ->
            val dependency = dep.copy(groupId = eval(dep.groupId, { if (it == "project.groupId") bom.groupId ?: "" else bom.properties.getValue(it) }, { it }))
            if (dependency.scope == "import" && dependency.type == "pom") {
                bomDownloader.download(dependency.copy(version = eval(dependency.version, { bom.properties.getValue(it) }, { it })), this::processBom)
            }
            addDependency(dependency, bom.version ?: "")
        }
    }

    private fun addDependency(dependency: Dependency, projectVersion: String): String {
        val alias: String = dependency.groupId.toCamelCase() + "-" + dependency.artifactId.toCamelCase() +
                if (dependency.artifactId.endsWithAny("bundle", "bundles", "version", "versions", ignoreCase = true)) "0" else ""
        if (!existingAliases.contains(alias)) {
            existingAliases.add(alias)
            val dep = alias(alias).to(dependency.groupId, dependency.artifactId)
            eval(dependency.version, { if (it == "project.version") dep.version(projectVersion) else dep.versionRef(it.toVersionName()) }, { dep.version(it) })
        }
        return alias
    }

    private fun String.endsWithAny(vararg suffixes: String, ignoreCase: Boolean = false) = suffixes.any { endsWith(it, ignoreCase) }

    private fun <T> eval(value: String, processRef: (String) -> T, processDirect: (String) -> T): T {
        val match = Regex("\\$\\{(.*)}").matchEntire(value)
        return if (match != null) {
            processRef(match.groupValues[1])
        } else {
            processDirect(value)
        }
    }

    private fun String.toVersionName() = removeSuffix(".version").toCamelCase()

    private class Import(val notation: Any)
}