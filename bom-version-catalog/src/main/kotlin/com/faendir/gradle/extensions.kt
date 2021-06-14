package com.faendir.gradle

import org.gradle.api.initialization.resolve.MutableVersionCatalogContainer
import org.gradle.api.model.ObjectFactory


fun MutableVersionCatalogContainer.createFromBom(name: String, vararg boms: Any, configure: BomVersionCatalogBuilder.() -> Unit = {}) =
    add(createBomCatalogBuilder(name).apply {
        boms.forEach { fromBom(it) }
        configure()
    })

private fun MutableVersionCatalogContainer.createBomCatalogBuilder(name: String) : BomVersionCatalogBuilder {
    val delegate = create(name)
    remove(delegate)
    return objects.newInstance(BomVersionCatalogBuilder::class.java, objects, providers, dependencyResolutionServices, delegate)
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