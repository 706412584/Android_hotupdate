package com.orange.patchgen.parser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用最小 resources.arsc 构造器。
 *
 * <p>按真实 ResTable 结构写出二进制：ResTable_header → 全局 ResStringPool →
 * package chunk（含 typeStrings / keyStrings 池 + typeSpec + type chunk）。
 * 结构与 aapt2 36 的真实产物一致（条目偏移为常规 u32 编码），仅省略
 * 全局字符串池内容（真实文件里存的是 res/ 文件路径，解析不需要）。
 *
 * <p>只用最小编码路径（UTF-8 池、u32 条目偏移），用于验证 {@link ArscParser}
 * 的读取逻辑；UTF-16 池与稀疏条目另有手工构造的用例。
 */
public final class ArscFixtureBuilder {

    /** 资源类型 → 该类型下按顺序排列的资源名（下标即 entry index）。 */
    private final Map<String, List<String>> types = new LinkedHashMap<>();

    /** 字符串池编码：true=UTF-8（默认），false=UTF-16LE。 */
    private boolean utf8Pools = true;

    public ArscFixtureBuilder add(String typeName, String... resourceNames) {
        List<String> list = types.computeIfAbsent(typeName, k -> new ArrayList<>());
        for (String name : resourceNames) {
            list.add(name);
        }
        return this;
    }

    /** 切换全部字符串池的编码（默认 UTF-8）。 */
    public ArscFixtureBuilder utf8Pools(boolean utf8) {
        this.utf8Pools = utf8;
        return this;
    }

    public byte[] build() throws IOException {
        // ---- 全局字符串池（内容无关紧要，给一个占位字符串）----
        byte[] globalPool = buildStringPool(new String[]{"res/x.xml"}, utf8Pools);

        // ---- package 内的 typeStrings / keyStrings 池 ----
        List<String> typeNames = new ArrayList<>(types.keySet());
        List<String> keyNames = new ArrayList<>();
        for (List<String> names : types.values()) {
            keyNames.addAll(names);
        }
        byte[] typePool = buildStringPool(typeNames.toArray(new String[0]), utf8Pools);
        byte[] keyPool = buildStringPool(keyNames.toArray(new String[0]), utf8Pools);

        // ---- type chunk 们 ----
        // 先算每个 type chunk，并累计 key 起始下标
        List<byte[]> typeChunks = new ArrayList<>();
        int keyCursor = 0;
        int typeId = 1;
        for (Map.Entry<String, List<String>> entry : types.entrySet()) {
            List<String> names = entry.getValue();
            typeChunks.add(buildTypeChunk(typeId, names.size(), keyCursor));
            keyCursor += names.size();
            typeId++;
        }

        // ---- package chunk ----
        int pkgHeaderSize = 288; // 与真实 aapt2 产物一致（含 typeIdOffset 的 288 布局）
        ByteArrayOutputStream pkg = new ByteArrayOutputStream();
        writeChunkHeader(pkg, 0x0200, pkgHeaderSize, 0); // size 稍后回填
        writeInt(pkg, 0x7F);                             // id
        writeUtf16Z(pkg, "com.test.fixture", 128);       // name[128]
        writeInt(pkg, pkgHeaderSize);                    // typeStrings 偏移
        writeInt(pkg, 0);                                // lastPublicType
        writeInt(pkg, pkgHeaderSize + typePool.length);  // keyStrings 偏移
        writeInt(pkg, 0);                                // lastPublicKey
        writeInt(pkg, 0);                                // typeIdOffset
        pkg.write(typePool);
        pkg.write(keyPool);
        for (byte[] chunk : typeChunks) {
            pkg.write(chunk);
        }
        byte[] pkgBytes = pkg.toByteArray();
        patchChunkSize(pkgBytes, 4); // 回填 chunk size

        // ---- ResTable_header ----
        int headerSize = 12;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeChunkHeader(out, 0x0002, headerSize, 0);
        writeInt(out, 1); // packageCount
        out.write(globalPool);
        out.write(pkgBytes);
        byte[] result = out.toByteArray();
        patchChunkSize(result, 4);
        return result;
    }

    /**
     * 单个 type chunk（ResTable_type）：常规 u32 条目偏移，条目数据为
     * 8 字节 ResTable_entry（size=8, flags=0, key=…）。
     */
    private static byte[] buildTypeChunk(int typeId, int entryCount, int firstKeyIndex) throws IOException {
        int headerSize = 84; // 与真实产物一致（header 20 + ResTable_config 64）
        int entriesStart = headerSize + entryCount * 4;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeChunkHeader(out, 0x0201, headerSize, 0);
        out.write(typeId);        // id
        out.write(0);             // flags：常规 u32 偏移
        writeShort(out, 0);       // reserved（u16，位于 pos+10）
        writeInt(out, entryCount);
        writeInt(out, entriesStart);
        // ResTable_config：64 字节，全零（默认配置）
        out.write(new byte[64]);
        // 条目偏移表
        for (int i = 0; i < entryCount; i++) {
            writeInt(out, i * 8);
        }
        // 条目数据
        for (int i = 0; i < entryCount; i++) {
            writeShort(out, 8);                       // ResTable_entry.size
            writeShort(out, 0);                       // flags
            writeInt(out, firstKeyIndex + i);         // key 下标
        }
        byte[] result = out.toByteArray();
        patchChunkSize(result, 4);
        return result;
    }

    /**
     * 构造一个字符串池 chunk。{@code utf8} 控制编码（true=UTF-8，false=UTF-16LE）。
     */
    static byte[] buildStringPool(String[] strings, boolean utf8) throws IOException {
        int headerSize = 28;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        int[] offsets = new int[strings.length];
        for (int i = 0; i < strings.length; i++) {
            offsets[i] = data.size();
            if (utf8) {
                byte[] bytes = strings[i].getBytes(StandardCharsets.UTF_8);
                writeUtf8Len(data, strings[i].length());
                writeUtf8Len(data, bytes.length);
                data.write(bytes);
                data.write(0);
            } else {
                byte[] bytes = strings[i].getBytes(StandardCharsets.UTF_16LE);
                writeShort(data, strings[i].length());
                data.write(bytes);
                writeShort(data, 0);
            }
        }
        int stringsStart = headerSize + strings.length * 4;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeChunkHeader(out, 0x0001, headerSize, 0);
        writeInt(out, strings.length); // stringCount
        writeInt(out, 0);              // styleCount
        writeInt(out, utf8 ? 0x100 : 0);
        writeInt(out, stringsStart);
        writeInt(out, 0);              // stylesStart
        for (int offset : offsets) {
            writeInt(out, offset);
        }
        out.write(data.toByteArray());
        byte[] result = out.toByteArray();
        patchChunkSize(result, 4);
        return result;
    }

    // ---- 基础写出工具 ----

    private static void writeChunkHeader(ByteArrayOutputStream out, int type,
                                         int headerSize, int size) throws IOException {
        writeShort(out, type);
        writeShort(out, headerSize);
        writeInt(out, size);
    }

    private static void writeShort(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    /** UTF-8 长度前缀：7 位可容时单字节，否则双字节（高位在前）。 */
    private static void writeUtf8Len(ByteArrayOutputStream out, int len) throws IOException {
        if (len > 0x7F) {
            out.write(((len >> 8) & 0x7F) | 0x80);
            out.write(len & 0xFF);
        } else {
            out.write(len);
        }
    }

    private static void writeUtf16Z(ByteArrayOutputStream out, String value,
                                    int charCapacity) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16LE);
        out.write(bytes);
        out.write(new byte[(charCapacity * 2) - bytes.length]);
    }

    /** 回填 chunk size（位置 4 起 4 字节）。 */
    private static void patchChunkSize(byte[] data, int pos) {
        int size = data.length;
        data[pos] = (byte) (size & 0xFF);
        data[pos + 1] = (byte) ((size >> 8) & 0xFF);
        data[pos + 2] = (byte) ((size >> 16) & 0xFF);
        data[pos + 3] = (byte) ((size >> 24) & 0xFF);
    }
}
