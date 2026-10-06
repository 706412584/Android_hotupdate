package com.orange.patchgen.differ;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FileChange 单元测试
 *
 * 覆盖两个静态工厂与 equals/hashCode 的「仅按路径比较」语义。
 */
public class FileChangeTest {

    @Test
    public void added_setsOnlyNewSide() {
        FileChange change = FileChange.added("res/a.xml", "newmd5", 42L);

        assertThat(change.getRelativePath()).isEqualTo("res/a.xml");
        assertThat(change.getNewMd5()).isEqualTo("newmd5");
        assertThat(change.getNewSize()).isEqualTo(42L);
        assertThat(change.getOldMd5()).isNull();
        assertThat(change.getOldSize()).isZero();
    }

    @Test
    public void modified_setsBothSides() {
        FileChange change = FileChange.modified("res/a.xml", "oldmd5", "newmd5", 10L, 20L);

        assertThat(change.getRelativePath()).isEqualTo("res/a.xml");
        assertThat(change.getOldMd5()).isEqualTo("oldmd5");
        assertThat(change.getNewMd5()).isEqualTo("newmd5");
        assertThat(change.getOldSize()).isEqualTo(10L);
        assertThat(change.getNewSize()).isEqualTo(20L);
    }

    @Test
    public void equals_isBasedOnRelativePathOnly() {
        FileChange a = FileChange.modified("res/a.xml", "m1", "m2", 1L, 2L);
        FileChange b = FileChange.added("res/a.xml", "totally-different", 999L);

        // 路径相同即相等，即便 md5 / size 完全不同
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    public void equals_isFalseForDifferentPaths() {
        FileChange a = FileChange.added("res/a.xml", "m", 1L);
        FileChange b = FileChange.added("res/b.xml", "m", 1L);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    public void equals_handlesNullRelativePath() {
        FileChange nullPathA = new FileChange();
        FileChange nullPathB = new FileChange();
        FileChange withPath = FileChange.added("res/a.xml", "m", 1L);

        assertThat(nullPathA).isEqualTo(nullPathB);
        assertThat(nullPathA.hashCode()).isZero();
        assertThat(nullPathA).isNotEqualTo(withPath);
    }

    @Test
    public void equals_isReflexiveAndNullSafe() {
        FileChange change = FileChange.added("res/a.xml", "m", 1L);

        assertThat(change).isEqualTo(change);
        assertThat(change).isNotEqualTo(null);
        assertThat(change).isNotEqualTo("a string");
    }

    @Test
    public void toString_containsPath() {
        FileChange change = FileChange.added("res/a.xml", "m", 1L);

        assertThat(change.toString()).contains("res/a.xml");
    }
}
