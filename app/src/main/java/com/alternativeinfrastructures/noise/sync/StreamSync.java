package com.alternativeinfrastructures.noise.sync;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class StreamSync {
    public static final String TAG = "StreamSync";

    public static void bidirectionalSync(InputStream inputStream, OutputStream outputStream) {
        Log.d(TAG, "Starting sync");

        final BufferedSource source = Okio.buffer(Okio.source(inputStream));
        final BufferedSink sink = Okio.buffer(Okio.sink(outputStream));
        ExecutorService ioExecutors = Executors.newFixedThreadPool(2); // Separate threads for send and receive

        IOFutures<String> handshakeFutures = handshakeAsync(source, sink, ioExecutors);

        try {
            handshakeFutures.get();
        } catch (Exception e) {
            Log.e(TAG, "Handshake failed", e);
            return;
        }

        Log.d(TAG, "Connected to a peer");


        // TODO: Exchange bit fields (and maybe validate that it matches the one in the broadcast?)
        // TODO: Send and receive individual messages (in separate threads so we can send and receive simultaneously)
        // TODO: Write tests for this class/function
        ioExecutors.shutdown();
    }

    private static final String PROTOCOL_NAME = "Noise0";
    private static final Charset DEFAULT_CHARSET = Charset.forName("US-ASCII");

    private enum Messages {
        MESSAGE_VECTOR((byte) 1),
        MESSAGE((byte) 2),
        END((byte) 3);

        private byte value;

        Messages(byte value) {
            this.value = value;
        }

        byte getValue() {
            return value;
        }
    }

    static class IOFutures<T> {
        public Future<Void> sender;
        public Future<T> receiver;

        public T get() throws InterruptedException, ExecutionException {
            if (sender != null)
                sender.get();

            if (receiver != null)
                return receiver.get();
            else
                return null;
        }
    }

    static IOFutures<String> handshakeAsync(final BufferedSource source, final BufferedSink sink, ExecutorService ioExecutors) {
        if (PROTOCOL_NAME.length() > Byte.MAX_VALUE)
            Log.wtf(TAG, "Protocol name is too long");

        IOFutures<String> futures = new IOFutures<String>();

        futures.sender = ioExecutors.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                sink.writeByte(PROTOCOL_NAME.length());
                sink.writeString(PROTOCOL_NAME, DEFAULT_CHARSET);
                sink.flush();
                return null;
            }
        });

        futures.receiver = ioExecutors.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                byte protocolNameLength = source.readByte();
                String protocolName = source.readString(protocolNameLength, DEFAULT_CHARSET);
                if (!protocolName.equals(PROTOCOL_NAME))
                    throw new IOException("Protocol \"" + protocolName + "\" not supported");
                return protocolName;
            }
        });

        return futures;
    }
}
