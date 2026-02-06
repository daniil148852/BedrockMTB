// app/src/main/java/com/bedrockconverter/model/BedrockAddon.kt
package com.bedrockconverter.model

import java.util.UUID

/**
 * Complete Bedrock Addon structure
 */
data class BedrockAddon(
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val version: List<Int> = listOf(1, 0, 0),
    val minEngineVersion: List<Int> = listOf(1, 20, 0),
    val resourcePack: ResourcePack,
    val behaviorPack: BehaviorPack
) {
    val entityIdentifier: String
        get() = "${behaviorPack.entity.namespace}:${behaviorPack.entity.name}"
}

/**
 * Resource Pack structure
 */
data class ResourcePack(
    val uuid: String = UUID.randomUUID().toString(),
    val manifest: PackManifest,
    val entity: ClientEntity,
    val geometry: BedrockGeometry,
    val renderController: RenderController,
    val textures: List<Texture> = emptyList()
)

/**
 * Behavior Pack structure
 */
data class BehaviorPack(
    val uuid: String = UUID.randomUUID().toString(),
    val manifest: PackManifest,
    val entity: ServerEntity
)

/**
 * Pack manifest (manifest.json)
 */
data class PackManifest(
    val formatVersion: Int = 2,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val version: List<Int> = listOf(1, 0, 0),
    val minEngineVersion: List<Int> = listOf(1, 20, 0),
    val type: PackType,
    val dependencies: List<PackDependency> = emptyList()
) {
    fun toJsonMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "format_version" to formatVersion,
            "header" to mapOf(
                "name" to name,
                "description" to description,
                "uuid" to uuid,
                "version" to version,
                "min_engine_version" to minEngineVersion
            ),
            "modules" to listOf(
                mapOf(
                    "type" to type.value,
                    "uuid" to UUID.randomUUID().toString(),
                    "version" to version
                )
            )
        )

        if (dependencies.isNotEmpty()) {
            map["dependencies"] = dependencies.map { it.toJsonMap() }
        }

        return map
    }
}

enum class PackType(val value: String) {
    RESOURCES("resources"),
    DATA("data")
}

data class PackDependency(
    val uuid: String,
    val version: List<Int>
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "uuid" to uuid,
        "version" to version
    )
}

/**
 * Client-side entity definition (resource pack)
 */
data class ClientEntity(
    val formatVersion: String = "1.10.0",
    val identifier: String,
    val materials: Map<String, String> = mapOf("default" to "entity_alphatest"),
    val textures: Map<String, String> = emptyMap(),
    val geometry: Map<String, String> = emptyMap(),
    val renderControllers: List<String> = emptyList(),
    val spawnEgg: SpawnEgg? = null
) {
    fun toJsonMap(): Map<String, Any> {
        val description = mutableMapOf<String, Any>(
            "identifier" to identifier,
            "materials" to materials,
            "textures" to textures,
            "geometry" to geometry,
            "render_controllers" to renderControllers
        )

        spawnEgg?.let {
            description["spawn_egg"] = it.toJsonMap()
        }

        return mapOf(
            "format_version" to formatVersion,
            "minecraft:client_entity" to mapOf(
                "description" to description
            )
        )
    }
}

data class SpawnEgg(
    val baseColor: String = "#FF0000",
    val overlayColor: String = "#00FF00"
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "base_color" to baseColor,
        "overlay_color" to overlayColor
    )
}

/**
 * Server-side entity definition (behavior pack)
 */
data class ServerEntity(
    val formatVersion: String = "1.20.0",
    val namespace: String,
    val name: String,
    val isSpawnable: Boolean = true,
    val isSummonable: Boolean = true,
    val isExperimental: Boolean = false,
    val components: EntityComponents = EntityComponents()
) {
    val identifier: String get() = "$namespace:$name"

    fun toJsonMap(): Map<String, Any> {
        return mapOf(
            "format_version" to formatVersion,
            "minecraft:entity" to mapOf(
                "description" to mapOf(
                    "identifier" to identifier,
                    "is_spawnable" to isSpawnable,
                    "is_summonable" to isSummonable,
                    "is_experimental" to isExperimental
                ),
                "components" to components.toJsonMap()
            )
        )
    }
}

/**
 * Entity components for behavior
 */
data class EntityComponents(
    val health: HealthComponent? = HealthComponent(),
    val physics: PhysicsComponent? = PhysicsComponent(),
    val collisionBox: CollisionBoxComponent? = CollisionBoxComponent(),
    val pushable: PushableComponent? = PushableComponent(),
    val movement: MovementComponent? = null,
    val typeFamily: TypeFamilyComponent? = TypeFamilyComponent(),
    val scale: Float = 1f
) {
    fun toJsonMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()

        health?.let { map["minecraft:health"] = it.toJsonMap() }
        physics?.let { map["minecraft:physics"] = it.toJsonMap() }
        collisionBox?.let { map["minecraft:collision_box"] = it.toJsonMap() }
        pushable?.let { map["minecraft:pushable"] = it.toJsonMap() }
        movement?.let { map["minecraft:movement.basic"] = it.toJsonMap() }
        typeFamily?.let { map["minecraft:type_family"] = it.toJsonMap() }

        if (scale != 1f) {
            map["minecraft:scale"] = mapOf("value" to scale)
        }

        return map
    }
}

data class HealthComponent(
    val value: Int = 20,
    val max: Int = 20
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "value" to value,
        "max" to max
    )
}

data class PhysicsComponent(
    val hasCollision: Boolean = true,
    val hasGravity: Boolean = true
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "has_collision" to hasCollision,
        "has_gravity" to hasGravity
    )
}

data class CollisionBoxComponent(
    val width: Float = 1f,
    val height: Float = 1f
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "width" to width,
        "height" to height
    )
}

data class PushableComponent(
    val isPushable: Boolean = false,
    val isPushableByPiston: Boolean = true
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "is_pushable" to isPushable,
        "is_pushable_by_piston" to isPushableByPiston
    )
}

data class MovementComponent(
    val maxTurn: Float = 30f
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "max_turn" to maxTurn
    )
}

data class TypeFamilyComponent(
    val family: List<String> = listOf("custom", "mob")
) {
    fun toJsonMap(): Map<String, Any> = mapOf(
        "family" to family
    )
}

/**
 * Render controller definition
 */
data class RenderController(
    val formatVersion: String = "1.10.0",
    val identifier: String,
    val geometryField: String = "Geometry.default",
    val materialsField: List<Map<String, String>> = listOf(mapOf("*" to "Material.default")),
    val texturesField: List<String> = listOf("Texture.default")
) {
    fun toJsonMap(): Map<String, Any> {
        return mapOf(
            "format_version" to formatVersion,
            "render_controllers" to mapOf(
                identifier to mapOf(
                    "geometry" to geometryField,
                    "materials" to materialsField,
                    "textures" to texturesField
                )
            )
        )
    }
}