package dev.zerostudios.zeroauth.storage

import dev.zerostudios.zeroauth.ZeroAuthPlugin

object StorageFactory {
    fun create(plugin: ZeroAuthPlugin): StorageProvider {
        val type = plugin.config.getString("storage.type", "flatfile")!!.lowercase()
        return try {
            when (type) {
                "mysql", "postgresql", "sqlite" -> JdbcStorage(plugin, type)
                "mongodb", "mongo" -> MongoStorage(plugin)
                "flatfile", "yaml" -> FlatFileStorage(plugin)
                else -> error("Unknown storage type '$type'")
            }
        } catch (exception: Exception) {
            plugin.logger.severe("Could not initialize '$type' storage: ${exception.message}")
            plugin.logger.warning("Falling back to flat-file storage.")
            FlatFileStorage(plugin)
        } catch (error: LinkageError) {
            plugin.logger.severe("Storage driver for '$type' is unavailable: ${error.message}")
            plugin.logger.warning("Falling back to flat-file storage.")
            FlatFileStorage(plugin)
        }
    }
}