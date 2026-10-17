package com.classnote.app;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * WAV 封装工具。录音阶段直接写裸 PCM（避免边录边改文件头），结束时一次性生成标准 WAV。
 */
public final class WavUtil {

    private static final int HEADER_SIZE = 44;

    private WavUtil() {
    }

    public static byte[] pcm16ToWav(byte[] pcm, int sampleRate, int channels) {
        byte[] wav = new byte[HEADER_SIZE + pcm.length];
        ByteArrayOutputStream header = new ByteArrayOutputStream(HEADER_SIZE);
        try {
            writeHeader(header, pcm.length, sampleRate, channels);
        } catch (IOException impossible) {
            // ByteArrayOutputStream 不会抛异常
        }
        byte[] h = header.toByteArray();
        System.arraycopy(h, 0, wav, 0, Math.min(h.length, HEADER_SIZE));
        System.arraycopy(pcm, 0, wav, HEADER_SIZE, pcm.length);
        return wav;
    }

    public static void writeWavFromPcmFile(File pcmFile, File wavFile, int sampleRate, int channels)
            throws IOException {
        long dataSize = pcmFile.length();
        if (dataSize > Integer.MAX_VALUE - 64) {
            throw new IOException("录音文件过大");
        }
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(pcmFile), 64 * 1024);
        BufferedOutputStream out = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(wavFile), 64 * 1024);
            writeHeader(out, (int) dataSize, sampleRate, channels);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void writeHeader(OutputStream out, int dataSize, int sampleRate, int channels)
            throws IOException {
        int byteRate = sampleRate * channels * 2;
        writeAscii(out, "RIFF");
        writeIntLE(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLE(out, 16);
        writeShortLE(out, 1);              // PCM
        writeShortLE(out, channels);
        writeIntLE(out, sampleRate);
        writeIntLE(out, byteRate);
        writeShortLE(out, channels * 2);   // block align
        writeShortLE(out, 16);             // bits per sample
        writeAscii(out, "data");
        writeIntLE(out, dataSize);
    }

    private static void writeAscii(OutputStream out, String s) throws IOException {
        out.write(s.getBytes("US-ASCII"));
    }

    private static void writeIntLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeShortLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }
}
