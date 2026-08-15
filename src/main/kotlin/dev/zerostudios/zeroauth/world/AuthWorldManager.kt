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
        val environment = runCatching {
            World.Environment.valueOf(plugin.config.getString("auth-world.environment", "THE_END")!!.uppercase())
        }.getOrDefault(World.Environment.THE_END)
        val existing = Bukkit.getWorld(name)
        val existingFolder = File(plugin.server.worldContainer, name)
        if (existing != null && mode == "void" && plugin.config.getBoolean("auth-world.reset-existing-world", true)) {
            val marker = File(existing.worldFolder, ".zeroauth-void-v2")
            if (!marker.exists() || existing.environment != environment) {
                val folder = existing.worldFolder
                require(Bukkit.unloadWorld(existing, false)) { "Could not unload authentication world '$name'" }
                require(!folder.exists() || folder.deleteRecursively()) { "Could not reset authentication world '$name'" }
            }
        }
        if (existing == null && mode == "void" && plugin.config.getBoolean("auth-world.reset-existing-world", true)) {
            val marker = File(existingFolder, ".zeroauth-void-v2")
            if (existingFolder.exists() && !marker.exists()) {
                require(existingFolder.deleteRecursively()) { "Could not reset authentication world '$name'" }
            }
        }

        world = Bukkit.getWorld(name) ?: WorldCreator(name)
            .environment(environment)
            .generateStructures(false)
            .generator(VoidWorldGenerator())
            .createWorld()
            ?: error("Could not create authentication world '$name'")

        if (mode == "void") {
            File(world.worldFolder, ".zeroauth-void-v2").writeText("ZeroAuth void authentication world")
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
        world.getEntities().filterIsInstance<EnderDragon>().forEach { it.remove() }
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        world.setKeepSpawnInMemory(false)
        world.loadChunk(spawn.blockX shr 4, spawn.blockZ shr 4, true)
        createSpawnPlatform()
        world.setSpawnLocation(spawn.blockX, spawn.blockY, spawn.blockZ)
        world.difficulty = org.bukkit.Difficulty.PEACEFUL
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

    private class VoidWorldGenerator : ChunkGenerator() {
        override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
            return createChunkData(world)
        }
    }
}