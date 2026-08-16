package dev.zerostudios.zeroauth

import dev.zerostudios.zeroauth.auth.AuthListener
import dev.zerostudios.zeroauth.auth.AuthManager
import dev.zerostudios.zeroauth.command.LoginCommand
import dev.zerostudios.zeroauth.command.RegisterCommand
import dev.zerostudios.zeroauth.command.ZeroAuthCommand
import dev.zerostudios.zeroauth.event.EventScriptManager
import dev.zerostudios.zeroauth.storage.StorageFactory
import dev.zerostudios.zeroauth.storage.StorageProvider
import dev.zerostudios.zeroauth.world.AuthWorldManager
import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.ChatColor
import java.io.File

class ZeroAuthPlugin : JavaPlugin() {
    lateinit var authWorlds: AuthWorldManager
        private set
    private lateinit var storage: StorageProvider
    private lateinit var scripts: EventScriptManager
    private lateinit var manager: AuthManager

    override fun onEnable() {
        saveDefaultConfig()
        dataFolder.mkdirs()
        File(dataFolder, "events").mkdirs()
        if (!File(dataFolder, "events/join.yml").exists()) saveResource("events/join.yml", false)
        authWorlds = AuthWorldManager(this)
        storage = StorageFactory.create(this)
        scripts = EventScriptManager(this)
        manager = AuthManager(this, storage, authWorlds, scripts)
        server.pluginManager.registerEvents(AuthListener(this, manager, authWorlds), this)
        val registerCommand = RegisterCommand(this, manager)
        val loginCommand = LoginCommand(this, manager)
        val zeroAuthCommand = ZeroAuthCommand(this, manager)
        command("register")?.apply {
            setExecutor(registerCommand)
            tabCompleter = registerCommand
        }
        command("login")?.apply {
            setExecutor(loginCommand)
            tabCompleter = loginCommand
        }
        command("zeroauth")?.apply {
            setExecutor(zeroAuthCommand)
            tabCompleter = zeroAuthCommand
        }
        logger.info(
            "\n" +
                "  ███████╗███████╗██████╗  ██████╗  █████╗ ██╗   ██╗████████╗██╗  ██╗\n" +
                "  ╚══███╔╝██╔════╝██╔══██╗██╔═══██╗██╔══██╗██║   ██║╚══██╔══╝██║  ██║\n" +
                "    ███╔╝ █████╗  ██████╔╝██║   ██║███████║██║   ██║   ██║   ███████║\n" +
                "   ███╔╝  ██╔══╝  ██╔══██╗██║   ██║██╔══██║██║   ██║   ██║   ██╔══██║\n" +
                "  ███████╗███████╗██║  ██║╚██████╔╝██║  ██║╚██████╔╝   ██║   ██║  ██║\n" +
                "  ╚══════╝╚══════╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚═╝ ╚═════╝    ╚═╝   ╚═╝  ╚═╝\n" +
                "  ZeroAuth by ZeroStudios"
        )
        logger.info("ZeroAuth enabled with ${authWorlds.world.name} as the authentication world.")
    }

    override fun onDisable() {
        if (::storage.isInitialized) storage.close()
    }

    fun reloadPluginConfiguration() {
        reloadConfig()
        if (::scripts.isInitialized) scripts.reload()
    }

    fun message(key: String, replacements: Map<String, String> = emptyMap()): String {
        var message = config.getString("messages.$key", key) ?: key
        replacements.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value)
        }
        val prefix = config.getString("messages.prefix", "") ?: ""
        return color(prefix + message)
    }

    fun color(message: String): String {
        var converted = message
            .replace(Regex("(?i)<gradient:([^>]+)>")) {
                val firstColor = it.groupValues[1].substringBefore(':')
                if (firstColor.matches(Regex("#[A-Fa-f0-9]{6}"))) {
                    legacyHexColor(firstColor.substring(1))
                } else {
                    ""
                }
            }
            .replace(Regex("(?i)</gradient>"), "&r")
            .replace(Regex("(?i)<#([A-Fa-f0-9]{6})>")) { legacyHexColor(it.groupValues[1]) }
            .replace(Regex("&#([A-Fa-f0-9]{6})")) { legacyHexColor(it.groupValues[1]) }
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)

        val tags = mapOf(
            "black" to '0', "dark_blue" to '1', "dark_green" to '2', "dark_aqua" to '3',
            "dark_red" to '4', "dark_purple" to '5', "gold" to '6', "gray" to '7',
            "dark_gray" to '8', "blue" to '9', "green" to 'a', "aqua" to 'b',
            "red" to 'c', "light_purple" to 'd', "yellow" to 'e', "white" to 'f',
            "obfuscated" to 'k', "bold" to 'l', "strikethrough" to 'm',
            "underlined" to 'n', "italic" to 'o', "reset" to 'r'
        )
        tags.forEach { (tag, code) ->
            converted = converted.replace(Regex("(?i)</?$tag>"), "&$code")
        }
        return ChatColor.translateAlternateColorCodes('&', converted)
    }

    private fun legacyHexColor(hex: String): String =
        "&x" + hex.map { "&$it" }.joinToString("")

    private fun command(name: String): PluginCommand? = getCommand(name)
}