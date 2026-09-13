package dev.zerostudios.zeroauth.storage

import dev.zerostudios.zeroauth.ZeroAuthPlugin
import dev.zerostudios.zeroauth.model.LocationData
import dev.zerostudios.zeroauth.model.UserRecord
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class FlatFileStorage(private val plugin: ZeroAuthPlugin) : StorageProvider {
    private val file = File(plugin.dataFolder, "users.yml")
    private val yaml = YamlConfiguration()

    init {
        file.parentFile.mkdirs()
        if (file.exists()) yaml.load(file)
    }

    @Synchronized
    override fun load(uuid: UUID): UserRecord? {
        val path = "users.$uuid"
        if (!yaml.contains(path)) return null
        val locationPath = "$path.location"
        val world = yaml.getString("$locationPath.world")
        val location = if (world == null) null else LocationData(
            world,
            yaml.getDouble("$locationPath.x"),
            yaml.getDouble("$locationPath.y"),
            yaml.getDouble("$locationPath.z"),
            yaml.getDouble("$locationPath.yaw").toFloat(),
            yaml.getDouble("$locationPath.pitch").toFloat()
        )
        return UserRecord(uuid, yaml.getString("$path.password"), location, yaml.getString("$path.email"))
    }

    @Synchronized
    override fun save(record: UserRecord) {
        val path = "users.${record.uuid}"
        yaml.set("$path.password", record.passwordHash)
        yaml.set("$path.email", record.email)
        val location = record.lastLocation
        if (location == null) {
            yaml.set("$path.location", null)
        } else {
            yaml.set("$path.location.world", location.world)
            yaml.set("$path.location.x", location.x)
            yaml.set("$path.location.y", location.y)
            yaml.set("$path.location.z", location.z)
            yaml.set("$path.location.yaw", location.yaw)
            yaml.set("$path.location.pitch", location.pitch)
        }
        yaml.save(file)
    }

    override fun close() = Unit
}