package dev.zerostudios.zeroauth.event

import dev.zerostudios.zeroauth.ZeroAuthPlugin
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File

class EventScriptManager(private val plugin: ZeroAuthPlugin) {
    private var joinActions: List<String> = emptyList()

    init {
        reload()
    }

    fun reload() {
        val folder = File(plugin.dataFolder, "events")
        folder.mkdirs()
        joinActions = folder.listFiles { file -> file.isFile && file.extension.equals("yml", true) }
            ?.sortedBy { it.name }
            ?.flatMap { file ->
                try {
                    val yaml = YamlConfiguration.loadConfiguration(file)
                    if (yaml.getBoolean("enabled", true) && yaml.getString("event")?.equals("join", true) == true) {
                        yaml.getStringList("actions")
                    } else emptyList()
                } catch (exception: Exception) {
                    plugin.logger.warning("Could not load event script ${file.name}: ${exception.message}")
                    emptyList()
                }
            }
            ?: emptyList()
    }

    fun runJoin(player: Player) {
        joinActions.forEach { rawAction ->
            val action = replacePlaceholders(rawAction, player)
            when {
                action.startsWith("console:", true) -> plugin.server.dispatchCommand(
                    plugin.server.consoleSender, action.substringAfter(':').trim()
                )
                action.startsWith("player:", true) -> player.performCommand(action.substringAfter(':').trim())
                action.startsWith("message:", true) -> player.sendMessage(plugin.color(action.substringAfter(':').trim()))
                action.isNotBlank() -> player.sendMessage(plugin.color(action))
            }
        }
    }

    private fun replacePlaceholders(action: String, player: Player): String = action
        .replace("{player}", player.name)
        .replace("{uuid}", player.uniqueId.toString())
        .replace("{world}", player.world.name)
}