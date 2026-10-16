package com.orange.patchgen.parser;

import com.orange.patchgen.callback.GeneratorErrorCode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * resources.arsc 解析器（纯 JVM，不依赖任何 Android 类）。
 *
 * <p>从 ResTable 二进制中提取「资源 ID → 资源名」映射（{@link ArscResourceIds}），
 * 供补丁生成侧做资源 ID 稳定性校验——补丁整体替换 resources.arsc 后，未被差分
 * dex 覆盖的旧类仍内联着旧 ID，若新版重排了 ID 就会静默错位（详见
 * {@link ArscResourceIds} 的说明与 docs/RESOURCE_ID_STABILITY.md）。
 *
 * <p>解析范围：
 * <ul>
 *   <li>ResTable_header（0x0002）→ 遍历顶层 chunk，只处理 package（0x0200）；</li>
 *   <li>package 内的 typeStrings / keyStrings 字符串池（UTF-8 与 UTF-16 都支持）；</li>
 *   <li>type（0x0201）chunk 的条目表，支持常规 u32 偏移、FLAG_OFFSET16 与
 *       FLAG_SPARSE 三种编码（aapt2 对稀疏类型会启用后两者）。</li>
 * </ul>
 *
 * <p>资源名形如 {@code string/app_name}、{@code layout/main}；同一资源的多配置
 * 条目（不同 config 的多个 type chunk）会重复出现，由 Builder 去重。
 *
 * <p>解析失败一律抛 {@link ParseException}——调用方（补丁生成器）会把它当作
 * 「无法证明对齐」处理，宁可让消费侧拒绝补丁，也不放行可能静默错乱的产物。
 */
public final class ArscParser {

    // ResChunk_header.type
    private static final int TYPE_STRING_POOL = 0x0001;
    private static final int TYPE_TABLE = 0x0002;
    private static final int TYPE_TABLE_PACKAGE = 0x0200;
    private static final int TYPE_TABLE_TYPE = 0x0201;

    /** ResStringPool_header.flags：UTF-8 编码。未设置时为 UTF-16。 */
    private static final int STRING_POOL_UTF8_FLAG = 0x100;

    // ResTable_type.flags
    private static final int TYPE_FLAG_SPARSE = 0x01;
    private static final int TYPE_FLAG_OFFSET16 = 0x02;

    /** 条目偏移表中的「无此条目」标记（u32 编码）。 */
    private static final int NO_ENTRY = 0xFFFFFFFF;
    /** u16 偏移表中的「无此条目」标记（FLAG_OFFSET16 / FLAG_SPARSE）。 */
    private static final int NO_ENTRY_16 = 0xFFFF;

    // ResTable_package 头部中两个字符串池偏移的位置（相对 package chunk 起点）。
    // 布局：header(8) + id(4) + name[128](256) + typeStrings(4) + lastPublicType(4)
    //       + keyStrings(4) + lastPublicKey(4) [+ typeIdOffset(4)]
    private static final int PACKAGE_TYPE_STRINGS_POS = 268;
    private static final int PACKAGE_KEY_STRINGS_POS = 276;
    private static final int PACKAGE_MIN_HEADER_SIZE = 284;

    private ArscParser() {
        // Prevent instantiation
    }

    /**
     * 解析 resources.arsc 文件。
     *
     * @param arscFile arsc 文件（通常从 APK 中解压得到）
     * @return 资源 ID ↔ 资源名映射
     * @throws ParseException 文件缺失或结构非法时抛出
     */
    public static ArscResourceIds parse(File arscFile) throws ParseException {
        if (arscFile == null || !arscFile.exists() || !arscFile.isFile()) {
            throw new ParseException("resources.arsc 不存在: " + arscFile,
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        try {
            return parse(Files.readAllBytes(arscFile.toPath()));
        } catch (IOException e) {
            throw new ParseException("读取 resources.arsc 失败: " + e.getMessage(),
                    GeneratorErrorCode.ERROR_FILE_READ_FAILED, e);
        }
    }

    /**
     * 解析 resources.arsc 字节内容。
     *
     * @param data arsc 文件内容
     * @return 资源 ID ↔ 资源名映射
     * @throws ParseException 结构非法时抛出
     */
    public static ArscResourceIds parse(byte[] data) throws ParseException {
        if (data == null || data.length < 12) {
            throw new ParseException("resources.arsc 内容为空或过短",
                    GeneratorErrorCode.ERROR_APK_PARSE_FAILED);
        }
        if (u16(data, 0) != TYPE_TABLE) {
            throw new ParseException("不是 ResTable（resources.arsc）文件",
                    GeneratorErrorCode.ERROR_APK_PARSE_FAILED);
        }
        int headerSize = u16(data, 2);
        int tableSize = u32(data, 4);
        // tableSize 异常时退回文件长度，避免因头部字段损坏而漏掉整个包
        int limit = (tableSize > 0 && tableSize <= data.length) ? tableSize : data.length;

        ArscResourceIds.Builder builder = new ArscResourceIds.Builder();
        int pos = headerSize;
        while (pos + 8 <= limit) {
            int type = u16(data, pos);
            int chunkHeaderSize = u16(data, pos + 2);
            int chunkSize = u32(data, pos + 4);
            checkChunk(data, pos, chunkHeaderSize, chunkSize);
            if (type == TYPE_TABLE_PACKAGE) {
                parsePackage(data, pos, chunkHeaderSize, chunkSize, builder);
            }
            pos += chunkSize;
        }
        return builder.build();
    }

    /**
     * 解析单个 package chunk：读取两个字符串池，再遍历 type chunk 提取条目。
     */
    private static void parsePackage(byte[] data, int pkgPos, int pkgHeaderSize,
                                     int pkgSize, ArscResourceIds.Builder builder)
            throws ParseException {
        if (pkgHeaderSize < PACKAGE_MIN_HEADER_SIZE) {
            throw new ParseException("ResTable_package 头部过短: " + pkgHeaderSize,
                    GeneratorErrorCode.ERROR_APK_PARSE_FAILED);
        }
        int packageId = u32(data, pkgPos + 8) & 0xFF;
        int typeStringsOffset = u32(data, pkgPos + PACKAGE_TYPE_STRINGS_POS);
        int keyStringsOffset = u32(data, pkgPos + PACKAGE_KEY_STRINGS_POS);

        String[] typeNames = readStringPool(data, pkgPos + typeStringsOffset);
        String[] keyNames = readStringPool(data, pkgPos + keyStringsOffset);

        int pos = pkgPos + pkgHeaderSize;
        int end = pkgPos + pkgSize;
        while (pos + 8 <= end) {
            int type = u16(data, pos);
            int chunkHeaderSize = u16(data, pos + 2);
            int chunkSize = u32(data, pos + 4);
            checkChunk(data, pos, chunkHeaderSize, chunkSize);
            if (type == TYPE_TABLE_TYPE) {
                parseTypeChunk(data, pos, chunkHeaderSize, chunkSize,
                        packageId, typeNames, keyNames, builder);
            }
            pos += chunkSize;
        }
    }

    /**
     * 解析单个 type chunk 的条目表。
     *
     * <p>条目偏移有三种编码（见 ResTable_type.flags）：常规 u32、FLAG_OFFSET16
     * （u16，值为 4 字节单位的偏移）、FLAG_SPARSE（u16 下标 + u16 偏移，成对）。
     * 条目存在与否由 {@code ResTable_entry.size != 0} 判定。
     */
    private static void parseTypeChunk(byte[] data, int pos, int headerSize, int size,
                                       int packageId, String[] typeNames, String[] keyNames,
                                       ArscResourceIds.Builder builder) throws ParseException {
        int typeId = u8(data, pos + 8);
        int flags = u8(data, pos + 9);
        int entryCount = u32(data, pos + 12);
        int entriesStart = u32(data, pos + 16);

        if (typeId <= 0 || typeId > typeNames.length) {
            // 类型名不可知（池里没有），跳过而不是整体失败——不影响其它类型的提取
            return;
        }
        String typeName = typeNames[typeId - 1];
        if (typeName == null || typeName.isEmpty()) {
            return;
        }

        int offsetTablePos = pos + headerSize;
        boolean sparse = (flags & TYPE_FLAG_SPARSE) != 0;
        boolean offset16 = (flags & TYPE_FLAG_OFFSET16) != 0;

        for (int i = 0; i < entryCount; i++) {
            int entryIndex;
            int entryOffset;
            if (sparse) {
                checkBounds(data, offsetTablePos + i * 4, 4);
                int index = u16(data, offsetTablePos + i * 4);
                int offset = u16(data, offsetTablePos + i * 4 + 2);
                if (offset == NO_ENTRY_16) {
                    continue;
                }
                entryIndex = index;
                entryOffset = offset * 4;
            } else if (offset16) {
                checkBounds(data, offsetTablePos + i * 2, 2);
                int offset = u16(data, offsetTablePos + i * 2);
                if (offset == NO_ENTRY_16) {
                    continue;
                }
                entryIndex = i;
                entryOffset = offset * 4;
            } else {
                checkBounds(data, offsetTablePos + i * 4, 4);
                int offset = u32(data, offsetTablePos + i * 4);
                if (offset == NO_ENTRY) {
                    continue;
                }
                entryIndex = i;
                entryOffset = offset;
            }

            int entryPos = pos + entriesStart + entryOffset;
            checkBounds(data, entryPos, 8);
            int entrySize = u16(data, entryPos);
            if (entrySize == 0) {
                continue; // 空占位
            }
            int keyIndex = u32(data, entryPos + 4);
            if (keyIndex < 0 || keyIndex >= keyNames.length) {
                throw new ParseException("资源 key 下标越界: " + keyIndex,
                        GeneratorErrorCode.ERROR_APK_PARSE_FAILED);
            }
            String keyName = keyNames[keyIndex];
            if (keyName == null || keyName.isEmpty()) {
                continue;
            }

            int resourceId = (packageId << 24) | (typeId << 16) | entryIndex;
            builder.put(resourceId, typeName + "/" + keyName);
        }
    }

    /**
     * 读取一个字符串池 chunk，返回全部字符串（下标即池内索引）。
     */
    private static String[] readStringPool(byte[] data, int poolPos) throws ParseException {
        int type = u16(data, poolPos);
        if (type != TYPE_STRING_POOL) {
            throw new ParseException("期望 ResStringPool，实际 type=0x" + Integer.toHexString(type),
                    GeneratorErrorCode.ERROR_APK_PARSE_FAILED);
        }
        int headerSize = u16(data, poolPos + 2);
        int stringCount = u32(data, poolPos + 8);
        int flags = u32(data, poolPos + 16);
        int stringsStart = u32(data, poolPos + 20);
        boolean utf8 = (flags & STRING_POOL_UTF8_FLAG) != 0;

        if (stringCount < 0 || stringCount > 1_000_000) {
            throw new ParseException("字符串池数量异常: " + stringCount,
                    GeneratorErrorCode.ERROR_APK_PARSE_FAILED);
        }

        String[] result = new String[stringCount];
        for (int i = 0; i < stringCount; i++) {
            int offset = u32(data, poolPos + headerSize + i * 4);
            result[i] = readString(data, poolPos + stringsStart + offset, utf8);
        }
        return result;
    }

    /**
     * 读取池内单个字符串。UTF-8 与 UTF-16 的（可能两字节的）长度前缀都要支持。
     */
    private static String readString(byte[] data, int pos, boolean utf8) throws ParseException {
        if (utf8) {
            int charLen = u8(data, pos);
            int p = pos + 1;
            if ((charLen & 0x80) != 0) {
                charLen = ((charLen & 0x7F) << 8) | u8(data, p);
                p++;
            }
            int byteLen = u8(data, p);
            p++;
            if ((byteLen & 0x80) != 0) {
                byteLen = ((byteLen & 0x7F) << 8) | u8(data, p);
                p++;
            }
            checkBounds(data, p, byteLen + 1); // +1：结尾 NUL
            return new String(data, p, byteLen, StandardCharsets.UTF_8);
        }
        int charLen = u16(data, pos);
        int p = pos + 2;
        if ((charLen & 0x8000) != 0) {
            charLen = ((charLen & 0x7FFF) << 16) | u16(data, p);
            p += 2;
        }
        int byteLen = charLen * 2;
        checkBounds(data, p, byteLen + 2); // +2：结尾 UTF-16 NUL
        return new String(data, p, byteLen, StandardCharsets.UTF_16LE);
    }

    /**
     * 校验 chunk 头部：头部大小不小于 8、chunk 大小不小于头部且不越出缓冲区。
     */
    private static void checkChunk(byte[] data, int pos, int headerSize, int chunkSize)
            throws ParseException {
        if (headerSize < 8 || chunkSize < headerSize) {
            throw new ParseException("chunk 头部非法: headerSize=" + headerSize
                    + " size=" + chunkSize, GeneratorErrorCode.ERROR_APK_PARSE_FAILED);
        }
        checkBounds(data, pos, chunkSize);
    }

    private static void checkBounds(byte[] data, int pos, int len) throws ParseException {
        if (pos < 0 || len < 0 || pos + len > data.length) {
            throw new ParseException("resources.arsc 结构越界: pos=" + pos + " len=" + len
                    + " fileSize=" + data.length, GeneratorErrorCode.ERROR_APK_PARSE_FAILED);
        }
    }

    private static int u8(byte[] data, int pos) throws ParseException {
        checkBounds(data, pos, 1);
        return data[pos] & 0xFF;
    }

    private static int u16(byte[] data, int pos) throws ParseException {
        checkBounds(data, pos, 2);
        return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8);
    }

    private static int u32(byte[] data, int pos) throws ParseException {
        checkBounds(data, pos, 4);
        return (data[pos] & 0xFF) | ((data[pos + 1] & 0xFF) << 8)
                | ((data[pos + 2] & 0xFF) << 16) | ((data[pos + 3] & 0xFF) << 24);
    }
}
