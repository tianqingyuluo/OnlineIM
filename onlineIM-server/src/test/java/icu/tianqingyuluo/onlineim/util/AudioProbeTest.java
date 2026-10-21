package icu.tianqingyuluo.onlineim.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class AudioProbeTest {

    public static byte[] fixture(String name) throws Exception {
        try (InputStream in = AudioProbeTest.class.getResourceAsStream("/audio/" + name)) {
            assertNotNull(in, "缺少测试音频 " + name);
            return in.readAllBytes();
        }
    }

    /** 生成 PCM WAV（单声道 16bit） */
    public static byte[] wav(int sampleRate, int millis) {
        int samples = sampleRate * millis / 1000;
        int dataSize = samples * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes());
        buffer.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        buffer.put("data".getBytes()).putInt(dataSize);
        for (int i = 0; i < samples; i++) {
            buffer.putShort((short) (Math.sin(i * 2 * Math.PI * 440 / sampleRate) * 8000));
        }
        return buffer.array();
    }

    @ParameterizedTest
    @CsvSource({
            "tone-1500ms.webm, webm, webm, audio/webm, opus",
            "tone-1500ms-live.webm, webm, webm, audio/webm, opus",
            "tone-1500ms-opus.ogg, ogg, ogg, audio/ogg, opus",
            "tone-1500ms-vorbis.ogg, ogg, ogg, audio/ogg, vorbis",
            "tone-1500ms.m4a, mp4, m4a, audio/mp4, ",
            "tone-1500ms.mp3, mp3, mp3, audio/mpeg, mp3",
            "chrome-mediarecorder.webm, webm, webm, audio/webm, opus",
    })
    void detectsFormatFromContentAndParsesDuration(String name, String format, String extension, String mime, String codec) throws Exception {
        AudioProbe.Result result = AudioProbe.probe(fixture(name));
        assertEquals(format, result.format());
        assertEquals(extension, result.extension());
        assertEquals(mime, result.mime());
        if (codec != null) {
            assertEquals(codec, result.codec());
        }
        assertNotNull(result.durationMs(), name);
        long expected = name.startsWith("chrome") ? 2000 : 1500;
        long tolerance = name.startsWith("chrome") ? 700 : 150;
        assertTrue(Math.abs(result.durationMs() - expected) <= tolerance, name + " duration=" + result.durationMs());
    }

    @Test
    void fragmentedMp4HasNoParsableDuration() throws Exception {
        AudioProbe.Result result = AudioProbe.probe(fixture("tone-1500ms-fragmented.m4a"));
        assertEquals("mp4", result.format());
        assertNull(result.durationMs());
    }

    @Test
    void chromeMediaRecorderMp4IsAudioMp4() throws Exception {
        AudioProbe.Result result = AudioProbe.probe(fixture("chrome-mediarecorder.m4a"));
        assertEquals("mp4", result.format());
        assertEquals("audio/mp4", result.mime());
        // 分片 MP4：mvhd 里没有时长，由客户端上报（服务端校验范围）
        assertTrue(result.durationMs() == null || Math.abs(result.durationMs() - 2000) < 700, String.valueOf(result.durationMs()));
    }

    @Test
    void wavDurationFromHeader() throws Exception {
        AudioProbe.Result result = AudioProbe.probe(wav(8000, 2500));
        assertEquals("wav", result.format());
        assertEquals("pcm", result.codec());
        assertEquals(2500, result.durationMs());
    }

    @Test
    void rejectsVideoAndUnknownContent() throws Exception {
        assertThrows(AudioProbe.Unsupported.class, () -> AudioProbe.probe(fixture("video-1s.webm")));
        byte[] random = new byte[4096];
        new Random(7).nextBytes(random);
        random[0] = 0x12;
        assertThrows(AudioProbe.Unsupported.class, () -> AudioProbe.probe(random));
        assertThrows(AudioProbe.Unsupported.class, () -> AudioProbe.probe("<html>not audio</html>".getBytes()));
        // PNG 伪装成 .webm
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
        assertThrows(AudioProbe.Unsupported.class, () -> AudioProbe.probe(png));
    }

    @Test
    void rejectsTruncatedOrCorruptContainers() throws Exception {
        byte[] webm = fixture("tone-1500ms.webm");
        assertThrows(AudioProbe.Corrupt.class, () -> AudioProbe.probe(Arrays.copyOf(webm, 60)));
        byte[] ogg = fixture("tone-1500ms-opus.ogg");
        assertThrows(AudioProbe.Corrupt.class, () -> AudioProbe.probe(Arrays.copyOf(ogg, 100)));
        byte[] wav = wav(8000, 1000);
        assertThrows(AudioProbe.Corrupt.class, () -> AudioProbe.probe(Arrays.copyOf(wav, 30)));
        // EBML 魔数后面是随机垃圾
        byte[] garbage = new byte[2048];
        new Random(3).nextBytes(garbage);
        garbage[0] = 0x1A;
        garbage[1] = 0x45;
        garbage[2] = (byte) 0xDF;
        garbage[3] = (byte) 0xA3;
        assertThrows(Exception.class, () -> AudioProbe.probe(garbage));
        // 只有 ID3 头没有音频帧
        ByteArrayOutputStream id3 = new ByteArrayOutputStream();
        id3.write(new byte[]{'I', 'D', '3', 4, 0, 0, 0, 0, 0, 10});
        id3.write(new byte[64]);
        assertThrows(AudioProbe.Corrupt.class, () -> AudioProbe.probe(id3.toByteArray()));
    }
}
