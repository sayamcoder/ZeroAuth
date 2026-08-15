package dev.zerostudios.zeroauth.storage

import dev.zerostudios.zeroauth.model.UserRecord

import java.util.UUID

interface StorageProvider {
    fun load(uuid: UUID): UserRecord?
    fun save(record: UserRecord)
    fun close()
}