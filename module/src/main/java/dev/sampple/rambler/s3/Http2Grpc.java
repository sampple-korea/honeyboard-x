package dev.sampple.rambler.s3;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** One-stream TLS HTTP/2 gRPC transport. The reader runs independently of the audio sender. */
final class Http2Grpc implements AutoCloseable {
    interface Receiver { void message(byte[] data) throws IOException; }
    private final String host;
    private final int port;
    private final String apiKey, packageName, cert;
    private final Receiver receiver;
    private final Object socketLock = new Object();
    private Socket ownedSocket;
    private SSLSocket socket;
    private InputStream input;
    private OutputStream output;
    private final Hpack hpack = new Hpack();
    private final Object writeLock = new Object(), flowLock = new Object();
    private int connectionWindow = 65535, streamWindow = 65535, initialStreamWindow = 65535, maxFrame = 16384;
    private volatile boolean closed;
    private boolean gotTrailers;
    private int grpcStatus = -1;
    private String grpcMessage = "";
    private int httpStatus = -1;
    private final StringBuilder responseHeaders = new StringBuilder();
    private byte[] pendingHeaders;
    private boolean pendingEndStream;
    private final ByteArrayOutputStream inbound = new ByteArrayOutputStream();
    Http2Grpc(S3Session.Config config, Receiver receiver) {
        this.host = config.host; this.port = config.port;
        this.apiKey = config.apiKey; this.packageName = config.androidPackage;
        this.cert = config.androidCert; this.receiver = receiver;
    }
    void connect() throws IOException {
        SSLSocketFactory factory;
        try { factory = SSLContext.getDefault().getSocketFactory(); }
        catch (Exception e) { throw new IOException("TLS initialization failed", e); }
        // Publish before connecting so cancel() also interrupts an in-flight TCP connect.
        // Platform DNS lookup itself is not interruptible; cancellation is checked after it.
        Socket tcp = new Socket();
        synchronized (socketLock) {
            if (closed) { tcp.close(); throw new IOException("HTTP/2 stream closed"); }
            ownedSocket = tcp;
        }
        try {
            InetSocketAddress address = new InetSocketAddress(host, port);
            if (closed) throw new IOException("HTTP/2 stream closed");
            tcp.connect(address, 15000);
            SSLSocket tls = (SSLSocket)factory.createSocket(tcp, host, port, true);
            synchronized (socketLock) {
                if (closed) { tls.close(); throw new IOException("HTTP/2 stream closed"); }
                ownedSocket = tls;
                socket = tls;
            }
        } catch (IOException | RuntimeException error) {
            try { tcp.close(); } catch (IOException ignored) { }
            throw error;
        }
        socket.setSoTimeout(120000);
        SSLParameters params = socket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        params.setApplicationProtocols(new String[]{"h2"});
        socket.setSSLParameters(params);
        socket.startHandshake();
        if (!"h2".equals(socket.getApplicationProtocol())) throw new IOException("server did not negotiate h2 ALPN");
        input = socket.getInputStream(); output = socket.getOutputStream();
        synchronized (writeLock) {
            output.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            frame(4, 0, 0, new byte[0], 0, 0);
            String[][] headers = {
                {":method", "POST"}, {":scheme", "https"}, {":path", "/speech.s3.S3StubbyClientConnectorService/S3Bidi"},
                {":authority", host + ":" + port}, {"content-type", "application/grpc"},
                {"te", "trailers"}, {"user-agent", "grpc-java-rambler/1.0"},
                {"x-goog-api-key", apiKey}, {"x-android-package", packageName}, {"x-android-cert", cert}
            };
            byte[] b = Hpack.encode(headers);
            // Our request header block is smaller than the initial 16 KiB frame size.
            frame(1, 4, 1, b, 0, b.length);
        }
    }
    void send(byte[] protobuf, boolean endStream) throws IOException {
        if (protobuf.length > 2_000_000) throw new IOException("gRPC message too large");
        byte[] bytes = new byte[protobuf.length + 5];
        int n = protobuf.length;
        bytes[1] = (byte)(n >>> 24); bytes[2] = (byte)(n >>> 16);
        bytes[3] = (byte)(n >>> 8); bytes[4] = (byte)n;
        System.arraycopy(protobuf, 0, bytes, 5, n);
        int pos = 0;
        while (pos < bytes.length) {
            int count;
            synchronized (flowLock) {
                while (!closed && (connectionWindow <= 0 || streamWindow <= 0)) {
                    try { flowLock.wait(120000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("interrupted waiting for window", e); }
                }
                if (closed) throw new IOException("HTTP/2 stream closed");
                count = Math.min(bytes.length - pos, Math.min(maxFrame, Math.min(connectionWindow, streamWindow)));
                connectionWindow -= count; streamWindow -= count;
            }
            synchronized (writeLock) { frame(0, endStream && pos + count == bytes.length ? 1 : 0, 1, bytes, pos, count); }
            pos += count;
        }
    }
    void read() throws IOException {
        while (!closed) {
            byte[] head = exact(9);
            int length = ((head[0] & 255) << 16) | ((head[1] & 255) << 8) | (head[2] & 255);
            int type = head[3] & 255, flags = head[4] & 255;
            int stream = ((head[5] & 127) << 24) | ((head[6] & 255) << 16) | ((head[7] & 255) << 8) | (head[8] & 255);
            if (length > 1_048_576) throw new IOException("HTTP/2 frame too large: " + length);
            byte[] data = exact(length);
            if (pendingHeaders != null && type != 9) throw new IOException("expected CONTINUATION frame");
            switch (type) {
                case 0: // DATA
                    if (stream != 1) break;
                    int start = (flags & 8) != 0 ? 1 : 0;
                    int pad = (flags & 8) != 0 ? data[0] & 255 : 0;
                    if (start + pad > length) throw new IOException("invalid DATA padding");
                    inbound.write(data, start, length - start - pad);
                    if (inbound.size() > 4_000_000) throw new IOException("incoming gRPC data too large");
                    drainGrpc();
                    // Credit all received bytes, including padding.
                    synchronized (writeLock) { if (length > 0) { windowUpdate(0, length); windowUpdate(1, length); } }
                    if ((flags & 1) != 0) finished();
                    break;
                case 1: // HEADERS
                    if (stream != 1) break;
                    int hStart = (flags & 8) != 0 ? 1 : 0;
                    int hPad = (flags & 8) != 0 ? data[0] & 255 : 0;
                    if ((flags & 32) != 0) hStart += 5;
                    if (hStart + hPad > length) throw new IOException("invalid HEADERS padding");
                    pendingHeaders = new byte[length - hStart - hPad];
                    System.arraycopy(data, hStart, pendingHeaders, 0, pendingHeaders.length);
                    pendingEndStream = (flags & 1) != 0;
                    if ((flags & 4) != 0) completeHeaders();
                    break;
                case 9: // CONTINUATION
                    if (stream != 1 || pendingHeaders == null) throw new IOException("unexpected CONTINUATION");
                    if (pendingHeaders.length + length > 65536) throw new IOException("header block too large");
                    byte[] combined = new byte[pendingHeaders.length + length];
                    System.arraycopy(pendingHeaders, 0, combined, 0, pendingHeaders.length);
                    System.arraycopy(data, 0, combined, pendingHeaders.length, length);
                    pendingHeaders = combined;
                    if ((flags & 4) != 0) completeHeaders();
                    break;
                case 4: // SETTINGS
                    if (stream != 0) throw new IOException("SETTINGS on stream");
                    if ((flags & 1) != 0) { if (length != 0) throw new IOException("SETTINGS ACK with payload"); break; }
                    if (length % 6 != 0) throw new IOException("invalid SETTINGS");
                    for (int p = 0; p < length; p += 6) {
                        int id = ((data[p] & 255) << 8) | (data[p + 1] & 255);
                        long val = ((data[p + 2] & 255L) << 24) | ((data[p + 3] & 255L) << 16)
                                | ((data[p + 4] & 255L) << 8) | (data[p + 5] & 255L);
                        if (id == 1) { /* Peer limit applies to our encoder, which never indexes. */ }
                        else if (id == 4) {
                            if (val > Integer.MAX_VALUE) throw new IOException("invalid initial window");
                            synchronized (flowLock) { streamWindow += (int)val - initialStreamWindow; initialStreamWindow = (int)val; flowLock.notifyAll(); }
                        } else if (id == 5) {
                            if (val < 16384 || val > 16777215) throw new IOException("invalid max frame size");
                            synchronized (flowLock) { maxFrame = (int)val; }
                        }
                    }
                    synchronized (writeLock) { frame(4, 1, 0, new byte[0], 0, 0); }
                    break;
                case 6: // PING
                    if (stream != 0 || length != 8) throw new IOException("invalid PING");
                    if ((flags & 1) == 0) synchronized (writeLock) { frame(6, 1, 0, data, 0, 8); }
                    break;
                case 8: // WINDOW_UPDATE
                    if (length != 4) throw new IOException("invalid WINDOW_UPDATE");
                    int increment = ((data[0] & 127) << 24) | ((data[1] & 255) << 16)
                            | ((data[2] & 255) << 8) | (data[3] & 255);
                    if (increment == 0) throw new IOException("zero WINDOW_UPDATE");
                    synchronized (flowLock) {
                        if (stream == 0) {
                            if (connectionWindow > Integer.MAX_VALUE - increment) throw new IOException("connection flow window overflow");
                            connectionWindow += increment;
                        } else if (stream == 1) {
                            if (streamWindow > Integer.MAX_VALUE - increment) throw new IOException("stream flow window overflow");
                            streamWindow += increment;
                        }
                        flowLock.notifyAll();
                    }
                    break;
                case 3: // RST_STREAM
                    if (stream == 1) throw new IOException("HTTP/2 RST_STREAM error=" + word(data, 0) + " headers=" + responseHeaders);
                    break;
                case 7: // GOAWAY
                    throw new IOException("HTTP/2 GOAWAY error=" + word(data, 4) + " debug="
                            + new String(data, Math.min(8, data.length), Math.max(0, data.length - 8), StandardCharsets.UTF_8)
                            + " headers=" + responseHeaders);
                default: break;
            }
        }
    }
    private void completeHeaders() throws IOException {
        List<String[]> headers = hpack.decode(pendingHeaders);
        pendingHeaders = null;
        for (String[] h : headers) {
            responseHeaders.append(h[0]).append(": ").append(h[1]).append('\n');
            if (h[0].equals(":status")) {
                try { httpStatus = Integer.parseInt(h[1]); }
                catch (NumberFormatException e) { throw new IOException("invalid HTTP status", e); }
            }
            if (h[0].equals("grpc-status")) {
                gotTrailers = true;
                try { grpcStatus = Integer.parseInt(h[1]); }
                catch (NumberFormatException e) { throw new IOException("invalid grpc-status", e); }
            }
            if (h[0].equals("grpc-message")) grpcMessage = h[1];
        }
        if (pendingEndStream) finished();
    }
    private void finished() throws IOException {
        if (inbound.size() != 0) throw new IOException("truncated gRPC message");
        if (!gotTrailers || grpcStatus != 0 || httpStatus != 200)
            throw new IOException("HTTP status=" + httpStatus + " grpc-status=" + grpcStatus + " grpc-message="
                    + grpcMessage + " headers/trailers:\n" + responseHeaders);
        closed = true;
        synchronized (flowLock) { flowLock.notifyAll(); }
    }
    private void drainGrpc() throws IOException {
        byte[] b = inbound.toByteArray();
        int p = 0;
        while (b.length - p >= 5) {
            if (b[p] != 0) throw new IOException("compressed gRPC message unsupported");
            long n = ((b[p+1] & 255L) << 24) | ((b[p+2] & 255L) << 16) | ((b[p+3] & 255L) << 8) | (b[p+4] & 255L);
            if (n > 2_000_000) throw new IOException("gRPC message exceeds limit");
            if (b.length - p - 5 < n) break;
            byte[] message = new byte[(int)n]; System.arraycopy(b, p+5, message, 0, (int)n);
            receiver.message(message); p += 5 + (int)n;
        }
        if (p > 0) { inbound.reset(); inbound.write(b, p, b.length-p); }
    }
    private static int word(byte[] b, int off) {
        if (b.length < off + 4) return -1;
        return ((b[off] & 255) << 24) | ((b[off+1] & 255) << 16) | ((b[off+2] & 255) << 8) | (b[off+3] & 255);
    }
    private void windowUpdate(int stream, int inc) throws IOException {
        byte[] b = {(byte)(inc >>> 24), (byte)(inc >>> 16), (byte)(inc >>> 8), (byte)inc};
        frame(8, 0, stream, b, 0, 4);
    }
    private void frame(int type, int flags, int stream, byte[] data, int offset, int length) throws IOException {
        if (closed) throw new IOException("HTTP/2 stream closed");
        byte[] h = {(byte)(length >>> 16), (byte)(length >>> 8), (byte)length, (byte)type, (byte)flags,
                (byte)(stream >>> 24), (byte)(stream >>> 16), (byte)(stream >>> 8), (byte)stream};
        output.write(h); output.write(data, offset, length); output.flush();
    }
    private byte[] exact(int length) throws IOException {
        byte[] b = new byte[length]; int p = 0;
        while (p < length) { int n = input.read(b, p, length-p); if (n < 0) throw new EOFException("EOF before gRPC trailers; headers=" + responseHeaders); p += n; }
        return b;
    }
    @Override public void close() {
        Socket owned;
        synchronized (socketLock) {
            closed = true;
            owned = ownedSocket;
            ownedSocket = null;
        }
        synchronized (flowLock) { flowLock.notifyAll(); }
        if (owned != null) try { owned.close(); } catch (IOException ignored) { }
    }
}
