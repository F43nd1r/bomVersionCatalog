package com.faendir.gradle

data class Bom(
    val parent: Dependency?,
    val groupId: String?,
    val version: String?,
    val properties: Map<String, String> = emptyMap(),
    val dependencyManagement: DependencyManagement?
)

data class DependencyManagement(val dependencies: List<Dependency>)

data class Dependency(val groupId: String, val artifactId: String, val version: String, val type: String? = null, val scope: String? = null)