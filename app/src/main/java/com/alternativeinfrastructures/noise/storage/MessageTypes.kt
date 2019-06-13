package com.alternativeinfrastructures.noise.storage

import android.util.Log

import java.lang.reflect.Constructor
import java.util.HashMap
import java.util.UUID

object MessageTypes {
    val TAG = "MessageTypes"

    private val uuidToClass: MutableMap<UUID, Class<out UnknownMessage>>
    private val classToUuid: MutableMap<Class<out UnknownMessage>, UUID>

    fun downcastIfKnown(message: UnknownMessage): UnknownMessage? {
        // Failure to downcast is non-fatal to allow store-and-forward of unknown message types
        if (uuidToClass.containsKey(message.publicType)) {
            val subclass = uuidToClass[message.publicType]
            if (subclass == null) {
                Log.e(TAG, "Registered message type " + message.publicType + " does not have a corresponding class")
                return null
            }
            try {
                val constructor = subclass.getDeclaredConstructor(UnknownMessage::class.java)
                return constructor.newInstance(message)
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't downcast UnknownMessage to registered type " + subclass.name, e)
                return null
            }

        } else {
            return null
        }
    }

    operator fun get(clazz: Class<out UnknownMessage>): UUID? {
        return classToUuid[clazz]
    }

    operator fun get(uuid: UUID): Class<out UnknownMessage>? {
        return uuidToClass[uuid]
    }

    init {
        uuidToClass = HashMap()
        uuidToClass[UUID.fromString("2273200d-3560-4275-adfc-090ba13954d8")] = IdentityAnnouncementMessage::class.java

        classToUuid = HashMap()
        for ((key, value) in uuidToClass)
            classToUuid[value] = key
    }

    // TODO: Interface and storage to register and use third-party types
}
