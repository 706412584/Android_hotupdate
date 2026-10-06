package com.orange.patchgen.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PatchChanges 单元测试
 *
 * 覆盖 dex / resources / assets 三路变更容器的默认值与 hasChanges() 判定。
 */
public class PatchChangesTest {

    @Test
    public void constructor_initializesAllThreeCategories() {
        PatchChanges changes = new PatchChanges();

        assertThat(changes.getDex()).isNotNull();
        assertThat(changes.getResources()).isNotNull();
        assertThat(changes.getAssets()).isNotNull();
    }

    @Test
    public void fileChanges_constructorInitializesEmptyLists() {
        PatchChanges.FileChanges fc = new PatchChanges.FileChanges();

        assertThat(fc.getModified()).isEmpty();
        assertThat(fc.getAdded()).isEmpty();
        assertThat(fc.getDeleted()).isEmpty();
    }

    @Test
    public void fileChanges_hasChangesFalseWhenAllEmpty() {
        assertThat(new PatchChanges.FileChanges().hasChanges()).isFalse();
    }

    @Test
    public void fileChanges_hasChangesTrueForEachCategory() {
        PatchChanges.FileChanges modified = new PatchChanges.FileChanges();
        modified.addModified("a");
        assertThat(modified.hasChanges()).isTrue();

        PatchChanges.FileChanges added = new PatchChanges.FileChanges();
        added.addAdded("b");
        assertThat(added.hasChanges()).isTrue();

        PatchChanges.FileChanges deleted = new PatchChanges.FileChanges();
        deleted.addDeleted("c");
        assertThat(deleted.hasChanges()).isTrue();
    }

    @Test
    public void fileChanges_hasChangesFalseWhenListsExplicitlyNull() {
        // hasChanges() 对 null 列表做了空值保护，不应抛异常
        PatchChanges.FileChanges fc = new PatchChanges.FileChanges();
        fc.setModified(null);
        fc.setAdded(null);
        fc.setDeleted(null);

        assertThat(fc.hasChanges()).isFalse();
    }

    @Test
    public void fileChanges_addMethodsTolerateNullLists() {
        // addXxx() 在列表被置 null 后应能自愈（内部重新分配）
        PatchChanges.FileChanges fc = new PatchChanges.FileChanges();
        fc.setModified(null);
        fc.setAdded(null);
        fc.setDeleted(null);

        fc.addModified("m");
        fc.addAdded("a");
        fc.addDeleted("d");

        assertThat(fc.getModified()).containsExactly("m");
        assertThat(fc.getAdded()).containsExactly("a");
        assertThat(fc.getDeleted()).containsExactly("d");
    }

    @Test
    public void fileChanges_setterReplacesList() {
        PatchChanges.FileChanges fc = new PatchChanges.FileChanges();
        fc.addModified("old");

        fc.setModified(new ArrayList<>(Arrays.asList("new1", "new2")));

        assertThat(fc.getModified()).containsExactly("new1", "new2");
        assertThat(fc.hasChanges()).isTrue();
    }

    @Test
    public void settersReplaceCategoryObjects() {
        PatchChanges changes = new PatchChanges();

        PatchChanges.FileChanges replacement = new PatchChanges.FileChanges();
        replacement.addAdded("only-here");
        changes.setDex(replacement);

        assertThat(changes.getDex().getAdded()).containsExactly("only-here");
    }
}
