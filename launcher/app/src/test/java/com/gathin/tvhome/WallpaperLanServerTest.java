package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class WallpaperLanServerTest {
    private static class Store implements WallpaperLanServer.Store {
        final AtomicInteger writes = new AtomicInteger();
        String selected; byte[] uploaded;
        public String list() { return "{\"images\":[]}"; }
        public void upload(InputStream in, long length, String name) throws IOException {
            uploaded = new byte[(int) length]; new DataInputStream(in).readFully(uploaded); writes.incrementAndGet();
        }
        public void delete(String name) { writes.incrementAndGet(); }
        public void select(String name) { selected = name; writes.incrementAndGet(); }
        public void rotation(int minutes) { writes.incrementAndGet(); }
        public byte[] thumbnail(String name) { return new byte[]{1,2,3}; }
    }
    private String request(String url, String method, String suffix, String headers, byte[] body) throws Exception {
        URI uri = new URI(url); try (Socket socket = new Socket(uri.getHost(), uri.getPort())) {
            socket.setSoTimeout(3000);
            String line = method + " " + uri.getRawPath() + suffix + " HTTP/1.1\r\nHost: " + uri.getAuthority() + "\r\n" + headers + "\r\n";
            socket.getOutputStream().write(line.getBytes(StandardCharsets.UTF_8)); if (body != null) socket.getOutputStream().write(body);
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[1024]; int count;
            while ((count = socket.getInputStream().read(buffer)) != -1) out.write(buffer,0,count);
            return out.toString("UTF-8");
        }
    }
    @Test public void qrSessionServesPageAndLibrary() throws Exception {
        Store store = new Store(); try (WallpaperLanServer server = new WallpaperLanServer(store, "扫码管理".getBytes(StandardCharsets.UTF_8))) {
            String url = server.url("127.0.0.1");
            assertTrue(request(url,"GET","","",null).endsWith("扫码管理"));
            assertTrue(request(url,"GET","list","",null).endsWith("{\"images\":[]}"));
            assertTrue(request(url,"GET","list","",null).contains("Cache-Control: no-store"));
        }
    }
    @Test public void missingSessionCannotReadOrDelete() throws Exception {
        Store store = new Store(); try (WallpaperLanServer server = new WallpaperLanServer(store,new byte[0])) {
            URI uri = new URI(server.url("127.0.0.1")); String root = "http://" + uri.getAuthority() + "/";
            assertTrue(request(root,"GET","list","",null).startsWith("HTTP/1.1 403"));
            assertTrue(request(root,"POST","delete?name=x","",null).startsWith("HTTP/1.1 403"));
            assertEquals(0,store.writes.get());
        }
    }
    @Test public void uploadsReadExactBinaryLengthAndDecodeUnicodeNames() throws Exception {
        Store store = new Store(); try (WallpaperLanServer server = new WallpaperLanServer(store,new byte[0])) {
            String url = server.url("127.0.0.1"); byte[] picture = {0,1,13,10,(byte)255,0,3};
            assertTrue(request(url,"POST","upload?name=test.jpg","Content-Length: 7\r\n",picture).startsWith("HTTP/1.1 200"));
            assertArrayEquals(picture,store.uploaded);
            assertTrue(request(url,"POST","select?name=%E9%A3%8E%E6%99%AF.jpg","Content-Length: 0\r\n",null).startsWith("HTTP/1.1 200"));
            assertEquals("风景.jpg",store.selected);
        }
    }
    @Test public void rejectsCrossOriginOversizedAndChunkedWrites() throws Exception {
        Store store = new Store(); try (WallpaperLanServer server = new WallpaperLanServer(store,new byte[0])) {
            String url = server.url("127.0.0.1");
            assertTrue(request(url,"POST","delete?name=x","Origin: http://elsewhere.invalid\r\n",null).startsWith("HTTP/1.1 403"));
            assertTrue(request(url,"POST","upload?name=x","Content-Length: 10485761\r\n",null).startsWith("HTTP/1.1 413"));
            assertTrue(request(url,"POST","upload?name=x","Transfer-Encoding: chunked\r\n",null).startsWith("HTTP/1.1 400"));
            assertEquals(0,store.writes.get());
        }
    }
    @Test public void closingStopsListenerAndSessionChanges() throws Exception {
        Store store = new Store(); WallpaperLanServer server = new WallpaperLanServer(store,new byte[0]);
        String old = server.url("127.0.0.1"); int port = new URI(old).getPort(); server.close();
        try (Socket ignored = new Socket("127.0.0.1",port)) { fail("listener still open"); } catch (IOException expected) { }
        try (WallpaperLanServer next = new WallpaperLanServer(store,new byte[0])) { assertNotEquals(new URI(old).getPath(),new URI(next.url("127.0.0.1")).getPath()); }
    }
}
