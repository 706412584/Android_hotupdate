package com.orange.patch.plugin;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * nonFinalResIds 警告判定的单元测试。
 *
 * 规则（见 {@link GeneratePatchTask#shouldWarnAboutFinalResIds}）：
 * - android.nonFinalResIds=false 显式配置 → 警告；
 * - AGP &lt; 8（默认内联）→ 警告；
 * - 属性显式 true 或 AGP 8+ → 不警告；
 * - 都不可知 → 不警告（dex 层实测结果仍写入 patch.json 的 rFieldsFinal）。
 */
public class GeneratePatchTaskResIdWarningTest {

    // ==================== 显式属性 ====================

    @Test
    public void warnsWhenPropertyExplicitlyFalse() {
        assertTrue(GeneratePatchTask.shouldWarnAboutFinalResIds("false", "8.12.0"));
    }

    @Test
    public void doesNotWarnWhenPropertyExplicitlyTrue() {
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds("true", "7.4.2"));
    }

    @Test
    public void propertyTakesPrecedenceOverAgpVersion() {
        // 显式 true 时即使 AGP 老也不警告（用户已确认配置）
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds("true", "4.2.0"));
        // 显式 false 时即使 AGP 新也警告（配置被改回旧行为）
        assertTrue(GeneratePatchTask.shouldWarnAboutFinalResIds("false", "8.12.0"));
    }

    @Test
    public void handlesCaseAndWhitespaceInProperty() {
        assertTrue(GeneratePatchTask.shouldWarnAboutFinalResIds(" FALSE ", "8.12.0"));
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds("True", "7.0.0"));
    }

    // ==================== AGP 版本 ====================

    @Test
    public void warnsWhenAgpOlderThan8AndPropertyUnset() {
        assertTrue(GeneratePatchTask.shouldWarnAboutFinalResIds(null, "7.4.2"));
        assertTrue(GeneratePatchTask.shouldWarnAboutFinalResIds(null, "4.2.0"));
    }

    @Test
    public void doesNotWarnWhenAgp8OrNewerAndPropertyUnset() {
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds(null, "8.0.0"));
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds(null, "8.12.0"));
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds(null, "9.1.0"));
    }

    @Test
    public void handlesVersionSuffixes() {
        assertTrue(GeneratePatchTask.shouldWarnAboutFinalResIds(null, "7.4.2-alpha01"));
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds(null, "8.12.0-alpha01"));
    }

    // ==================== 未知情形 ====================

    @Test
    public void doesNotWarnWhenBothUnknown() {
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds(null, null));
    }

    @Test
    public void doesNotWarnForUnparseableValues() {
        assertFalse(GeneratePatchTask.shouldWarnAboutFinalResIds("yes", "unknown"));
    }

    @Test
    public void parseMajorVersion_extractsLeadingNumber() {
        assertEquals(Integer.valueOf(8), GeneratePatchTask.parseMajorVersion("8.12.0"));
        assertEquals(Integer.valueOf(7), GeneratePatchTask.parseMajorVersion("7.4.2-alpha01"));
        assertEquals(Integer.valueOf(9), GeneratePatchTask.parseMajorVersion("9"));
        assertNull(GeneratePatchTask.parseMajorVersion(null));
        assertNull(GeneratePatchTask.parseMajorVersion("unknown"));
        assertNull(GeneratePatchTask.parseMajorVersion(""));
    }
}
