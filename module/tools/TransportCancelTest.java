package dev.sampple.rambler.s3;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Offline regression: cancellation before connect and during a stalled TLS handshake. */
public final class TransportCancelTest {
    public static void main(String[] args) throws Exception {
        S3Session.Config config = new S3Session.Config();
        config.host = "127.0.0.1";
        Http2Grpc cancelled = new Http2Grpc(config, bytes -> {});
        cancelled.close();
        try {
            cancelled.connect();
            throw new AssertionError("connect after close succeeded");
        } catch (java.io.IOException expected) { }
        cancelled.close();
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            config.port = server.getLocalPort();
            Http2Grpc transport = new Http2Grpc(config, bytes -> {});
            CountDownLatch stopped = new CountDownLatch(1);
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            Thread connecting = new Thread(() -> {
                try { transport.connect(); outcome.set(new AssertionError("unexpected TLS success")); }
                catch (Throwable error) { outcome.set(error); }
                finally { transport.close(); stopped.countDown(); }
            }, "transport-cancel-test");
            connecting.setDaemon(true);
            connecting.start();
            try (Socket peer = server.accept()) {
                // Read part of ClientHello to prove ownership has moved to the TLS socket.
                peer.setSoTimeout(5000);
                if (peer.getInputStream().read() < 0) throw new AssertionError("no TLS hello");
                transport.close();
                transport.close();
                if (!stopped.await(5, TimeUnit.SECONDS)) throw new AssertionError("connect did not stop");
                if (!(outcome.get() instanceof java.io.IOException)) throw new AssertionError(outcome.get());
            }
        }
        System.out.println("PASS: pre-connect cancellation, TLS-handshake cancellation, repeated close");
    }
}
