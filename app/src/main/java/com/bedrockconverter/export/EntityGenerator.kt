// app/src/main/java/com/bedrockconverter/export/EntityGenerator.kt
package com.bedrockconverter.export

import com.bedrockconverter.model.*

/**
 * Generates Minecraft Bedrock entity definitions
 * Creates both client-side (resource pack) and server-side (behavior pack) entity files
 */
class EntityGenerator {

    /**
     * Generate all entity-related files
     */
    fun generate(
        settings: ExportSettings,
        collisionWidth: Float,
        collisionHeight: Float
    ): EntityGeneratorResult {
        val serverEntity = generateServerEntity(settings, collisionWidth, collisionHeight)
        val clientEntity = generateClientEntity(settings)
        val renderController = generateRenderController(settings)

        return EntityGeneratorResult(
            serverEntity = serverEntity,
            clientEntity = clientEntity,
            renderController = renderController,
            serverEntityJson = generateServerEntityJson(serverEntity),
            clientEntityJson = generateClientEntityJson(clientEntity, settings),
            renderControllerJson = generateRenderControllerJson(renderController),
            geometryJson = "" // Will be set by GeometryGenerator
        )
    }

    /**
     * Generate server-side entity definition (behavior pack)
     */
    private fun generateServerEntity(
        settings: ExportSettings,
        collisionWidth: Float,
        collisionHeight: Float
    ): ServerEntity {
        val components = EntityComponents(
            health = HealthComponent(value = 20, max = 20),
            physics = PhysicsComponent(
                hasCollision = true,
                hasGravity = settings.enableGravity
            ),
            collisionBox = CollisionBoxComponent(
                width = collisionWidth,
                height = collisionHeight
            ),
            pushable = PushableComponent(
                isPushable = false,
                isPushableByPiston = true
            ),
            typeFamily = TypeFamilyComponent(
                family = listOf(settings.namespace, "custom_model", "inanimate")
            ),
            scale = settings.scale
        )

        return ServerEntity(
            namespace = settings.namespace,
            name = settings.entityName,
            isSpawnable = true,
            isSummonable = true,
            isExperimental = false,
            components = components
        )
    }

    /**
     * Generate client-side entity definition (resource pack)
     */
    private fun generateClientEntity(settings: ExportSettings): ClientEntity {
        val texturePath = "textures/entity/${settings.entityName}/default"

        return ClientEntity(
            identifier = settings.fullIdentifier,
            materials = mapOf("default" to "entity_alphatest"),
            textures = mapOf("default" to texturePath),
            geometry = mapOf("default" to settings.geometryIdentifier),
            renderControllers = listOf(settings.renderControllerIdentifier),
            spawnEgg = if (settings.includeSpawnEgg) {
                SpawnEgg(
                    baseColor = settings.spawnEggBaseColor,
                    overlayColor = settings.spawnEggOverlayColor
                )
            } else null
        )
    }

    /**
     * Generate render controller
     */
    private fun generateRenderController(settings: ExportSettings): RenderController {
        return RenderController(
            identifier = settings.renderControllerIdentifier,
            geometryField = "Geometry.default",
            materialsField = listOf(mapOf("*" to "Material.default")),
            texturesField = listOf("Texture.default")
        )
    }

    /**
     * Generate server entity JSON
     */
    private fun generateServerEntityJson(entity: ServerEntity): String {
        return buildString {
            append("{\n")
            append("  \"format_version\": \"${entity.formatVersion}\",\n")
            append("  \"minecraft:entity\": {\n")
            append("    \"description\": {\n")
            append("      \"identifier\": \"${entity.identifier}\",\n")
            append("      \"is_spawnable\": ${entity.isSpawnable},\n")
            append("      \"is_summonable\": ${entity.isSummonable},\n")
            append("      \"is_experimental\": ${entity.isExperimental}\n")
            append("    },\n")
            append("    \"components\": {\n")

            val components = mutableListOf<String>()

            // Health
            entity.components.health?.let {
                components.add("""      "minecraft:health": {
        "value": ${it.value},
        "max": ${it.max}
      }""")
            }

            // Physics
            entity.components.physics?.let {
                components.add("""      "minecraft:physics": {
        "has_collision": ${it.hasCollision},
        "has_gravity": ${it.hasGravity}
      }""")
            }

            // Collision box
            entity.components.collisionBox?.let {
                components.add("""      "minecraft:collision_box": {
        "width": ${it.width},
        "height": ${it.height}
      }""")
            }

            // Pushable
            entity.components.pushable?.let {
                components.add("""      "minecraft:pushable": {
        "is_pushable": ${it.isPushable},
        "is_pushable_by_piston": ${it.isPushableByPiston}
      }""")
            }

            // Type family
            entity.components.typeFamily?.let {
                val familyList = it.family.joinToString(", ") { f -> "\"$f\"" }
                components.add("""      "minecraft:type_family": {
        "family": [$familyList]
      }""")
            }

            // Scale
            if (entity.components.scale != 1f) {
                components.add("""      "minecraft:scale": {
        "value": ${entity.components.scale}
      }""")
            }

            // Damage sensor (make it invulnerable)
            components.add("""      "minecraft:damage_sensor": {
        "triggers": {
          "cause": "all",
          "deals_damage": false
        }
      }""")

            // Movement (none - static entity)
            components.add("""      "minecraft:movement": {
        "value": 0
      }""")

            // No knockback
            components.add("""      "minecraft:knockback_resistance": {
        "value": 1
      }""")

            append(components.joinToString(",\n"))
            append("\n    }\n")
            append("  }\n")
            append("}")
        }
    }

    /**
     * Generate client entity JSON
     */
    private fun generateClientEntityJson(entity: ClientEntity, settings: ExportSettings): String {
        return buildString {
            append("{\n")
            append("  \"format_version\": \"${entity.formatVersion}\",\n")
            append("  \"minecraft:client_entity\": {\n")
            append("    \"description\": {\n")
            append("      \"identifier\": \"${entity.identifier}\",\n")
            append("      \"materials\": {\n")

            val materials = entity.materials.entries.joinToString(",\n") { (key, value) ->
                "        \"$key\": \"$value\""
            }
            append(materials)
            append("\n      },\n")

            append("      \"textures\": {\n")
            val textures = entity.textures.entries.joinToString(",\n") { (key, value) ->
                "        \"$key\": \"$value\""
            }
            append(textures)
            append("\n      },\n")

            append("      \"geometry\": {\n")
            val geometry = entity.geometry.entries.joinToString(",\n") { (key, value) ->
                "        \"$key\": \"$value\""
            }
            append(geometry)
            append("\n      },\n")

            append("      \"render_controllers\": [\n")
            val controllers = entity.renderControllers.joinToString(",\n") { "        \"$it\"" }
            append(controllers)
            append("\n      ]")

            // Spawn egg
            entity.spawnEgg?.let { egg ->
                append(",\n      \"spawn_egg\": {\n")
                append("        \"base_color\": \"${egg.baseColor}\",\n")
                append("        \"overlay_color\": \"${egg.overlayColor}\"\n")
                append("      }")
            }

            append("\n    }\n")
            append("  }\n")
            append("}")
        }
    }

    /**
     * Generate render controller JSON
     */
    private fun generateRenderControllerJson(controller: RenderController): String {
        return buildString {
            append("{\n")
            append("  \"format_version\": \"${controller.formatVersion}\",\n")
            append("  \"render_controllers\": {\n")
            append("    \"${controller.identifier}\": {\n")
            append("      \"geometry\": \"${controller.geometryField}\",\n")
            append("      \"materials\": [\n")

            val materials = controller.materialsField.joinToString(",\n") { matMap ->
                val entries = matMap.entries.joinToString(", ") { (key, value) ->
                    "\"$key\": \"$value\""
                }
                "        { $entries }"
            }
            append(materials)
            append("\n      ],\n")

            append("      \"textures\": [\n")
            val textures = controller.texturesField.joinToString(",\n") { "        \"$it\"" }
            append(textures)
            append("\n      ]\n")

            append("    }\n")
            append("  }\n")
            append("}")
        }
    }
}

/**
 * Result of entity generation
 */
data class EntityGeneratorResult(
    val serverEntity: ServerEntity,
    val clientEntity: ClientEntity,
    val renderController: RenderController,
    val serverEntityJson: String,
    val clientEntityJson: String,
    val renderControllerJson: String,
    var geometryJson: String
)