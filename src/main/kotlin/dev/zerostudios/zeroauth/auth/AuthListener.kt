package dev.zerostudios.zeroauth.auth

import dev.zerostudios.zeroauth.ZeroAuthPlugin
import dev.zerostudios.zeroauth.world.AuthWorldManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent

class AuthListener(
    private val plugin: ZeroAuthPlugin,
    private val manager: AuthManager,
    private val worlds: AuthWorldManager
) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTask(plugin, Runnable { manager.handleJoin(event.player) })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) = manager.handleQuit(event.player)

    @EventHandler
    fun onMove(event: PlayerMoveEvent) {
        if (manager.isAuthenticated(event.player.uniqueId) || !worlds.isAuthWorld(event.from)) return
        val destination = event.to ?: return
        if (!worlds.isAuthWorld(destination)) {
            event.isCancelled = true
        } else if (destination.y < 2.0) {
            event.isCancelled = true
            worlds.teleport(event.player)
        }
    }

    @EventHandler
    fun onTeleport(event: PlayerTeleportEvent) {
        if (event.player !is Player || manager.isAuthenticated(event.player.uniqueId)) return
        if (!worlds.isAuthWorld(event.to)) event.isCancelled = true
    }

    @EventHandler
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        if (manager.isAuthenticated(event.player.uniqueId)) return
        val command = event.message.removePrefix("/").trim().split(Regex("\\s+"), limit = 2).firstOrNull()?.lowercase()
            ?: return
        val allowed = plugin.config.getStringList("security.allowed-unauthenticated-commands").map { it.lowercase() }
        if (command !in allowed) {
            event.isCancelled = true
            event.player.sendMessage(plugin.message("command-blocked"))
        }
    }

    @EventHandler
    fun onDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (!manager.isAuthenticated(player.uniqueId)) event.isCancelled = true
    }

    @EventHandler
    fun onFood(event: FoodLevelChangeEvent) {
        if (!manager.isAuthenticated(event.entity.uniqueId)) event.isCancelled = true
    }

    @EventHandler
    fun onInventory(event: InventoryOpenEvent) {
        if (!manager.isAuthenticated(event.player.uniqueId)) event.isCancelled = true
    }
}