// app/src/main/java/com/bedrockconverter/export/ManifestGenerator.kt
package com.bedrockconverter.export

import com.bedrockconverter.model.ExportSettings
import com.bedrockconverter.model.PackDependency
import com.bedrockconverter.model.PackManifest
import com.bedrockconverter.model.PackType
import java.util.UUID

/**
 * Generates manifest.json files for resource and behavior packs
 */
class ManifestGenerator {

    /**
     * Generate manifests for both packs
     */
    fun generate(settings: ExportSettings): ManifestGeneratorResult {
        val resourcePackUuid = UUID.randomUUID().toString()
        val behaviorPackUuid = UUID.randomUUID().toString()

        val resourcePackManifest = generateResourcePackManifest(
            settings = settings,
            uuid = resourcePackUuid,
            behaviorPackUuid = behaviorPackUuid
        )

        val behaviorPackManifest = generateBehaviorPackManifest(
            settings = settings,
            uuid = behaviorPackUuid,
            resourcePackUuid = resourcePackUuid
        )

        return ManifestGeneratorResult(
            resourcePackUuid = resourcePackUuid,
            behaviorPackUuid = behaviorPackUuid,
            resourcePackManifest = resourcePackManifest,
            behaviorPackManifest = behaviorPackManifest,
            resourcePackManifestJson = generateManifestJson(resourcePackManifest),
            behaviorPackManifestJson = generateManifestJson(behaviorPackManifest)
        )
    }

    /**
     * Generate resource pack manifest
     */
    private fun generateResourcePackManifest(
        settings: ExportSettings,
        uuid: String,
        behaviorPackUuid: String
    ): PackManifest {
        return PackManifest(
            uuid = uuid,
            name = "${settings.actualPackName} Resources",
            description = "${settings.actualPackDescription} - Resource Pack",
            version = listOf(1, 0, 0),
            minEngineVersion = listOf(1, 20, 0),
            type = PackType.RESOURCES,
            dependencies = listOf(
                PackDependency(
                    uuid = behaviorPackUuid,
                    version = listOf(1, 0, 0)
                )
            )
        )
    }

    /**
     * Generate behavior pack manifest
     */
    private fun generateBehaviorPackManifest(
        settings: ExportSettings,
        uuid: String,
        resourcePackUuid: String
    ): PackManifest {
        return PackManifest(
            uuid = uuid,
            name = "${settings.actualPackName} Behaviors",
            description = "${settings.actualPackDescription} - Behavior Pack",
            version = listOf(1, 0, 0),
            minEngineVersion = listOf(1, 20, 0),
            type = PackType.DATA,
            dependencies = listOf(
                PackDependency(
                    uuid = resourcePackUuid,
                    version = listOf(1, 0, 0)
                )
            )
        )
    }

    /**
     * Generate manifest JSON string
     */
    private fun generateManifestJson(manifest: PackManifest): String {
        return buildString {
            append("{\n")
            append("  \"format_version\": ${manifest.formatVersion},\n")
            append("  \"header\": {\n")
            append("    \"name\": \"${escapeJson(manifest.name)}\",\n")
            append("    \"description\": \"${escapeJson(manifest.description)}\",\n")
            append("    \"uuid\": \"${manifest.uuid}\",\n")
            append("    \"version\": [${manifest.version.joinToString(", ")}],\n")
            append("    \"min_engine_version\": [${manifest.minEngineVersion.joinToString(", ")}]\n")
            append("  },\n")
            append("  \"modules\": [\n")
            append("    {\n")
            append("      \"type\": \"${manifest.type.value}\",\n")
            append("      \"uuid\": \"${UUID.randomUUID()}\",\n")
            append("      \"version\": [${manifest.version.joinToString(", ")}]\n")
            append("    }\n")
            append("  ]")

            if (manifest.dependencies.isNotEmpty()) {
                append(",\n")
                append("  \"dependencies\": [\n")
                
                val deps = manifest.dependencies.mapIndexed { index, dep ->
                    buildString {
                        append("    {\n")
                        append("      \"uuid\": \"${dep.uuid}\",\n")
                        append("      \"version\": [${dep.version.joinToString(", ")}]\n")
                        append("    }")
                    }
                }
                
                append(deps.joinToString(",\n"))
                append("\n  ]")
            }

            append("\n}")
        }
    }

    /**
     * Escape special characters for JSON
     */
    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * Result of manifest generation
 */
data class ManifestGeneratorResult(
    val resourcePackUuid: String,
    val behaviorPackUuid: String,
    val resourcePackManifest: PackManifest,
    val behaviorPackManifest: PackManifest,
    val resourcePackManifestJson: String,
    val behaviorPackManifestJson: String
)