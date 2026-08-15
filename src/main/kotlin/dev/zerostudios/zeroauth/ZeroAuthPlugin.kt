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
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.ChatColor
import java.io.File

class ZeroAuthPlugin : JavaPlugin() {
    lateinit var authWorlds: AuthWorldManager
        private set
    private lateinit var storage: StorageProvider
    private lateinit var scripts: EventScriptManager
    private lateinit var manager: AuthManager
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

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
            message = message.replace("{$placeholder}", value.replace("<", "\\<").replace(">", "\\>"))
        }
        val prefix = config.getString("messages.prefix", "") ?: ""
        return color(prefix + message)
    }

    fun color(message: String): String {
        val converted = message
            .replace(Regex("&#([A-Fa-f0-9]{6})")) { "<#${it.groupValues[1]}>" }
            .replace(Regex("&([0-9a-fk-orA-FK-OR])")) {
                val tags = mapOf(
                    '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua",
                    '4' to "dark_red", '5' to "dark_purple", '6' to "gold", '7' to "gray",
                    '8' to "dark_gray", '9' to "blue", 'a' to "green", 'b' to "aqua",
                    'c' to "red", 'd' to "light_purple", 'e' to "yellow", 'f' to "white",
                    'k' to "obfuscated", 'l' to "bold", 'm' to "strikethrough", 'n' to "underlined",
                    'o' to "italic", 'r' to "reset"
                )
                "<${tags[it.groupValues[1].lowercase()[0]]}>"
            }
        return try {
            legacySerializer.serialize(miniMessage.deserialize(converted))
        } catch (_: Exception) {
            ChatColor.translateAlternateColorCodes('&', message)
        }
    }

    private fun command(name: String): PluginCommand? = getCommand(name)
}