package com.gathin.tvhome;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Surface;
import java.nio.ByteBuffer;

/** Local silent video decoding, bypassing firmware MediaPlayer implementations. Worker looper only. */
final class UhdVideoDecoder {
    private final Handler worker;
    private final Runnable rendered, failed;
    private MediaExtractor extractor;
    private MediaCodec codec;
    private boolean closed, inputEnded, shown;
    private long clockStart, firstPts = -1;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    UhdVideoDecoder(Handler worker, Runnable rendered, Runnable failed) {
        this.worker = worker; this.rendered = rendered; this.failed = failed;
    }
    void open(Context context, String id, Surface surface) throws Exception {
        extractor = new MediaExtractor();
        try (AssetFileDescriptor asset = context.getAssets().openFd("video-wallpapers/" + id + ".mp4")) {
            extractor.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(), asset.getLength());
        }
        MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat candidate = extractor.getTrackFormat(i);
            if (candidate.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                extractor.selectTrack(i); format = candidate; break;
            }
        }
        if (format == null) throw new IllegalArgumentException("No video track");
        codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        codec.configure(format, surface, null, 0); codec.start();
        android.util.Log.i("TvHome.VideoWallpaper", "Native colour format: " + format);
        android.util.Log.i("TvHome.VideoWallpaper", "Decoder " + id + " " + format.getInteger(MediaFormat.KEY_WIDTH) + "x" + format.getInteger(MediaFormat.KEY_HEIGHT));
        worker.post(this::pump);
    }
    private void pump() {
        if (closed) return;
        try {
            if (!inputEnded) {
                int input = codec.dequeueInputBuffer(0);
                if (input >= 0) {
                    ByteBuffer buffer = codec.getInputBuffer(input);
                    int size = extractor.readSampleData(buffer, 0);
                    if (size < 0) {
                        codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true;
                    } else {
                        codec.queueInputBuffer(input, 0, size, extractor.getSampleTime(), 0); extractor.advance();
                    }
                }
            }
            int output = codec.dequeueOutputBuffer(info, 0);
            if (output >= 0) {
                boolean end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                if (info.size > 0) {
                    if (firstPts < 0) { firstPts = info.presentationTimeUs; clockStart = SystemClock.elapsedRealtimeNanos(); }
                    long target = clockStart + (info.presentationTimeUs - firstPts) * 1000;
                    // Hold at most one frame until its display time, leaving stop requests responsive.
                    renderAt(output, target, end); return;
                }
                codec.releaseOutputBuffer(output, false);
                if (end) loop();
            }
            worker.postDelayed(this::pump, 2);
        } catch (Exception error) { close(); failed.run(); }
    }
    private void renderAt(int output, long target, boolean end) {
        if (closed) return;
        long remaining = (target - SystemClock.elapsedRealtimeNanos()) / 1000000;
        if (remaining > 2) {
            worker.postDelayed(() -> renderAt(output, target, end), Math.min(remaining, 12)); return;
        }
        try {
            codec.releaseOutputBuffer(output, true);
            if (!shown) { shown = true; rendered.run(); }
            if (end) loop();
            worker.post(this::pump);
        } catch (Exception error) { close(); failed.run(); }
    }
    private void loop() {
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        codec.flush(); inputEnded = false; firstPts = -1;
    }
    void close() {
        if (closed) return;
        closed = true;
        if (codec != null) {
            try { codec.stop(); } catch (Exception ignored) { }
            try { codec.release(); } catch (Exception ignored) { }
            codec = null;
        }
        if (extractor != null) { extractor.release(); extractor = null; }
    }
}
