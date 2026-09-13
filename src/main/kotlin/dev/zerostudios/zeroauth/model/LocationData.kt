package dev.zerostudios.zeroauth.model

import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.UUID

data class LocationData(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float
) {
    fun toLocation(): Location? = Bukkit.getWorld(world)?.let { Location(it, x, y, z, yaw, pitch) }

    companion object {
        fun fromLocation(location: Location): LocationData = LocationData(
            location.world.name, location.x, location.y, location.z, location.yaw, location.pitch
        )
    }
}

data class UserRecord(
    val uuid: UUID,
    var passwordHash: String? = null,
    var lastLocation: LocationData? = null,
    var email: String? = null
)