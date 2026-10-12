package com.orange.update;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * VersionUtils 单元测试
 *
 * 版本比较决定是否下发/应用补丁，属于安全相关判定，这里锁定其语义。
 * 本类无 Android 依赖，可直接以普通 JUnit 运行。
 */
public class VersionUtilsTest {

    // ---------- compareVersion ----------

    @Test
    public void compareVersion_returnsZeroForEqualVersions() {
        assertThat(VersionUtils.compareVersion("1.0.0", "1.0.0")).isZero();
        assertThat(VersionUtils.compareVersion("0.0.0", "0.0.0")).isZero();
    }

    @Test
    public void compareVersion_returnsPositiveWhenFirstIsGreater() {
        assertThat(VersionUtils.compareVersion("1.0.1", "1.0.0")).isPositive();
        assertThat(VersionUtils.compareVersion("1.1.0", "1.0.9")).isPositive();
        assertThat(VersionUtils.compareVersion("2.0.0", "1.9.9")).isPositive();
    }

    @Test
    public void compareVersion_returnsNegativeWhenFirstIsSmaller() {
        assertThat(VersionUtils.compareVersion("1.0.0", "1.0.1")).isNegative();
        assertThat(VersionUtils.compareVersion("1.9.9", "2.0.0")).isNegative();
    }

    @Test
    public void compareVersion_comparesNumericallyNotLexicographically() {
        // "10" 必须大于 "9"，若按字符串比较会得出相反结论
        assertThat(VersionUtils.compareVersion("1.10.0", "1.9.0")).isPositive();
        assertThat(VersionUtils.compareVersion("1.9.0", "1.10.0")).isNegative();
    }

    @Test
    public void compareVersion_padsMissingSegmentsWithZero() {
        assertThat(VersionUtils.compareVersion("1.0", "1.0.0")).isZero();
        assertThat(VersionUtils.compareVersion("1", "1.0.0")).isZero();
        assertThat(VersionUtils.compareVersion("1.0.1", "1.0")).isPositive();
    }

    @Test
    public void compareVersion_toleratesWhitespaceAroundSegments() {
        assertThat(VersionUtils.compareVersion(" 1 . 0 . 0 ", "1.0.0")).isZero();
    }

    @Test
    public void compareVersion_throwsForNullArguments() {
        assertThatThrownBy(() -> VersionUtils.compareVersion(null, "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version cannot be null");

        assertThatThrownBy(() -> VersionUtils.compareVersion("1.0.0", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version cannot be null");
    }

    @Test
    public void compareVersion_throwsForNonNumericSegment() {
        assertThatThrownBy(() -> VersionUtils.compareVersion("1.x.0", "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid version format");
    }

    @Test
    public void compareVersion_throwsForNegativeSegment() {
        assertThatThrownBy(() -> VersionUtils.compareVersion("1.-1.0", "1.0.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be negative");
    }

    // ---------- isNewerVersion ----------

    @Test
    public void isNewerVersion_trueOnlyWhenServerIsGreater() {
        assertThat(VersionUtils.isNewerVersion("1.0.1", "1.0.0")).isTrue();
        assertThat(VersionUtils.isNewerVersion("2.0.0", "1.9.9")).isTrue();
    }

    @Test
    public void isNewerVersion_falseForEqualOrOlder() {
        assertThat(VersionUtils.isNewerVersion("1.0.0", "1.0.0")).isFalse();
        assertThat(VersionUtils.isNewerVersion("0.9.9", "1.0.0")).isFalse();
    }

    @Test
    public void isNewerVersion_doesNotDowngradeOnMultiSegmentEquality() {
        // "1.0" 与 "1.0.0" 等价，不应被判为新版本（避免无谓下发补丁）
        assertThat(VersionUtils.isNewerVersion("1.0", "1.0.0")).isFalse();
    }

    // ---------- isValidVersion ----------

    @Test
    public void isValidVersion_trueForWellFormedVersions() {
        assertThat(VersionUtils.isValidVersion("1.0.0")).isTrue();
        assertThat(VersionUtils.isValidVersion("1")).isTrue();
        assertThat(VersionUtils.isValidVersion("10.20.30")).isTrue();
        assertThat(VersionUtils.isValidVersion("1.0.0.0")).isTrue();
    }

    @Test
    public void isValidVersion_falseForNullOrEmpty() {
        assertThat(VersionUtils.isValidVersion(null)).isFalse();
        assertThat(VersionUtils.isValidVersion("")).isFalse();
    }

    @Test
    public void isValidVersion_falseForNonNumericSegments() {
        assertThat(VersionUtils.isValidVersion("1.x.0")).isFalse();
        assertThat(VersionUtils.isValidVersion("v1.0.0")).isFalse();
        assertThat(VersionUtils.isValidVersion("1.0.0-beta")).isFalse();
    }

    @Test
    public void isValidVersion_falseForNegativeSegments() {
        assertThat(VersionUtils.isValidVersion("1.-1.0")).isFalse();
    }

    @Test
    public void isValidVersion_acceptsWhitespacePaddedSegments() {
        assertThat(VersionUtils.isValidVersion(" 1 . 0 . 0 ")).isTrue();
    }

    // ---------- 工具类约定 ----------

    @Test
    public void classIsNotInstantiable() throws Exception {
        java.lang.reflect.Constructor<VersionUtils> ctor =
                VersionUtils.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(ctor.getModifiers())).isTrue();
    }
}
