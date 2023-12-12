package com.faendir.gradle

import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.model.ObjectFactory


fun MutableVersionCatalogContainer.createWithBomSupport(name: String, configure: BomVersionCatalogBuilder.() -> Unit = {}) =
    add(createBomCatalogBuilder(name).apply { configure() })

private fun MutableVersionCatalogContainer.createBomCatalogBuilder(name: String): BomVersionCatalogBuilder {
    return objects.newInstance(
        BomVersionCatalogBuilder::class.java,
        name,
        dependencyResolutionServices,
        this
    )
}

private val MutableVersionCatalogContainer.objects: ObjectFactory
    get() = accessField("objects")
private val MutableVersionCatalogContainer.dependencyResolutionServices: Any
    get() = accessField("dependencyResolutionServices")

private fun <T> MutableVersionCatalogContainer.accessField(name: String): T {
    return this.javaClass.superclass.getDeclaredField(name).apply { isAccessible = true }.get(this) as T
}