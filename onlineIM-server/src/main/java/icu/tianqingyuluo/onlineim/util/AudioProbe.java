package icu.tianqingyuluo.onlineim.util;

import java.nio.charset.StandardCharsets;

/**
 * 语音文件探测：只看文件内容（魔数 + 容器结构），不信任扩展名和客户端 Content-Type。
 *
 * <p>支持浏览器 MediaRecorder 常见输出及常见音频文件：WebM（Opus/Vorbis）、Ogg（Opus/Vorbis）、
 * MP4/M4A（AAC 等，只含音轨）、MP3、WAV。能解析时给出时长（毫秒）：</p>
 * <ul>
 *   <li>WAV：data 块大小 / byteRate</li>
 *   <li>Ogg：最后一页 granule position（Opus 减去 pre-skip，按 48kHz）</li>
 *   <li>MP4：mvhd duration / timescale（分片 MP4 通常为 0，返回 null）</li>
 *   <li>MP3：逐帧累加（CBR/VBR 均可）</li>
 *   <li>WebM：Info/Duration；MediaRecorder 产物没有 Duration 时按最后一个 Block 的时间戳估算</li>
 * </ul>
 * 无法识别返回 {@link Unsupported}；看起来是某种格式但结构损坏抛 {@link Corrupt}。
 */
public final class AudioProbe {

    private AudioProbe() {
    }

    /** format: webm/ogg/mp4/mp3/wav；durationMs 无法解析时为 null */
    public record Result(String format, String extension, String mime, Long durationMs, String codec) {
    }

    public static class Unsupported extends Exception {
        public Unsupported(String message) {
            super(message);
        }
    }

    public static class Corrupt extends Exception {
        public Corrupt(String message) {
            super(message);
        }
    }

    public static Result probe(byte[] b) throws Unsupported, Corrupt {
        if (b == null || b.length < 12) {
            throw new Unsupported("文件太小，无法识别");
        }
        if (u8(b, 0) == 0x1A && u8(b, 1) == 0x45 && u8(b, 2) == 0xDF && u8(b, 3) == 0xA3) {
            return webm(b);
        }
        if (ascii(b, 0, 4).equals("OggS")) {
            return ogg(b);
        }
        if (ascii(b, 0, 4).equals("RIFF") && ascii(b, 8, 4).equals("WAVE")) {
            return wav(b);
        }
        if (ascii(b, 4, 4).equals("ftyp")) {
            return mp4(b);
        }
        if (ascii(b, 0, 3).equals("ID3") || isMp3FrameHeader(b, 0)) {
            return mp3(b);
        }
        throw new Unsupported("无法识别的音频格式");
    }

    // ---------------- WAV ----------------

    private static Result wav(byte[] b) throws Corrupt {
        int pos = 12;
        long byteRate = 0;
        int audioFormat = -1;
        Long dataSize = null;
        while (pos + 8 <= b.length) {
            String id = ascii(b, pos, 4);
            long size = le32(b, pos + 4);
            int data = pos + 8;
            if ("fmt ".equals(id)) {
                if (size < 16 || data + 16 > b.length) {
                    throw new Corrupt("WAV fmt 块损坏");
                }
                audioFormat = le16(b, data);
                byteRate = le32(b, data + 8);
            } else if ("data".equals(id)) {
                long available = b.length - data;
                dataSize = size > available || size == 0xFFFFFFFFL ? available : size;
                break;
            }
            if (size > b.length) {
                break;
            }
            pos = (int) (data + size + (size & 1));
        }
        if (audioFormat < 0 || dataSize == null || byteRate <= 0) {
            throw new Corrupt("WAV 文件缺少 fmt/data 块");
        }
        return new Result("wav", "wav", "audio/wav", dataSize * 1000 / byteRate, audioFormat == 1 ? "pcm" : "wav-" + audioFormat);
    }

    // ---------------- Ogg ----------------

    private static Result ogg(byte[] b) throws Corrupt {
        int pos = 0;
        String codec = null;
        long rate = 0;
        long preSkip = 0;
        long serial = -1;
        long lastGranule = -1;
        int pages = 0;
        while (pos + 27 <= b.length && ascii(b, pos, 4).equals("OggS")) {
            int segments = u8(b, pos + 26);
            if (pos + 27 + segments > b.length) {
                throw new Corrupt("Ogg 页头被截断");
            }
            int bodyLength = 0;
            for (int i = 0; i < segments; i++) {
                bodyLength += u8(b, pos + 27 + i);
            }
            int body = pos + 27 + segments;
            if (body + bodyLength > b.length) {
                throw new Corrupt("Ogg 页数据被截断");
            }
            long granule = le64(b, pos + 6);
            long pageSerial = le32(b, pos + 14);
            if (pages == 0) {
                serial = pageSerial;
                if (bodyLength >= 19 && ascii(b, body, 8).equals("OpusHead")) {
                    codec = "opus";
                    rate = 48000;
                    preSkip = le16(b, body + 10);
                } else if (bodyLength >= 16 && u8(b, body) == 1 && ascii(b, body + 1, 6).equals("vorbis")) {
                    codec = "vorbis";
                    rate = le32(b, body + 12);
                } else {
                    throw new Corrupt("Ogg 中不是 Opus/Vorbis 音频");
                }
            }
            if (pageSerial == serial && granule != -1L) {
                lastGranule = granule;
            }
            pages++;
            pos = body + bodyLength;
        }
        if (pages < 2 || rate <= 0) {
            throw new Corrupt("Ogg 文件不完整");
        }
        Long duration = lastGranule > preSkip ? (lastGranule - preSkip) * 1000 / rate : null;
        return new Result("ogg", "ogg", "audio/ogg", duration, codec);
    }

    // ---------------- MP4 ----------------

    private static Result mp4(byte[] b) throws Unsupported, Corrupt {
        Mp4State state = new Mp4State();
        walkBoxes(b, 0, b.length, 0, state);
        if (!state.sawMoov) {
            throw new Corrupt("MP4 文件缺少 moov");
        }
        if (state.hasVideo) {
            throw new Unsupported("这是视频文件，不能作为语音发送");
        }
        if (!state.hasAudio) {
            throw new Unsupported("MP4 中没有音轨");
        }
        Long duration = state.timescale > 0 && state.duration > 0 ? state.duration * 1000 / state.timescale : null;
        return new Result("mp4", "m4a", "audio/mp4", duration, null);
    }

    private static final class Mp4State {
        boolean sawMoov;
        boolean hasAudio;
        boolean hasVideo;
        long timescale;
        long duration;
    }

    private static void walkBoxes(byte[] b, int start, int end, int depth, Mp4State state) throws Corrupt {
        int pos = start;
        while (pos + 8 <= end) {
            long size = be32(b, pos);
            String type = ascii(b, pos + 4, 4);
            int header = 8;
            if (size == 1) {
                if (pos + 16 > end) {
                    throw new Corrupt("MP4 box 被截断");
                }
                size = be64(b, pos + 8);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || pos + size > end) {
                if (depth == 0 && "mdat".equals(type)) {
                    return; // 末尾 mdat 被截断不影响元数据
                }
                throw new Corrupt("MP4 box 大小非法: " + type);
            }
            int data = pos + header;
            int boxEnd = (int) (pos + size);
            switch (type) {
                case "moov" -> {
                    state.sawMoov = true;
                    walkBoxes(b, data, boxEnd, depth + 1, state);
                }
                case "trak", "mdia" -> walkBoxes(b, data, boxEnd, depth + 1, state);
                case "mvhd" -> {
                    int version = u8(b, data);
                    if (version == 1 && data + 32 <= boxEnd) {
                        state.timescale = be32(b, data + 20);
                        state.duration = be64(b, data + 24);
                    } else if (data + 20 <= boxEnd) {
                        state.timescale = be32(b, data + 12);
                        state.duration = be32(b, data + 16);
                    }
                }
                case "hdlr" -> {
                    if (data + 12 <= boxEnd) {
                        String handler = ascii(b, data + 8, 4);
                        if ("soun".equals(handler)) {
                            state.hasAudio = true;
                        } else if ("vide".equals(handler)) {
                            state.hasVideo = true;
                        }
                    }
                }
                default -> {
                }
            }
            pos = boxEnd;
        }
    }

    // ---------------- MP3 ----------------

    private static final int[][] MP3_BITRATES = {
            // MPEG1 Layer III
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1},
            // MPEG2/2.5 Layer III
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1},
    };
    private static final int[][] MP3_RATES = {
            {44100, 48000, 32000, -1}, // MPEG1
            {22050, 24000, 16000, -1}, // MPEG2
            {11025, 12000, 8000, -1},  // MPEG2.5
    };

    private static boolean isMp3FrameHeader(byte[] b, int pos) {
        return mp3FrameLength(b, pos) > 0;
    }

    /** 返回帧长度（字节），不是合法 Layer III 帧头返回 -1；样本数写入 samples[0]、采样率写入 samples[1] */
    private static int mp3FrameLength(byte[] b, int pos, long... out) {
        if (pos + 4 > b.length || u8(b, pos) != 0xFF || (u8(b, pos + 1) & 0xE0) != 0xE0) {
            return -1;
        }
        int versionBits = (u8(b, pos + 1) >> 3) & 0x03; // 00=2.5 01=保留 10=2 11=1
        int layerBits = (u8(b, pos + 1) >> 1) & 0x03;   // 01=Layer III
        if (versionBits == 1 || layerBits != 1) {
            return -1;
        }
        int bitrateIndex = (u8(b, pos + 2) >> 4) & 0x0F;
        int rateIndex = (u8(b, pos + 2) >> 2) & 0x03;
        int padding = (u8(b, pos + 2) >> 1) & 0x01;
        boolean mpeg1 = versionBits == 3;
        int bitrate = MP3_BITRATES[mpeg1 ? 0 : 1][bitrateIndex];
        int rate = MP3_RATES[mpeg1 ? 0 : versionBits == 2 ? 1 : 2][rateIndex];
        if (bitrate <= 0 || rate <= 0) {
            return -1;
        }
        int samples = mpeg1 ? 1152 : 576;
        if (out.length >= 2) {
            out[0] = samples;
            out[1] = rate;
        }
        return (samples / 8) * bitrate * 1000 / rate + padding;
    }

    private static Result mp3(byte[] b) throws Unsupported, Corrupt {
        int pos = 0;
        if (ascii(b, 0, 3).equals("ID3")) {
            if (b.length < 10) {
                throw new Corrupt("ID3 标签被截断");
            }
            int tagSize = ((u8(b, 6) & 0x7F) << 21) | ((u8(b, 7) & 0x7F) << 14) | ((u8(b, 8) & 0x7F) << 7) | (u8(b, 9) & 0x7F);
            pos = 10 + tagSize + ((u8(b, 5) & 0x10) != 0 ? 10 : 0);
        }
        // 允许标签后少量填充
        int limit = Math.min(b.length, pos + 4096);
        while (pos < limit && !isMp3FrameHeader(b, pos)) {
            pos++;
        }
        long frames = 0;
        double millis = 0;
        long[] info = new long[2];
        while (pos < b.length) {
            int length = mp3FrameLength(b, pos, info);
            if (length <= 0) {
                break;
            }
            if (pos + length > b.length) {
                // 最后一帧不完整，忽略
                break;
            }
            millis += info[0] * 1000.0 / info[1];
            frames++;
            pos += length;
        }
        if (frames < 3) {
            if (ascii(b, 0, 3).equals("ID3")) {
                throw new Corrupt("MP3 文件没有有效音频帧");
            }
            throw new Unsupported("无法识别的音频格式");
        }
        return new Result("mp3", "mp3", "audio/mpeg", Math.round(millis), "mp3");
    }

    // ---------------- WebM / Matroska ----------------

    private static final long ID_EBML = 0x1A45DFA3L;
    private static final long ID_DOCTYPE = 0x4282L;
    private static final long ID_SEGMENT = 0x18538067L;
    private static final long ID_INFO = 0x1549A966L;
    private static final long ID_TIMECODE_SCALE = 0x2AD7B1L;
    private static final long ID_DURATION = 0x4489L;
    private static final long ID_TRACKS = 0x1654AE6BL;
    private static final long ID_TRACK_ENTRY = 0xAEL;
    private static final long ID_TRACK_TYPE = 0x83L;
    private static final long ID_CODEC_ID = 0x86L;
    private static final long ID_CLUSTER = 0x1F43B675L;
    private static final long ID_CLUSTER_TIMECODE = 0xE7L;
    private static final long ID_SIMPLE_BLOCK = 0xA3L;
    private static final long ID_BLOCK_GROUP = 0xA0L;
    private static final long ID_BLOCK = 0xA1L;

    /** 这些容器元素直接“走进去”扫描子元素（支持 MediaRecorder 的未知长度 Segment/Cluster） */
    private static boolean isDescended(long id) {
        return id == ID_EBML || id == ID_SEGMENT || id == ID_INFO || id == ID_TRACKS || id == ID_TRACK_ENTRY
                || id == ID_CLUSTER || id == ID_BLOCK_GROUP;
    }

    private static Result webm(byte[] b) throws Unsupported, Corrupt {
        int pos = 0;
        String docType = null;
        long timecodeScale = 1_000_000L;
        double infoDuration = -1;
        boolean audio = false;
        boolean video = false;
        String codec = null;
        long clusterTimecode = 0;
        long maxTimecode = -1;
        int blocks = 0;
        boolean sawSegment = false;
        while (pos < b.length) {
            long[] id = readVint(b, pos, true);
            if (id == null) {
                if (blocks > 0) {
                    break; // 尾部垃圾/截断：已有数据即可
                }
                throw new Corrupt("WebM 元素 ID 非法");
            }
            int sizePos = pos + (int) id[1];
            long[] size = readVint(b, sizePos, false);
            if (size == null) {
                if (blocks > 0) {
                    break;
                }
                throw new Corrupt("WebM 元素长度非法");
            }
            int data = sizePos + (int) size[1];
            boolean unknownSize = size[0] < 0;
            long elementId = id[0];
            if (elementId == ID_SEGMENT) {
                sawSegment = true;
            }
            if (isDescended(elementId)) {
                pos = data;
                continue;
            }
            if (unknownSize) {
                throw new Corrupt("WebM 非容器元素长度未知");
            }
            long end = data + size[0];
            if (end > b.length) {
                if (blocks > 0) {
                    break; // 最后一个块被截断
                }
                throw new Corrupt("WebM 数据被截断");
            }
            int len = (int) size[0];
            if (elementId == ID_DOCTYPE) {
                docType = ascii(b, data, len).replace("\0", "");
            } else if (elementId == ID_TIMECODE_SCALE) {
                timecodeScale = readUint(b, data, len);
            } else if (elementId == ID_DURATION) {
                infoDuration = readFloat(b, data, len);
            } else if (elementId == ID_TRACK_TYPE) {
                long type = readUint(b, data, len);
                audio |= type == 2;
                video |= type == 1;
            } else if (elementId == ID_CODEC_ID) {
                String value = ascii(b, data, len).replace("\0", "");
                if (value.startsWith("A_")) {
                    codec = value.substring(2).toLowerCase();
                }
            } else if (elementId == ID_CLUSTER_TIMECODE) {
                clusterTimecode = readUint(b, data, len);
            } else if (elementId == ID_SIMPLE_BLOCK || elementId == ID_BLOCK) {
                long[] track = readVint(b, data, false);
                if (track == null || data + track[1] + 2 > end) {
                    throw new Corrupt("WebM Block 损坏");
                }
                int rel = (short) ((u8(b, data + (int) track[1]) << 8) | u8(b, data + (int) track[1] + 1));
                maxTimecode = Math.max(maxTimecode, clusterTimecode + rel);
                blocks++;
            }
            pos = (int) end;
        }
        if (docType == null || !(docType.equals("webm") || docType.equals("matroska"))) {
            throw new Unsupported("不支持的 EBML 文件");
        }
        if (!sawSegment) {
            throw new Corrupt("WebM 缺少 Segment");
        }
        if (video) {
            throw new Unsupported("这是视频文件，不能作为语音发送");
        }
        if (!audio) {
            throw new Unsupported("WebM 中没有音轨");
        }
        if (blocks == 0) {
            throw new Corrupt("WebM 中没有音频数据");
        }
        Long duration;
        if (infoDuration > 0) {
            duration = Math.round(infoDuration * timecodeScale / 1_000_000.0);
        } else {
            // 最后一个块的起始时间 + 一帧（Opus 常见 20ms）
            duration = Math.round(maxTimecode * timecodeScale / 1_000_000.0) + 20;
        }
        return new Result("webm", "webm", "audio/webm", duration, codec);
    }

    /** EBML 变长整数；keepMarker=true 用于元素 ID。返回 {值, 字节数}；长度未知时值为 -1 */
    private static long[] readVint(byte[] b, int pos, boolean keepMarker) {
        if (pos >= b.length) {
            return null;
        }
        int first = u8(b, pos);
        int length = 1;
        int mask = 0x80;
        while (length <= 8 && (first & mask) == 0) {
            mask >>= 1;
            length++;
        }
        if (length > 8 || (keepMarker && length > 4) || pos + length > b.length) {
            return null;
        }
        long value = keepMarker ? first : (first & (mask - 1));
        boolean allOnes = (first & (mask - 1)) == mask - 1;
        for (int i = 1; i < length; i++) {
            int next = u8(b, pos + i);
            value = (value << 8) | next;
            allOnes &= next == 0xFF;
        }
        if (!keepMarker && allOnes) {
            value = -1;
        }
        return new long[]{value, length};
    }

    private static long readUint(byte[] b, int pos, int len) {
        long value = 0;
        for (int i = 0; i < len && i < 8; i++) {
            value = (value << 8) | u8(b, pos + i);
        }
        return value;
    }

    private static double readFloat(byte[] b, int pos, int len) {
        if (len == 4) {
            return Float.intBitsToFloat((int) readUint(b, pos, 4));
        }
        if (len == 8) {
            return Double.longBitsToDouble(readUint(b, pos, 8));
        }
        return -1;
    }

    // ---------------- 字节工具 ----------------

    private static int u8(byte[] b, int pos) {
        return b[pos] & 0xFF;
    }

    private static String ascii(byte[] b, int pos, int len) {
        if (pos < 0 || len < 0 || pos + len > b.length) {
            return "";
        }
        return new String(b, pos, len, StandardCharsets.ISO_8859_1);
    }

    private static int le16(byte[] b, int pos) {
        return u8(b, pos) | (u8(b, pos + 1) << 8);
    }

    private static long le32(byte[] b, int pos) {
        return (long) u8(b, pos) | ((long) u8(b, pos + 1) << 8) | ((long) u8(b, pos + 2) << 16) | ((long) u8(b, pos + 3) << 24);
    }

    private static long le64(byte[] b, int pos) {
        return le32(b, pos) | (le32(b, pos + 4) << 32);
    }

    private static long be32(byte[] b, int pos) {
        return ((long) u8(b, pos) << 24) | ((long) u8(b, pos + 1) << 16) | ((long) u8(b, pos + 2) << 8) | u8(b, pos + 3);
    }

    private static long be64(byte[] b, int pos) {
        return (be32(b, pos) << 32) | be32(b, pos + 4);
    }
}
