package com.jans.music;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Base64;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@CapacitorPlugin(name = "JansMedia", permissions = {
    @Permission(alias = "audio", strings = { Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_EXTERNAL_STORAGE })
})
public class JansMediaPlugin extends Plugin {
    @PluginMethod
    public void scanLibrary(PluginCall call) {
        if (getPermissionState("audio") != PermissionState.GRANTED) {
            requestPermissionForAlias("audio", call, "permissionResult");
            return;
        }
        doScan(call);
    }

    @PermissionCallback
    private void permissionResult(PluginCall call) {
        if (getPermissionState("audio") == PermissionState.GRANTED) doScan(call);
        else call.reject("Se necesita permiso para leer los archivos de audio del dispositivo.");
    }

    private String col(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        return i >= 0 && !c.isNull(i) ? c.getString(i) : "";
    }
    private long num(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        return i >= 0 && !c.isNull(i) ? c.getLong(i) : 0L;
    }
    private String base(String s) {
        String name = s == null ? "" : s;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.toLowerCase(Locale.ROOT).trim();
    }
    private String parent(String s) {
        if (s == null) return "";
        int i = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
        return i >= 0 ? s.substring(0, i + 1).toLowerCase(Locale.ROOT) : "";
    }
    private String readText(Uri uri) {
        try (InputStream in = getContext().getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) return "";
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) > 0 && out.size() < 1024 * 1024) out.write(b, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) { return ""; }
    }
    private Map<String, String> findLyrics() {
        Map<String, String> map = new HashMap<>();
        ContentResolver cr = getContext().getContentResolver();
        Uri files = MediaStore.Files.getContentUri("external");
        String[] projection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? new String[]{ MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.RELATIVE_PATH, MediaStore.Files.FileColumns.DATA }
            : new String[]{ MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.DATA };
        try (Cursor c = cr.query(files, projection, "LOWER(" + MediaStore.Files.FileColumns.DISPLAY_NAME + ") LIKE ?", new String[]{"%.lrc"}, null)) {
            if (c != null) while (c.moveToNext()) {
                String display = col(c, MediaStore.Files.FileColumns.DISPLAY_NAME);
                String rel = col(c, MediaStore.Files.FileColumns.RELATIVE_PATH);
                if (rel.isEmpty()) rel = parent(col(c, MediaStore.Files.FileColumns.DATA));
                long id = num(c, MediaStore.Files.FileColumns._ID);
                Uri uri = ContentUris.withAppendedId(files, id);
                String text = readText(uri);
                String key = (rel.toLowerCase(Locale.ROOT) + "|" + base(display));
                map.put(key, text);
                // Fallback for libraries where the audio and LRC relative path is not exposed consistently.
                map.putIfAbsent("*|" + base(display), text);
            }
        } catch (Exception ignored) { }
        return map;
    }

    private void doScan(PluginCall call) {
        JSArray songs = new JSArray();
        ContentResolver cr = getContext().getContentResolver();
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? new String[]{ MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DATA }
            : new String[]{ MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATA };
        Map<String, String> lyrics = findLyrics();
        int count = 0;
        try (Cursor c = cr.query(collection, projection, null, null, MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")) {
            if (c != null) while (c.moveToNext()) {
                long id = num(c, MediaStore.Audio.Media._ID);
                String display = col(c, MediaStore.Audio.Media.DISPLAY_NAME);
                String title = col(c, MediaStore.Audio.Media.TITLE);
                String artist = col(c, MediaStore.Audio.Media.ARTIST);
                String album = col(c, MediaStore.Audio.Media.ALBUM);
                String relative = col(c, MediaStore.Audio.Media.RELATIVE_PATH);
                if (relative.isEmpty()) relative = parent(col(c, MediaStore.Audio.Media.DATA));
                Uri uri = ContentUris.withAppendedId(collection, id);
                String lrc = lyrics.getOrDefault(relative.toLowerCase(Locale.ROOT) + "|" + base(display), lyrics.getOrDefault("*|" + base(display), ""));
                JSObject item = new JSObject();
                item.put("uri", uri.toString()); item.put("title", title.isEmpty() ? base(display) : title);
                item.put("artist", artist); item.put("album", album); item.put("displayName", display);
                item.put("relativePath", relative); item.put("duration", num(c, MediaStore.Audio.Media.DURATION));
                item.put("size", num(c, MediaStore.Audio.Media.SIZE)); item.put("lrcText", lrc);
                songs.put(item); count++;
            }
            JSObject ret = new JSObject(); ret.put("songs", songs); ret.put("count", count); call.resolve(ret);
        } catch (SecurityException e) { call.reject("Android no autorizó el acceso al catálogo de audio.", e); }
        catch (Exception e) { call.reject("No se pudo escanear la biblioteca multimedia.", e); }
    }

    @PluginMethod
    public void copyToCache(PluginCall call) {
        String raw = call.getString("uri");
        if (raw == null || raw.isEmpty()) { call.reject("Falta el URI del audio."); return; }
        try {
            Uri uri = Uri.parse(raw);
            String name = call.getString("displayName"); if (name == null || name.isEmpty()) name = "audio.mp3";
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            String id = uri.getLastPathSegment(); if (id == null) id = String.valueOf(System.currentTimeMillis());
            File dir = new File(getContext().getCacheDir(), "jans-audio"); if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, id + "_" + name);
            if (!out.exists() || out.length() == 0) {
                try (InputStream in = getContext().getContentResolver().openInputStream(uri); FileOutputStream fos = new FileOutputStream(out)) {
                    if (in == null) { call.reject("Android no permitió leer este audio."); return; }
                    byte[] b = new byte[65536]; int n;
                    while ((n = in.read(b)) > 0) fos.write(b, 0, n);
                }
            }
            JSObject ret = new JSObject(); ret.put("path", out.getAbsolutePath()); call.resolve(ret);
        } catch (Exception e) { call.reject("No se pudo preparar el archivo para reproducirlo.", e); }
    }

    @PluginMethod
    public void getTrackDetails(PluginCall call) {
        String raw = call.getString("uri");
        if (raw == null || raw.isEmpty()) { call.reject("Falta el URI del audio."); return; }
        JSObject ret = new JSObject();
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(getContext(), Uri.parse(raw));
            byte[] art = mmr.getEmbeddedPicture();
            if (art != null) { ret.put("coverBase64", Base64.encodeToString(art, Base64.NO_WRAP)); ret.put("coverMime", "image/jpeg"); }
            String lyrics = mmr.extractMetadata(21); // Android metadata key for embedded lyrics on implementations that expose it.
            if (lyrics != null && !lyrics.trim().isEmpty()) ret.put("embeddedLyrics", lyrics);
        } catch (Exception ignored) { }
        finally { try { mmr.release(); } catch (Exception ignored) { } }
        ret.put("lrcText", ""); call.resolve(ret);
    }
}
