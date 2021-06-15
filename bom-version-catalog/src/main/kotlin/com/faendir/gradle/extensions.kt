package com.faendir.gradle

import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.model.ObjectFactory


fun MutableVersionCatalogContainer.createWithBomSupport(name: String, configure: BomVersionCatalogBuilder.() -> Unit = {}) =
    add(createBomCatalogBuilder(name).apply { configure() })

private fun MutableVersionCatalogContainer.createBomCatalogBuilder(name: String): BomVersionCatalogBuilder {
    return objects.newInstance(
        BomVersionCatalogBuilder::class.java,
        name,
        objects,
        providers,
        dependencyResolutionServices,
        this
    )

}

private val MutableVersionCatalogContainer.objects: ObjectFactory
    get() = accessField("objects")
private val MutableVersionCatalogContainer.providers: Any
    get() = accessField("providers")
private val MutableVersionCatalogContainer.dependencyResolutionServices: Any
    get() = accessField("dependencyResolutionServices")

private fun <T> MutableVersionCatalogContainer.accessField(name: String): T {
    return this.javaClass.superclass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T
}