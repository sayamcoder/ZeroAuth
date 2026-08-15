package dev.zerostudios.zeroauth.auth

import dev.zerostudios.zeroauth.ZeroAuthPlugin
import dev.zerostudios.zeroauth.event.EventScriptManager
import dev.zerostudios.zeroauth.model.LocationData
import dev.zerostudios.zeroauth.model.UserRecord
import dev.zerostudios.zeroauth.security.PasswordHasher
import dev.zerostudios.zeroauth.storage.StorageProvider
import dev.zerostudios.zeroauth.world.AuthWorldManager
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AuthManager(
    private val plugin: ZeroAuthPlugin,
    private val storage: StorageProvider,
    private val worlds: AuthWorldManager,
    private val scripts: EventScriptManager
) {
    private val authenticated = ConcurrentHashMap.newKeySet<UUID>()
    private val pendingLocations = ConcurrentHashMap<UUID, LocationData>()
    private val originalFlight = ConcurrentHashMap<UUID, FlightState>()

    fun handleJoin(player: Player) {
        authenticated.remove(player.uniqueId)
        prepareForAuthentication(player)
        val record = storage.load(player.uniqueId)
        if (!worlds.isAuthWorld(player.location)) {
            pendingLocations[player.uniqueId] = record?.lastLocation ?: LocationData.fromLocation(player.location)
        }
        worlds.teleport(player)
        player.sendMessage(plugin.message("join"))
    }

    fun register(player: Player, password: String, confirmation: String): RegistrationResult {
        if (password != confirmation) return RegistrationResult.PASSWORD_MISMATCH
        if (password.length < plugin.config.getInt("security.minimum-password-length", 8)) {
            return RegistrationResult.INVALID_PASSWORD
        }
        val record = storage.load(player.uniqueId) ?: UserRecord(player.uniqueId)
        if (record.passwordHash != null) return RegistrationResult.ALREADY_REGISTERED
        record.passwordHash = PasswordHasher.hash(password)
        storage.save(record)
        completeAuthentication(player, record)
        return RegistrationResult.SUCCESS
    }

    fun login(player: Player, password: String): LoginResult {
        val record = storage.load(player.uniqueId) ?: return LoginResult.NOT_REGISTERED
        val hash = record.passwordHash ?: return LoginResult.NOT_REGISTERED
        if (!PasswordHasher.verify(password, hash)) return LoginResult.INVALID_PASSWORD
        completeAuthentication(player, record)
        return LoginResult.SUCCESS
    }

    fun logout(player: Player) {
        if (!authenticated.remove(player.uniqueId)) return
        saveLocation(player)
        pendingLocations.remove(player.uniqueId)
        prepareForAuthentication(player)
        worlds.teleport(player)
    }

    fun forceAuthenticate(player: Player) {
        val record = storage.load(player.uniqueId) ?: UserRecord(player.uniqueId)
        completeAuthentication(player, record)
    }

    fun handleQuit(player: Player) {
        if (authenticated.remove(player.uniqueId)) {
            saveLocation(player)
        } else {
            pendingLocations[player.uniqueId]?.let { location ->
                val record = storage.load(player.uniqueId) ?: UserRecord(player.uniqueId)
                record.lastLocation = location
                storage.save(record)
            }
        }
        pendingLocations.remove(player.uniqueId)
        originalFlight.remove(player.uniqueId)
    }

    fun isAuthenticated(uuid: UUID): Boolean = authenticated.contains(uuid)

    fun isRegistered(uuid: UUID): Boolean = storage.load(uuid)?.passwordHash != null

    private fun completeAuthentication(player: Player, record: UserRecord) {
        authenticated.add(player.uniqueId)
        val target = pendingLocations.remove(player.uniqueId)?.toLocation() ?: record.lastLocation?.toLocation()
        if (target != null && !worlds.isAuthWorld(target)) {
            player.teleport(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN)
        }
        restoreFlight(player)
        scripts.runJoin(player)
    }

    private fun prepareForAuthentication(player: Player) {
        originalFlight.putIfAbsent(player.uniqueId, FlightState(player.allowFlight, player.isFlying))
        player.fallDistance = 0f
        player.isFlying = false
        player.allowFlight = true
    }

    private fun restoreFlight(player: Player) {
        val state = originalFlight.remove(player.uniqueId) ?: return
        player.isFlying = state.flying && state.allowFlight
        player.allowFlight = state.allowFlight
        player.fallDistance = 0f
    }

    private data class FlightState(val allowFlight: Boolean, val flying: Boolean)

    private fun saveLocation(player: Player) {
        if (worlds.isAuthWorld(player.location)) return
        val record = storage.load(player.uniqueId) ?: UserRecord(player.uniqueId)
        record.lastLocation = LocationData.fromLocation(player.location)
        storage.save(record)
    }

    enum class RegistrationResult { SUCCESS, PASSWORD_MISMATCH, INVALID_PASSWORD, ALREADY_REGISTERED }
    enum class LoginResult { SUCCESS, NOT_REGISTERED, INVALID_PASSWORD }
}