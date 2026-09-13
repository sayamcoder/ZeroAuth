package dev.zerostudios.zeroauth.world

import dev.zerostudios.zeroauth.ZeroAuthPlugin
import org.bukkit.Location
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.generator.ChunkGenerator
import org.bukkit.entity.EnderDragon
import java.io.File
import java.util.Random

class AuthWorldManager(private val plugin: ZeroAuthPlugin) {
    val world: World
    var spawn: Location
        private set

    init {
        val name = plugin.config.getString("auth-world.name", "virtual_world_auth")!!
        val mode = plugin.config.getString("auth-world.mode", "void")!!.lowercase()
        val disableStructures = plugin.config.getBoolean("auth-world.disable-structures", true)
        val environment = runCatching {
            World.Environment.valueOf(plugin.config.getString("auth-world.environment", "THE_END")!!.uppercase())
        }.getOrDefault(World.Environment.THE_END)
        val existing = Bukkit.getWorld(name)
        val existingFolder = File(plugin.server.worldContainer, name)
        if (existing != null && plugin.config.getBoolean("auth-world.reset-existing-world", true)) {
            val marker = File(existing.worldFolder, ".zeroauth-void-v2")
            val structureMarker = File(existing.worldFolder, ".zeroauth-structures-disabled-v1")
            val shouldReset = existing.environment != environment ||
                (mode == "void" && !marker.exists()) ||
                (mode != "void" && marker.exists()) ||
                (disableStructures && !structureMarker.exists()) ||
                (!disableStructures && structureMarker.exists())
            if (shouldReset) {
                val folder = existing.worldFolder
                require(Bukkit.unloadWorld(existing, false)) { "Could not unload authentication world '$name'" }
                require(!folder.exists() || folder.deleteRecursively()) { "Could not reset authentication world '$name'" }
            }
        }
        if (existing == null && plugin.config.getBoolean("auth-world.reset-existing-world", true)) {
            val marker = File(existingFolder, ".zeroauth-void-v2")
            val structureMarker = File(existingFolder, ".zeroauth-structures-disabled-v1")
            val shouldReset = existingFolder.exists() && (
                (mode == "void" && !marker.exists()) ||
                    (mode != "void" && marker.exists()) ||
                    (disableStructures && !structureMarker.exists()) ||
                    (!disableStructures && structureMarker.exists())
                )
            if (shouldReset) {
                require(existingFolder.deleteRecursively()) { "Could not reset authentication world '$name'" }
            }
        }

        val creator = WorldCreator(name)
            .environment(environment)
            .generateStructures(mode != "void" && !disableStructures)
        if (mode == "void") creator.generator(VoidWorldGenerator())
        world = Bukkit.getWorld(name) ?: creator.createWorld()
            ?: error("Could not create authentication world '$name'")

        if (mode == "void") {
            File(world.worldFolder, ".zeroauth-void-v2").writeText("ZeroAuth void authentication world")
        }
        if (disableStructures) {
            File(world.worldFolder, ".zeroauth-structures-disabled-v1").writeText("ZeroAuth structure-free authentication world")
        }

        val section = plugin.config.getConfigurationSection("auth-world.spawn")
        spawn = Location(
            world,
            section?.getDouble("x", 0.5) ?: 0.5,
            section?.getDouble("y", 100.0) ?: 100.0,
            section?.getDouble("z", 0.5) ?: 0.5,
            section?.getDouble("yaw", 0.0)?.toFloat() ?: 0f,
            section?.getDouble("pitch", 0.0)?.toFloat() ?: 0f
        )
        if (environment == World.Environment.THE_END && mode != "void" &&
            plugin.config.getBoolean("auth-world.spawn-ender-dragon", true)) {
            spawnEnderDragon()
        }
        val disableMobSpawning = plugin.config.getBoolean("auth-world.disable-mob-spawning", true)
        world.setGameRule(GameRule.DO_MOB_SPAWNING, !disableMobSpawning)
        if (disableMobSpawning) {
            world.setGameRule(GameRule.DO_PATROL_SPAWNING, false)
            world.setGameRule(GameRule.DO_TRADER_SPAWNING, false)
            world.setGameRule(GameRule.DO_INSOMNIA, false)
        }
        world.setKeepSpawnInMemory(false)
        world.loadChunk(spawn.blockX shr 4, spawn.blockZ shr 4, true)
        createSpawnPlatform()
        createSpawnBarrier()
        world.setSpawnLocation(spawn.blockX, spawn.blockY, spawn.blockZ)
        world.difficulty = org.bukkit.Difficulty.NORMAL
        world.setPVP(false)
        world.setTime(6000)
    }

    fun teleport(player: org.bukkit.entity.Player) {
        player.teleport(spawn, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)
    }

    fun isAuthWorld(location: Location?): Boolean = location?.world?.uid == world.uid

    fun setSpawn(location: Location) {
        require(isAuthWorld(location))
        spawn = location.clone()
        createSpawnPlatform()
        createSpawnBarrier()
        world.setSpawnLocation(spawn.blockX, spawn.blockY, spawn.blockZ)
        plugin.config.set("auth-world.spawn.x", spawn.x)
        plugin.config.set("auth-world.spawn.y", spawn.y)
        plugin.config.set("auth-world.spawn.z", spawn.z)
        plugin.config.set("auth-world.spawn.yaw", spawn.yaw)
        plugin.config.set("auth-world.spawn.pitch", spawn.pitch)
        plugin.saveConfig()
    }

    private fun createSpawnPlatform() {
        val floorY = spawn.blockY - 1
        if (floorY < world.minHeight) return

        val center = world.getBlockAt(spawn.blockX, floorY, spawn.blockZ)
        if (!center.type.isAir && !center.isLiquid) return

        for (x in spawn.blockX - 1..spawn.blockX + 1) {
            for (z in spawn.blockZ - 1..spawn.blockZ + 1) {
                world.getBlockAt(x, floorY, z).type = Material.BEDROCK
            }
        }
    }

    private fun createSpawnBarrier() {
        val floorY = spawn.blockY - 1
        val minX = spawn.blockX - 4
        val maxX = spawn.blockX + 4
        val minZ = spawn.blockZ - 4
        val maxZ = spawn.blockZ + 4

        for (y in floorY + 1..spawn.blockY + 4) {
            for (x in minX..maxX) {
                world.getBlockAt(x, y, minZ).type = Material.BARRIER
                world.getBlockAt(x, y, maxZ).type = Material.BARRIER
            }
            for (z in minZ..maxZ) {
                world.getBlockAt(minX, y, z).type = Material.BARRIER
                world.getBlockAt(maxX, y, z).type = Material.BARRIER
            }
        }

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                world.getBlockAt(x, spawn.blockY + 5, z).type = Material.BARRIER
            }
        }
    }

    private fun spawnEnderDragon() {
        val dragons = world.getEntities().filterIsInstance<EnderDragon>()
        if (dragons.isNotEmpty()) return

        val center = Location(world, 0.5, 80.0, 0.5)
        world.loadChunk(center.blockX shr 4, center.blockZ shr 4, true)
        world.spawn(center, EnderDragon::class.java)
    }

    private class VoidWorldGenerator : ChunkGenerator() {
        override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
            return createChunkData(world)
        }
    }
}