package com.gathin.tvhome;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Short-lived LAN server. Every endpoint belongs to the unguessable QR session path. */
final class WallpaperLanServer implements Closeable {
    static final long MAX_UPLOAD = 10L * 1024 * 1024;
    interface Store {
        String list() throws Exception;
        void upload(InputStream input, long length, String name) throws Exception;
        void delete(String name) throws Exception;
        void select(String name) throws Exception;
        void rotation(int minutes) throws Exception;
        byte[] thumbnail(String name) throws Exception;
        default File original(String name) throws Exception { throw new IOException("原图暂不可用"); }
    }
    private final Store store;
    private final byte[] page;
    private final ServerSocket listener;
    private final ExecutorService clients;
    private final String prefix;
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    WallpaperLanServer(Store store, byte[] page) throws IOException {
        this.store = store; this.page = page;
        byte[] secret = new byte[18]; new SecureRandom().nextBytes(secret);
        StringBuilder token = new StringBuilder(); for (byte n : secret) token.append(String.format(Locale.ROOT, "%02x", n & 255));
        prefix = "/" + token + "/";
        listener = new ServerSocket(); listener.bind(new InetSocketAddress(0), 8);
        clients = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), r -> {
            Thread thread = new Thread(r, "WallpaperClient"); thread.setDaemon(true); return thread;
        });
        Thread accept = new Thread(() -> {
            while (!closed) try {
                Socket socket = listener.accept(); socket.setSoTimeout(15000); connections.add(socket);
                try { clients.execute(() -> serve(socket)); } catch (RejectedExecutionException full) { connections.remove(socket); socket.close(); }
            } catch (IOException ignored) { if (closed) break; }
        }, "WallpaperLAN"); accept.setDaemon(true); accept.start();
    }
    String url(String address) { return "http://" + address + ":" + listener.getLocalPort() + prefix; }
    @Override public void close() throws IOException { closed = true; listener.close(); for (Socket socket : connections) { try { socket.close(); } catch (IOException ignored) { } } connections.clear(); clients.shutdownNow(); }
    private void serve(Socket socket) {
        try (Socket peer = socket) {
            InputStream input = new BufferedInputStream(peer.getInputStream()); OutputStream output = peer.getOutputStream();
            try {
                String first = line(input); String[] request = first.split(" ");
                if (request.length != 3) throw new IOException("请求格式错误");
                Map<String, String> headers = new HashMap<>(); int size = first.length();
                while (true) {
                    String header = line(input); size += header.length(); if (size > 12000) throw new IOException("请求头过长");
                    if (header.isEmpty()) break;
                    int colon = header.indexOf(':'); if (colon < 1) throw new IOException("请求格式错误");
                    String key = header.substring(0, colon).toLowerCase(Locale.ROOT);
                    if (headers.put(key, header.substring(colon + 1).trim()) != null) throw new IOException("重复请求头");
                }
                URI uri = new URI(request[1]); String path = uri.getPath();
                if (path == null || !path.startsWith(prefix)) { reply(output, 403, "text/plain", bytes("请扫描电视上的二维码")); return; }
                String route = path.substring(prefix.length()); Map<String,String> query = query(uri.getRawQuery());
                if ("GET".equals(request[0])) {
                    if (route.isEmpty()) reply(output, 200, "text/html", page);
                    else if (route.equals("list")) reply(output, 200, "application/json", bytes(store.list()));
                    else if (route.equals("image")) {
                        File image = store.original(query.get("name"));
                        String extension = image.getName().toLowerCase(Locale.ROOT);
                        String type = extension.endsWith(".png") ? "image/png" : extension.endsWith(".webp") ? "image/webp" : "image/jpeg";
                        output.write(bytes("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\nContent-Length: " + image.length()
                                + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n"));
                        try (InputStream file = new FileInputStream(image)) { byte[] buffer = new byte[32768]; int n;
                            while ((n = file.read(buffer)) != -1) output.write(buffer, 0, n); }
                        output.flush();
                    }
                    else if (route.equals("thumb")) reply(output, 200, "image/jpeg", store.thumbnail(query.get("name")));
                    else reply(output, 404, "text/plain", bytes("页面不存在"));
                } else if ("POST".equals(request[0])) {
                    String origin = headers.get("origin");
                    if (origin != null && !origin.equals("http://" + headers.get("host"))) {
                        reply(output, 403, "text/plain", bytes("请求来源不匹配")); return;
                    }
                    if (headers.containsKey("transfer-encoding")) throw new IOException("不支持分块上传");
                    long length = Long.parseLong(headers.getOrDefault("content-length", "0"));
                    if (length < 0 || length > MAX_UPLOAD) { reply(output, 413, "text/plain", bytes("单张图片不能超过 10 MB")); return; }
                    if (route.equals("upload")) { if (length == 0) throw new IOException("请选择图片"); store.upload(input, length, query.get("name")); }
                    else {
                        if (length != 0) throw new IOException("请求格式错误");
                        if (route.equals("delete")) store.delete(query.get("name"));
                        else if (route.equals("select")) store.select(query.get("name"));
                        else if (route.equals("rotation")) store.rotation(Integer.parseInt(query.getOrDefault("minutes", "0")));
                        else { reply(output, 404, "text/plain", bytes("页面不存在")); return; }
                    }
                    reply(output, 200, "application/json", bytes("{\"ok\":true}"));
                } else reply(output, 405, "text/plain", bytes("不支持的操作"));
            } catch (Exception failure) { reply(output, 400, "text/plain", bytes(failure.getMessage() == null ? "操作失败，请重试" : failure.getMessage())); }
        } catch (IOException ignored) { } finally { connections.remove(socket); }
    }
    static Map<String,String> query(String raw) throws IOException {
        Map<String,String> result = new HashMap<>(); if (raw == null) return result;
        for (String pair : raw.split("&")) { String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], "UTF-8"), parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : ""); }
        return result;
    }
    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); int n;
        while ((n = input.read()) != -1) {
            if (n == '\n') return buffer.toString("US-ASCII").replace("\r", "");
            if (buffer.size() >= 4096) throw new IOException("请求头过长"); buffer.write(n);
        }
        throw new EOFException("连接已关闭");
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static void reply(OutputStream out, int status, String type, byte[] body) throws IOException {
        String headers = "HTTP/1.1 " + status + " Result\r\nContent-Type: " + type + "; charset=utf-8\r\nContent-Length: " + body.length
                + "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n"
                + "Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' blob:; frame-ancestors 'none'\r\n\r\n";
        out.write(bytes(headers)); out.write(body); out.flush();
    }
}
