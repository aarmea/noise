package com.alternativeinfrastructures.noise.storage;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MessageTypes {
    public static final String TAG = "MessageTypes";

    public static UnknownMessage downcastIfKnown(UnknownMessage message) {
        // Failure to downcast is non-fatal to allow store-and-forward of unknown message types
        if (uuidToClass.containsKey(message.getPublicType())) {
            Class<? extends UnknownMessage> subclass = uuidToClass.get(message.getPublicType());
            if (subclass == null) {
                Log.e(TAG, "Registered message type " + message.getPublicType() + " does not have a corresponding class");
                return null;
            }
            try {
                Constructor<? extends UnknownMessage> constructor = subclass.getDeclaredConstructor(UnknownMessage.class);
                return constructor.newInstance(message);
            } catch (Exception e) {
                Log.e(TAG, "Couldn't downcast UnknownMessage to registered type " + subclass.getName(), e);
                return null;
            }
        } else {
            return null;
        }
    }

    public static UUID get(Class<? extends UnknownMessage> clazz) {
        return classToUuid.get(clazz);
    }

    public static Class<? extends UnknownMessage> get(UUID uuid) {
        return uuidToClass.get(uuid);
    }

    private static final Map<UUID, Class<? extends UnknownMessage>> uuidToClass;
    private static final Map<Class<? extends UnknownMessage>, UUID> classToUuid;

    static {
        uuidToClass = new HashMap<>();
        uuidToClass.put(UUID.fromString("2273200d-3560-4275-adfc-090ba13954d8"), IdentityAnnouncementMessage.class);

        classToUuid = new HashMap<>();
        for (Map.Entry<UUID, Class<? extends UnknownMessage>> entry : uuidToClass.entrySet())
            classToUuid.put(entry.getValue(), entry.getKey());
    }

    // TODO: Interface and storage to register and use third-party types
}
