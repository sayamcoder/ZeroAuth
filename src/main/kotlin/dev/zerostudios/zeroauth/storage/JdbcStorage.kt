package dev.zerostudios.zeroauth.storage

import dev.zerostudios.zeroauth.ZeroAuthPlugin
import dev.zerostudios.zeroauth.model.LocationData
import dev.zerostudios.zeroauth.model.UserRecord
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID

class JdbcStorage(private val plugin: ZeroAuthPlugin, private val type: String) : StorageProvider {
    private val url: String
    private val username: String
    private val password: String

    init {
        val section = plugin.config.getConfigurationSection("storage.$type")
            ?: error("Missing storage.$type configuration")
        if (type == "sqlite") {
            val database = section.getString("file", "zeroauth.db")!!
            url = "jdbc:sqlite:${java.io.File(plugin.dataFolder, database).path}"
            username = ""
            password = ""
            Class.forName("org.sqlite.JDBC")
        } else {
            url = section.getString("url") ?: error("Missing storage.$type.url")
            username = section.getString("username", "")!!
            password = section.getString("password", "")!!
            if (type == "mysql") Class.forName("com.mysql.cj.jdbc.Driver")
            if (type == "postgresql") Class.forName("org.postgresql.Driver")
        }
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS zeroauth_users (" +
                        "uuid VARCHAR(36) PRIMARY KEY, password_hash TEXT, world_name VARCHAR(255), " +
                        "x DOUBLE PRECISION, y DOUBLE PRECISION, z DOUBLE PRECISION, yaw REAL, pitch REAL, email TEXT)"
                )
                runCatching { statement.executeUpdate("ALTER TABLE zeroauth_users ADD COLUMN email TEXT") }
            }
        }
    }

    private fun connection(): Connection = if (type == "sqlite") {
        DriverManager.getConnection(url)
    } else {
        DriverManager.getConnection(url, username, password)
    }

    override fun load(uuid: UUID): UserRecord? {
        connection().use { connection ->
            connection.prepareStatement("SELECT * FROM zeroauth_users WHERE uuid = ?").use { statement ->
                statement.setString(1, uuid.toString())
                statement.executeQuery().use { result ->
                    if (!result.next()) return null
                    return UserRecord(uuid, result.getString("password_hash"), readLocation(result), result.getString("email"))
                }
            }
        }
    }

    override fun save(record: UserRecord) {
        connection().use { connection ->
            connection.autoCommit = false
            try {
                val exists = connection.prepareStatement("SELECT 1 FROM zeroauth_users WHERE uuid = ?").use { statement ->
                    statement.setString(1, record.uuid.toString())
                    statement.executeQuery().use { it.next() }
                }
                if (exists) {
                    connection.prepareStatement(
                        "UPDATE zeroauth_users SET password_hash = ?, world_name = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?, email = ? WHERE uuid = ?"
                    ).use { statement ->
                        bindRecord(statement, record, 1)
                        statement.setString(9, record.uuid.toString())
                        statement.executeUpdate()
                    }
                } else {
                    connection.prepareStatement(
                        "INSERT INTO zeroauth_users (uuid, password_hash, world_name, x, y, z, yaw, pitch, email) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    ).use { statement ->
                        statement.setString(1, record.uuid.toString())
                        bindRecord(statement, record, 2)
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (exception: Exception) {
                connection.rollback()
                throw exception
            }
        }
    }

    private fun bindRecord(statement: PreparedStatement, record: UserRecord, start: Int) {
        if (record.passwordHash == null) statement.setNull(start, Types.VARCHAR) else statement.setString(start, record.passwordHash)
        val location = record.lastLocation
        if (location == null) {
            statement.setNull(start + 1, Types.VARCHAR)
            statement.setNull(start + 2, Types.DOUBLE)
            statement.setNull(start + 3, Types.DOUBLE)
            statement.setNull(start + 4, Types.DOUBLE)
            statement.setNull(start + 5, Types.REAL)
            statement.setNull(start + 6, Types.REAL)
        } else {
            statement.setString(start + 1, location.world)
            statement.setDouble(start + 2, location.x)
            statement.setDouble(start + 3, location.y)
            statement.setDouble(start + 4, location.z)
            statement.setFloat(start + 5, location.yaw)
            statement.setFloat(start + 6, location.pitch)
        }
        if (record.email == null) statement.setNull(start + 7, Types.VARCHAR) else statement.setString(start + 7, record.email)
    }

    private fun readLocation(result: ResultSet): LocationData? {
        val world = result.getString("world_name") ?: return null
        return LocationData(
            world,
            result.getDouble("x"),
            result.getDouble("y"),
            result.getDouble("z"),
            result.getFloat("yaw"),
            result.getFloat("pitch")
        )
    }

    override fun close() = Unit
}