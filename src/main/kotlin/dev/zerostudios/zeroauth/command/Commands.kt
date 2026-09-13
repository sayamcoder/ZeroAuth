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

class EmailCommand(private val plugin: ZeroAuthPlugin, private val manager: AuthManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can attach an email.")
            return true
        }
        if (!manager.isAuthenticated(player.uniqueId)) {
            player.sendMessage(plugin.message("email-auth-required"))
            return true
        }
        if (args.size != 1) {
            player.sendMessage(plugin.message("usage", mapOf("usage" to "/attach-email <email|remove>")))
            return true
        }
        val email = if (args[0].equals("remove", true)) null else args[0]
        when (manager.attachEmail(player.uniqueId, email)) {
            AuthManager.EmailResult.SUCCESS -> player.sendMessage(
                plugin.message("email-attached", mapOf("email" to (email ?: "none")))
            )
            AuthManager.EmailResult.NOT_REGISTERED -> player.sendMessage(plugin.message("email-requires-registration"))
            AuthManager.EmailResult.INVALID -> player.sendMessage(plugin.message("email-invalid"))
            AuthManager.EmailResult.DISABLED -> player.sendMessage(plugin.message("email-disabled"))
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> = if (args.size == 1) mutableListOf("remove") else mutableListOf()
}

class ZeroAuthCommand(private val plugin: ZeroAuthPlugin, private val manager: AuthManager) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val permission = plugin.config.getString("admin.permission", "zeroauth.admin") ?: "zeroauth.admin"
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(plugin.message("no-permission"))
            return true
        }
        when (args.firstOrNull()?.lowercase()) {
            "help" -> sender.sendMessage(plugin.message("admin-help"))
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
            "resetpassword" -> resetPassword(sender, args)
            "email" -> manageEmail(sender, args)
            "list" -> sendPlayerList(sender)
            "tp", "teleport" -> teleportToAuthWorld(sender, args)
            "info" -> sender.sendMessage(plugin.message("world-info", mapOf(
                "world" to plugin.authWorlds.world.name,
                "spawn" to "${plugin.authWorlds.spawn.blockX}, ${plugin.authWorlds.spawn.blockY}, ${plugin.authWorlds.spawn.blockZ}"
            )))
            else -> sender.sendMessage(plugin.message("usage", mapOf("usage" to "/zeroauth <help|reload|setspawn|auth|logout|status|resetpassword|email|list|tp|info> [player] [password|email]")))
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

    private fun resetPassword(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage(plugin.message("usage", mapOf("usage" to "/zeroauth resetpassword <player> <password>")))
            return
        }
        val player = Bukkit.getPlayerExact(args[1])
        if (player == null) {
            sender.sendMessage(plugin.message("player-not-found"))
            return
        }
        when (manager.resetPassword(player.uniqueId, args[2])) {
            AuthManager.ResetPasswordResult.SUCCESS -> sender.sendMessage(plugin.message("password-reset", mapOf("player" to player.name)))
            AuthManager.ResetPasswordResult.NOT_REGISTERED -> sender.sendMessage(plugin.message("reset-password-not-registered"))
            AuthManager.ResetPasswordResult.INVALID_PASSWORD -> sender.sendMessage(plugin.message("invalid-password"))
        }
    }

    private fun manageEmail(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage(plugin.message("usage", mapOf("usage" to "/zeroauth email <player> <email|remove>")))
            return
        }
        val player = Bukkit.getPlayerExact(args[1])
        if (player == null) {
            sender.sendMessage(plugin.message("player-not-found"))
            return
        }
        val email = if (args[2].equals("remove", true)) null else args[2]
        when (manager.attachEmail(player.uniqueId, email)) {
            AuthManager.EmailResult.SUCCESS -> sender.sendMessage(plugin.message("email-managed", mapOf("player" to player.name)))
            AuthManager.EmailResult.NOT_REGISTERED -> sender.sendMessage(plugin.message("email-requires-registration"))
            AuthManager.EmailResult.INVALID -> sender.sendMessage(plugin.message("email-invalid"))
            AuthManager.EmailResult.DISABLED -> sender.sendMessage(plugin.message("email-disabled"))
        }
    }

    private fun sendPlayerList(sender: CommandSender) {
        sender.sendMessage(plugin.message("admin-list-header"))
        Bukkit.getOnlinePlayers().forEach { player ->
            sender.sendMessage(plugin.message("admin-list-entry", mapOf(
                "player" to player.name,
                "registered" to manager.isRegistered(player.uniqueId).toString(),
                "authenticated" to manager.isAuthenticated(player.uniqueId).toString(),
                "world" to player.world.name
            )))
        }
    }

    private fun teleportToAuthWorld(sender: CommandSender, args: Array<out String>) {
        val target = if (args.size >= 2) Bukkit.getPlayerExact(args[1]) else sender as? Player
        if (target == null) {
            sender.sendMessage(plugin.message("player-not-found"))
            return
        }
        plugin.authWorlds.teleport(target)
        sender.sendMessage(plugin.message("teleported", mapOf("player" to target.name)))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        if (args.size == 1) {
            return listOf("help", "reload", "setspawn", "auth", "logout", "status", "resetpassword", "email", "list", "tp", "info")
                .filter { it.startsWith(args[0], true) }
                .toMutableList()
        }
        if (args.size == 2 && args[0].lowercase() in setOf("auth", "logout", "status", "resetpassword", "email", "tp", "teleport")) {
            return Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.startsWith(args[1], true) }
                .sorted()
                .toMutableList()
        }
        return mutableListOf()
    }
}