package com.orange.patchgen.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 从 resources.arsc 提取的「资源 ID ↔ 资源名」映射。
 *
 * <p><b>为什么需要它</b>：热更新补丁会整体替换 resources.arsc，而 R.string.foo
 * 编译后是内联进调用点的**字面量**。补丁只带差分 dex——未变更的类仍在设备上跑，
 * 它们内联的是**旧版本**的 ID。若新版本把某个 ID 分配给了别的资源，旧类就会
 * 静默显示错误内容（实测 ACS r11→r12：10308 个共有资源里 981 个 ID 变了，
 * 主页「打开项目」显示成「找不到未解析的类名」）。
 *
 * <p>因此生成侧必须能比较两个 APK 的资源 ID 是否对齐——判据见
 * {@link #isStableAgainst(ArscResourceIds)}。运行时（App 端）解析 arsc 代价
 * 与风险都高，所以这件事只在这里做。
 *
 * <p>纯 JVM 实现，不依赖任何 Android 类（patch-core 需同时服务 CLI / Gradle
 * 插件 / Android SDK 三条产出路径）。
 */
public class ArscResourceIds {

    /** 资源 ID → 资源名（如 {@code string/app_name}、{@code layout/main}）。 */
    private final Map<Integer, String> idToName;

    /** 资源名 → 资源 ID。 */
    private final Map<String, Integer> nameToId;

    ArscResourceIds(Map<Integer, String> idToName, Map<String, Integer> nameToId) {
        this.idToName = idToName;
        this.nameToId = nameToId;
    }

    /** 全部资源数（按 ID 去重；多配置条目共用同一 ID）。 */
    public int size() {
        return idToName.size();
    }

    public boolean isEmpty() {
        return idToName.isEmpty();
    }

    /** 资源 ID → 资源名；无此 ID 返回 null。 */
    public String getName(int resourceId) {
        return idToName.get(resourceId);
    }

    /** 资源名 → 资源 ID；无此资源返回 null。 */
    public Integer getId(String name) {
        return nameToId.get(name);
    }

    public Map<Integer, String> getIdToName() {
        return Collections.unmodifiableMap(idToName);
    }

    /**
     * 「资源 ID 对齐」判据：base 中每个资源（按名匹配）在 new 中仍是**同一个 ID**。
     *
     * <p>允许 new 新增资源（占用新 ID 即可）——它们不与 base 的 ID 冲突。
     * 被删除的资源视为不对齐（旧代码引用它会解析失败；虽然会抛异常而非静默
     * 错位，但仍属于版本间语义变化，交由调用方决定如何处理）。
     *
     * @param base 旧版本的资源表
     * @param updated 新版本的资源表
     * @return 对齐返回 true；任一 base 资源缺失或换了 ID 返回 false
     */
    public static boolean isStableAgainst(ArscResourceIds base, ArscResourceIds updated) {
        if (base == null || updated == null) {
            return false;
        }
        for (Map.Entry<Integer, String> entry : base.idToName.entrySet()) {
            Integer newId = updated.nameToId.get(entry.getValue());
            if (newId == null || !newId.equals(entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 找出 base 中「换了 ID 或已删除」的资源，最多返回 {@code limit} 条，
     * 供错误信息展示。
     *
     * @return 每项形如 {@code string/app_name: 0x7f010001 -> 0x7f010002} 或
     *         {@code string/app_name: 0x7f010001 -> (已删除)}
     */
    public static java.util.List<String> findMoved(ArscResourceIds base,
                                                   ArscResourceIds updated,
                                                   int limit) {
        java.util.List<String> moved = new java.util.ArrayList<>();
        if (base == null || updated == null) {
            return moved;
        }
        for (Map.Entry<Integer, String> entry : base.idToName.entrySet()) {
            if (moved.size() >= limit) {
                break;
            }
            Integer newId = updated.nameToId.get(entry.getValue());
            if (newId == null) {
                moved.add(String.format("%s: 0x%08x -> (已删除)", entry.getValue(), entry.getKey()));
            } else if (!newId.equals(entry.getKey())) {
                moved.add(String.format("%s: 0x%08x -> 0x%08x",
                        entry.getValue(), entry.getKey(), newId));
            }
        }
        return moved;
    }

    /** 供解析器构建用。 */
    static class Builder {
        private final Map<Integer, String> idToName = new HashMap<>();
        private final Map<String, Integer> nameToId = new HashMap<>();

        void put(int resourceId, String name) {
            // 同一 ID 在多配置条目下会重复出现，保留首次（名字应一致）。
            if (!idToName.containsKey(resourceId)) {
                idToName.put(resourceId, name);
            }
            nameToId.putIfAbsent(name, resourceId);
        }

        ArscResourceIds build() {
            return new ArscResourceIds(idToName, nameToId);
        }
    }
}
