package com.faendir.gradle

private val interpolationRegex = Regex("\\$\\{([^}]+)}")

interface PropertyEvaluator {
    fun evaluate(property: String): Property
    fun evaluateRecursively(property: String): String
    fun evaluateRecursively(property: Property): String
}

object NoContextPropertyEvaluator : PropertyEvaluator {
    override fun evaluate(property: String): Property {
        return Property.Literal(property)
    }

    override fun evaluateRecursively(property: String): String {
        return property
    }

    override fun evaluateRecursively(property: Property): String {
        return when (property) {
            is Property.Interpolation -> property.expression
            is Property.Mixed -> property.parts.joinToString("") { evaluateRecursively(it) }
            is Property.Literal -> property.value
        }
    }
}

class BomPropertyEvaluator(private val bom: Bom, propertiesFromParents: Map<String, String>) : PropertyEvaluator {
    private val allProperties = propertiesFromParents + bom.properties
    override fun evaluate(property: String): Property {
        val result = mutableListOf<Property>()
        var currentEnd = 0
        for (match in interpolationRegex.findAll(property)) {
            if (match.range.first > currentEnd) {
                result.add(Property.Literal(property.substring(currentEnd, match.range.first)))
            }
            val expression = match.groupValues[1]
            result.add(
                when (expression) {
                    "project.groupId" -> Property.Literal(bom.groupId ?: "")
                    "project.version" -> Property.Literal(bom.version ?: "")
                    else -> Property.Interpolation(expression)
                }
            )
            currentEnd = match.range.last + 1
        }
        if (currentEnd < property.length) {
            result.add(Property.Literal(property.substring(currentEnd)))
        }
        return when (result.size) {
            0 -> Property.Literal("")
            1 -> result.first()
            else -> Property.Mixed(result)
        }
    }

    override fun evaluateRecursively(property: String): String {
        return evaluateRecursively(evaluate(property))
    }

    override fun evaluateRecursively(property: Property): String {
        return when (property) {
            is Property.Interpolation -> evaluateRecursively(allProperties[property.expression] ?: "")
            is Property.Mixed -> property.parts.joinToString("") { evaluateRecursively(it) }
            is Property.Literal -> property.value
        }
    }
}

sealed interface Property {
    class Interpolation(val expression: String) : Property
    class Mixed(val parts: List<Property>) : Property
    class Literal(val value: String) : Property
}