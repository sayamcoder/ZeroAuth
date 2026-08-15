package dev.zerostudios.zeroauth.storage

import dev.zerostudios.zeroauth.ZeroAuthPlugin
import dev.zerostudios.zeroauth.model.LocationData
import dev.zerostudios.zeroauth.model.UserRecord
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Filters
import com.mongodb.client.model.ReplaceOptions
import org.bson.Document
import java.util.UUID

class MongoStorage(private val plugin: ZeroAuthPlugin) : StorageProvider {
    private val client: MongoClient
    private val collection: MongoCollection<Document>

    init {
        val section = plugin.config.getConfigurationSection("storage.mongodb")
            ?: error("Missing storage.mongodb configuration")
        client = MongoClients.create(section.getString("connection-string", "mongodb://localhost:27017")!!)
        val database = client.getDatabase(section.getString("database", "zeroauth")!!)
        collection = database.getCollection(section.getString("collection", "users")!!)
    }

    override fun load(uuid: UUID): UserRecord? {
        val document = collection.find(Filters.eq("uuid", uuid.toString())).limit(1).iterator().use {
            if (it.hasNext()) it.next() else null
        } ?: return null
        val locationDocument = document.get("location") as? Document
        val location = locationDocument?.let {
            val world = it.getString("world") ?: return@let null
            val x = (it.get("x") as? Number)?.toDouble() ?: return@let null
            val y = (it.get("y") as? Number)?.toDouble() ?: return@let null
            val z = (it.get("z") as? Number)?.toDouble() ?: return@let null
            val yaw = (it.get("yaw") as? Number)?.toFloat() ?: 0f
            val pitch = (it.get("pitch") as? Number)?.toFloat() ?: 0f
            LocationData(world, x, y, z, yaw, pitch)
        }
        return UserRecord(uuid, document.getString("passwordHash"), location)
    }

    override fun save(record: UserRecord) {
        val document = Document("uuid", record.uuid.toString()).append("passwordHash", record.passwordHash)
        record.lastLocation?.let {
            document.append("location", Document("world", it.world)
                .append("x", it.x).append("y", it.y).append("z", it.z)
                .append("yaw", it.yaw).append("pitch", it.pitch))
        } ?: document.append("location", null)
        collection.replaceOne(Filters.eq("uuid", record.uuid.toString()), document, ReplaceOptions().upsert(true))
    }

    override fun close() = client.close()
}