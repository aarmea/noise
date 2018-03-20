package com.alternativeinfrastructures.noise.sync;

import android.util.Log;

import com.alternativeinfrastructures.noise.storage.BloomFilter;
import com.alternativeinfrastructures.noise.storage.UnknownMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.schedulers.Schedulers;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class StreamSync {
    public static final String TAG = "StreamSync";

    public static void bidirectionalSync(InputStream inputStream, OutputStream outputStream) {
        Log.d(TAG, "Starting sync");

        // TODO: Set timeouts

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

        final BitSet myMessageVector = BloomFilter.getMessageVectorAsync().blockingGet();
        IOFutures<BitSet> messageVectorFutures = exchangeMessageVectorsAsync(myMessageVector, source, sink, ioExecutors);

        BitSet theirMessageVector;
        try {
            theirMessageVector = messageVectorFutures.get();
        } catch (Exception e) {
            Log.e(TAG, "Failed to exchange message vectors", e);
            return;
        }

        // TODO: Include a subset of the message vector in the broadcast and verify that theirMessageVector matches

        Log.d(TAG, "Exchanged message vectors");
        ioExecutors.shutdown();

        BitSet vectorDifference = BloomFilter.calculateDifference(myMessageVector, theirMessageVector);

        // TODO: Is this I/O as parallel as you think it is? Look into explicitly using separate threads for these
        Flowable<UnknownMessage> myMessages = BloomFilter.getMatchingMessages(vectorDifference);
        sendMessagesAsync(myMessages, sink);

        Flowable<UnknownMessage> theirMessages = receiveMessagesAsync(source);
        theirMessages.subscribe(
                (UnknownMessage message) -> message.saveAsync().subscribe(),
                (Throwable e) -> Log.e(TAG, "Error receiving messages", e));

        // Wait until both complete so that we don't prematurely close the connection
        Log.d(TAG, "Sync completed");
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

        futures.sender = ioExecutors.submit(() -> {
            sink.writeByte(PROTOCOL_NAME.length());
            sink.writeString(PROTOCOL_NAME, DEFAULT_CHARSET);
            sink.flush();
            return null;
        });

        futures.receiver = ioExecutors.submit(() -> {
            byte protocolNameLength = source.readByte();
            String protocolName = source.readString(protocolNameLength, DEFAULT_CHARSET);
            if (!protocolName.equals(PROTOCOL_NAME))
                throw new IOException("Protocol \"" + protocolName + "\" not supported");
            return protocolName;
        });

        return futures;
    }

    static IOFutures<BitSet> exchangeMessageVectorsAsync(
            final BitSet myMessageVector, final BufferedSource source, final BufferedSink sink, ExecutorService ioExecutors) {
        IOFutures<BitSet> futures = new IOFutures<BitSet>();

        futures.sender = ioExecutors.submit(() -> {
            sink.writeByte(Messages.MESSAGE_VECTOR.getValue());
            sink.write(myMessageVector.toByteArray());
            sink.flush();
            return null;
        });

        futures.receiver = ioExecutors.submit(() -> {
            byte messageType = source.readByte();
            // TODO: Make an exception type for protocol errors
            if (messageType != Messages.MESSAGE_VECTOR.getValue())
                throw new IOException("Expected a message vector but got " + messageType);

            byte[] theirMessageVectorByteArray = source.readByteArray(BloomFilter.SIZE_IN_BYTES);
            return BitSet.valueOf(theirMessageVectorByteArray);
        });

        return futures;
    }

    static void sendMessagesAsync(final Flowable<UnknownMessage> myMessages, final BufferedSink sink) {
        Log.d(TAG, "Sending messages");
        myMessages.subscribe((UnknownMessage message) -> {
            sink.writeByte(Messages.MESSAGE.getValue());
            message.writeToSink(sink);
            sink.flush();
        }, (Throwable e) -> {
            Log.e(TAG, "Error sending messages", e);
        }, () -> {
            Log.d(TAG, "Sent messages");
            sink.writeByte(Messages.END.getValue());
            sink.flush();
        });
    }

    static Flowable<UnknownMessage> receiveMessagesAsync(final BufferedSource source) {
        Log.d(TAG, "Receiving messages");
        return Flowable.create((FlowableEmitter<UnknownMessage> messageEmitter) -> {
            int messageCount = 0;
            while (true) {
                byte messageType = source.readByte();
                if (messageType == Messages.END.getValue())
                    break;
                else if (messageType != Messages.MESSAGE.getValue())
                    messageEmitter.onError(new IOException("Expected a message but got " + messageType));

                messageEmitter.onNext(UnknownMessage.fromSource(source));
                ++messageCount;
            }

            messageEmitter.onComplete();
            Log.d(TAG, "Received " + messageCount + " messages");
        }, BackpressureStrategy.BUFFER);
    }
}
