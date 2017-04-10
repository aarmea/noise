package com.alternativeinfrastructures.noise.sync;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class StreamSync {
    public static final String TAG = "StreamSync";

    public enum Role {
        CLIENT,
        SERVER
    }

    public static void bidirectionalSync(Role role, InputStream inputStream, OutputStream outputStream) {
        BufferedSource source = Okio.buffer(Okio.source(inputStream));
        BufferedSink sink = Okio.buffer(Okio.sink(outputStream));

        try {
            handshake(role, source, sink);
        } catch (IOException ioException) {
            Log.e(TAG, "IOException during handshake", ioException);
            return;
        }

        Log.d(TAG, "Connected to a " + (role == Role.CLIENT ? "server" : "client"));

        // TODO: Exchange bit fields (and maybe validate that it matches the one in the broadcast?)
        // TODO: Send and receive individual messages (in separate threads so we can send and receive simultaneously)
    }

    private static final String PROTOCOL_NAME = "Noise0";
    private static final Charset DEFAULT_CHARSET = Charset.forName("US-ASCII");

    private enum Messages {
        SERVER_READY((byte) 1),
        CLIENT_READY((byte) 2);

        private byte value;

        Messages(byte value) {
            this.value = value;
        }

        byte getValue() {
            return value;
        }
    }

    private static void handshake(Role role, BufferedSource source, BufferedSink sink) throws IOException {
        if (PROTOCOL_NAME.length() > Byte.MAX_VALUE)
            Log.wtf(TAG, "Protocol name is too long");

        if (role == Role.SERVER) {
            sink.writeByte(PROTOCOL_NAME.length());
            sink.writeString(PROTOCOL_NAME, DEFAULT_CHARSET);

            sink.writeByte(Messages.SERVER_READY.getValue());
            sink.flush();
        }

        if (role == Role.CLIENT) {
            byte protocolNameLength = source.readByte();
            String protocolName = source.readString(protocolNameLength, DEFAULT_CHARSET);
            if (!protocolName.equals(PROTOCOL_NAME))
                throw new IOException("Protocol \"" + protocolName + "\" not supported");

            byte message = source.readByte();
            if (message != Messages.SERVER_READY.getValue())
                throw new IOException("Client expected SERVER_READY but received " + message);

            sink.writeByte(Messages.CLIENT_READY.getValue());
            sink.flush();
        }

        if (role == Role.SERVER) {
            byte message = source.readByte();
            if (message != Messages.CLIENT_READY.getValue())
                throw new IOException("Server expected CLIENT_READY but received " + message);
        }
    }
}
