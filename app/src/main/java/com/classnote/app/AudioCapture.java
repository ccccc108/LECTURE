package com.classnote.app;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * 采集麦克风 PCM：16kHz / 单声道 / 16bit。
 * - 全部数据写入裸 PCM 文件（结束后再封装成 WAV，便于回放）
 * - 每满 chunkSeconds 秒回调一次 onChunk，交给前台服务做云端转写
 */
public class AudioCapture {

    public interface Listener {
        void onChunk(byte[] pcm);

        void onError(String message);
    }

    public static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int READ_BYTES = 3200; // 100ms

    private final File pcmFile;
    private final int chunkBytes;
    private final Listener listener;

    /** 上一段末尾保留的 1.5 秒音频：拼到下一段前面，让两段转写结果有重复词可对齐去重。 */
    private static final int OVERLAP_BYTES = SAMPLE_RATE * 3;
    private byte[] carry = new byte[0];

    private volatile boolean running;
    private Thread thread;
    private volatile boolean started;

    public AudioCapture(File pcmFile, int chunkSeconds, Listener listener) {
        this.pcmFile = pcmFile;
        int sec = Math.max(5, chunkSeconds);
        this.chunkBytes = sec * SAMPLE_RATE * 2;
        this.listener = listener;
    }

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                captureLoop();
            }
        }, "classnote-capture");
        thread.setDaemon(true);
        thread.start();
    }

    /** 停止并等待采集线程结束（会冲刷最后一段不足 chunkSeconds 的数据）。 */
    public void stop() {
        running = false;
        Thread t = thread;
        if (t != null) {
            try {
                t.join(4000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void captureLoop() {
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (minBuf <= 0) {
            minBuf = 8192;
        }
        AudioRecord record = null;
        FileOutputStream out = null;
        ByteArrayOutputStream pending = new ByteArrayOutputStream(1024 * 1024);
        boolean errorReported = false;
        try {
            // 依次尝试几种录音源：VOICE_RECOGNITION 对语音识别最友好，
            // 但部分平板/外接麦克风不支持，失败时回退到普通 MIC / DEFAULT。
            int[] sources = new int[]{
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.DEFAULT
            };
            for (int i = 0; i < sources.length && record == null; i++) {
                AudioRecord candidate = null;
                try {
                    candidate = new AudioRecord(sources[i], SAMPLE_RATE,
                            CHANNEL_CONFIG, AUDIO_FORMAT, Math.max(minBuf * 2, 32000));
                    if (candidate.getState() == AudioRecord.STATE_INITIALIZED) {
                        record = candidate;
                    } else {
                        try {
                            candidate.release();
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    if (candidate != null) {
                        try {
                            candidate.release();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            if (record == null) {
                throw new IllegalStateException("麦克风初始化失败，可能被其他应用占用");
            }
            out = new FileOutputStream(pcmFile);
            record.startRecording();
            started = true;

            byte[] buf = new byte[READ_BYTES];
            while (running) {
                int n = record.read(buf, 0, buf.length);
                if (n > 0) {
                    out.write(buf, 0, n);
                    pending.write(buf, 0, n);
                    if (pending.size() >= chunkBytes) {
                        flush(pending);
                    }
                } else if (n < 0) {
                    throw new IllegalStateException("麦克风读取失败（错误码 " + n + "）");
                }
            }
        } catch (Exception e) {
            if (!errorReported) {
                errorReported = true;
                if (listener != null) {
                    listener.onError(e.getMessage() == null ? e.toString() : e.getMessage());
                }
            }
        } finally {
            running = false;
            // 冲刷最后一段（flush 内部会判断长度是否值得送转写）
            try {
                flush(pending);
            } catch (Exception ignored) {
            }
            if (record != null) {
                try {
                    record.stop();
                } catch (Exception ignored) {
                }
                try {
                    record.release();
                } catch (Exception ignored) {
                }
            }
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void flush(ByteArrayOutputStream pending) {
        if (pending.size() == 0) {
            return;
        }
        // 少于 0.5 秒的尾巴不单独送转写，留到下一段一起处理
        if (pending.size() + carry.length < SAMPLE_RATE) {
            return;
        }
        byte[] chunk = pending.toByteArray();
        pending.reset();

        byte[] data = new byte[carry.length + chunk.length];
        System.arraycopy(carry, 0, data, 0, carry.length);
        System.arraycopy(chunk, 0, data, carry.length, chunk.length);

        int keep = Math.min(OVERLAP_BYTES, data.length / 2);
        carry = tail(data, keep);

        if (listener != null) {
            listener.onChunk(data);
        }
    }

    private static byte[] tail(byte[] src, int n) {
        byte[] out = new byte[n];
        System.arraycopy(src, src.length - n, out, 0, n);
        return out;
    }

    public boolean hasStarted() {
        return started;
    }
}
