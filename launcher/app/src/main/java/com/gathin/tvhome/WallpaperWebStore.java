package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/** Only the launcher's wallpaper files are exposed; uploads are validated before becoming visible. */
final class WallpaperWebStore implements WallpaperLanServer.Store {
    private final Context context;
    private final File directory, root;
    WallpaperWebStore(Context context) throws IOException {
        this.context = context.getApplicationContext(); root = Config.dir(context);
        if (root == null) throw new IOException("电视存储暂不可用");
        directory = new File(root, "wallpapers");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建壁纸目录");
    }
    private SharedPreferences choices() { return context.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE); }
    private List<File> files() {
        List<File> list = new ArrayList<>(WallpaperRotation.images(directory));
        File single = new File(root, Config.WALLPAPER_NAME);
        if (single.isFile() && single.length() > 0) list.add(0, single);
        return list;
    }
    private File find(String name) throws IOException {
        if (name == null || name.contains("/") || name.contains("\\") || name.indexOf(0) >= 0) throw new IOException("壁纸名称无效");
        for (File file : files()) if (file.getName().equals(name)) return file;
        throw new IOException("壁纸已不存在，请刷新列表");
    }
    @Override public synchronized String list() throws Exception {
        JSONObject json = new JSONObject(); JSONArray images = new JSONArray();
        for (File file : files()) images.put(new JSONObject().put("name", file.getName()).put("bytes", file.length()));
        return json.put("images", images).put("selected", choices().getString(Config.WALLPAPER_CHOICE_KEY, ""))
                .put("minutes", context.getSharedPreferences(WallpaperPlayer.KEY_PREFS, Context.MODE_PRIVATE).getInt(WallpaperPlayer.KEY_MINUTES, 0))
                .put("free", directory.getUsableSpace()).toString();
    }
    @Override public synchronized void upload(InputStream input, long length, String original) throws Exception {
        if (files().size() >= 100) throw new IOException("最多保留 100 张壁纸，请先删除部分图片");
        if (length <= 0 || length > WallpaperLanServer.MAX_UPLOAD) throw new IOException("单张图片不能超过 10 MB");
        if (directory.getUsableSpace() < length + 20L * 1024 * 1024) throw new IOException("电视存储不足，请先删除部分壁纸");
        String name = original == null ? "wallpaper" : original;
        int dot = name.lastIndexOf('.'); if (dot > 0) name = name.substring(0, dot);
        name = name.replaceAll("[^\\p{L}\\p{N}_-]", "_");
        if (name.isEmpty()) name = "wallpaper"; if (name.length() > 50) name = name.substring(0, 50);
        File temporary = File.createTempFile("upload-", ".part", directory);
        try {
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[32768]; long remaining = length;
                while (remaining > 0) { int n = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (n < 0) throw new EOFException("上传中断，请重试"); out.write(buffer, 0, n); remaining -= n; }
            }
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(temporary.getPath(), options);
            String extension;
            if ("image/jpeg".equals(options.outMimeType)) extension = ".jpg";
            else if ("image/png".equals(options.outMimeType)) extension = ".png";
            else if ("image/webp".equals(options.outMimeType)) extension = ".webp";
            else throw new IOException("请选择 JPG、PNG 或 WebP 图片");
            if (options.outWidth < 1 || options.outHeight < 1 || options.outWidth > 32768 || options.outHeight > 32768)
                throw new IOException("图片尺寸无效或过大");
            options.inJustDecodeBounds = false; options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
            Bitmap check = BitmapFactory.decodeFile(temporary.getPath(), options);
            if (check == null) throw new IOException("图片损坏，无法读取"); check.recycle();
            File target = new File(directory, name + "-" + UUID.randomUUID().toString().substring(0, 8) + extension);
            if (!temporary.renameTo(target)) throw new IOException("保存失败，请重试");
            changed();
        } finally { if (temporary.exists()) temporary.delete(); }
    }
    @Override public synchronized void delete(String name) throws Exception {
        File file = find(name); if (!file.delete()) throw new IOException("删除失败，请重试");
        if (name.equals(choices().getString(Config.WALLPAPER_CHOICE_KEY, ""))) choices().edit().remove(Config.WALLPAPER_CHOICE_KEY).commit();
        changed();
    }
    @Override public synchronized void select(String name) throws Exception {
        if (name == null) throw new IOException("请选择壁纸");
        if (name.isEmpty()) choices().edit().remove(Config.WALLPAPER_CHOICE_KEY).commit();
        else { if (!"none".equals(name)) find(name); choices().edit().putString(Config.WALLPAPER_CHOICE_KEY, name).commit(); }
        changed();
    }
    @Override public synchronized void rotation(int minutes) throws Exception {
        if (minutes < 0 || minutes > 10) throw new IOException("轮换间隔应为 1–10 分钟");
        context.getSharedPreferences(WallpaperPlayer.KEY_PREFS, Context.MODE_PRIVATE).edit().putInt(WallpaperPlayer.KEY_MINUTES, minutes).commit();
        changed();
    }
    @Override public synchronized byte[] thumbnail(String name) throws Exception {
        File file = find(name); BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options); options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 480) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        Bitmap image = BitmapFactory.decodeFile(file.getPath(), options);
        if (image == null) throw new IOException("图片无法预览");
        try { ByteArrayOutputStream out = new ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.JPEG, 80, out); return out.toByteArray(); }
        finally { image.recycle(); }
    }
    private void changed() { SharedPreferences prefs = choices(); prefs.edit().putLong(Config.SETTINGS_REVISION_KEY,
            prefs.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).commit(); }
}
