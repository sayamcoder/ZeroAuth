package dev.zerostudios.zeroauth.command

import dev.zerostudios.zeroauth.ZeroAuthPlugin
import dev.zerostudios.zeroauth.auth.AuthManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class RegisterCommand(private val plugin: ZeroAuthPlugin, private val manager: AuthManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can register.")
            return true
        }
        if (manager.isAuthenticated(player.uniqueId)) {
            player.sendMessage(plugin.message("already-authenticated"))
            return true
        }
        if (args.size != 2) {
            player.sendMessage(plugin.message("usage", mapOf("usage" to "/register <password> <password>")))
            return true
        }
        when (manager.register(player, args[0], args[1])) {
            AuthManager.RegistrationResult.SUCCESS -> player.sendMessage(plugin.message("registered"))
            AuthManager.RegistrationResult.PASSWORD_MISMATCH -> player.sendMessage(plugin.message("password-mismatch"))
            AuthManager.RegistrationResult.INVALID_PASSWORD -> player.sendMessage(plugin.message("invalid-password"))
            AuthManager.RegistrationResult.ALREADY_REGISTERED -> player.sendMessage(plugin.message("already-registered"))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> = mutableListOf()
}

class LoginCommand(private val plugin: ZeroAuthPlugin, private val manager: AuthManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can log in.")
            return true
        }
        if (manager.isAuthenticated(player.uniqueId)) {
            player.sendMessage(plugin.message("already-authenticated"))
            return true
        }
        if (args.size != 1) {
            player.sendMessage(plugin.message("usage", mapOf("usage" to "/login <password>")))
            return true
        }
        when (manager.login(player, args[0])) {
            AuthManager.LoginResult.SUCCESS -> player.sendMessage(plugin.message("logged-in"))
            AuthManager.LoginResult.NOT_REGISTERED -> player.sendMessage(plugin.message("not-registered"))
            AuthManager.LoginResult.INVALID_PASSWORD -> player.sendMessage(plugin.message("invalid-password"))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> = mutableListOf()
}

class ZeroAuthCommand(private val plugin: ZeroAuthPlugin, private val manager: AuthManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("zeroauth.admin")) {
            sender.sendMessage(plugin.message("no-permission"))
            return true
        }
        when (args.firstOrNull()?.lowercase()) {
            "reload" -> {
                plugin.reloadPluginConfiguration()
                sender.sendMessage(plugin.message("reload"))
            }
            "setspawn" -> {
                val player = sender as? Player
                if (player == null || !plugin.authWorlds.isAuthWorld(player.location)) {
                    sender.sendMessage("Stand in the authentication world to set its spawn.")
                } else {
                    plugin.authWorlds.setSpawn(player.location)
                    sender.sendMessage(plugin.message("spawn-set"))
                }
            }
            "auth", "logout", "status" -> handlePlayerAction(sender, args)
            else -> sender.sendMessage(plugin.message("usage", mapOf("usage" to "/zeroauth <reload|setspawn|auth|logout|status> [player]")))
        }
        return true
    }

    private fun handlePlayerAction(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(plugin.message("usage", mapOf("usage" to "/zeroauth <auth|logout|status> <player>")))
            return
        }
        val player = Bukkit.getPlayerExact(args[1])
        if (player == null) {
            sender.sendMessage(plugin.message("player-not-found"))
            return
        }
        when (args[0].lowercase()) {
            "auth" -> {
                manager.forceAuthenticate(player)
                sender.sendMessage(plugin.message("forced-auth"))
            }
            "logout" -> {
                manager.logout(player)
                sender.sendMessage(plugin.message("forced-logout"))
            }
            "status" -> sender.sendMessage(plugin.message("status", mapOf(
                "player" to player.name,
                "registered" to manager.isRegistered(player.uniqueId).toString(),
                "authenticated" to manager.isAuthenticated(player.uniqueId).toString()
            )))
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            return listOf("reload", "setspawn", "auth", "logout", "status")
                .filter { it.startsWith(args[0], true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].lowercase() in setOf("auth", "logout", "status")) {
            return Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.startsWith(args[1], true) }
                .sorted()
                .toMutableList()
        }
        return mutableListOf()
    }
}