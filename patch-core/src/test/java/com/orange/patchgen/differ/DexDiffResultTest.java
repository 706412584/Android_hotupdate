package com.orange.patchgen.differ;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DexDiffResult 单元测试
 *
 * 重点覆盖 addXxx 与 setXxx 两条路径对 hasChanges 标记的不同处理：
 * addXxx 直接置位，setXxx 则依据列表内容重算。
 */
public class DexDiffResultTest {

    @Test
    public void defaultConstructor_initializesEmptyListsAndNoChanges() {
        DexDiffResult result = new DexDiffResult();

        assertThat(result.getModifiedClasses()).isEmpty();
        assertThat(result.getAddedClasses()).isEmpty();
        assertThat(result.getDeletedClasses()).isEmpty();
        assertThat(result.hasChanges()).isFalse();
        assertThat(result.getDexName()).isNull();
    }

    @Test
    public void namedConstructor_setsDexName() {
        DexDiffResult result = new DexDiffResult("classes.dex");

        assertThat(result.getDexName()).isEqualTo("classes.dex");
        assertThat(result.getModifiedClasses()).isEmpty();
    }

    @Test
    public void addModifiedClass_setsHasChangesAndAppends() {
        DexDiffResult result = new DexDiffResult("classes.dex");

        result.addModifiedClass("com.example.A");
        result.addModifiedClass("com.example.B");

        assertThat(result.getModifiedClasses()).containsExactly("com.example.A", "com.example.B");
        assertThat(result.hasChanges()).isTrue();
    }

    @Test
    public void addAddedClass_setsHasChangesAndAppends() {
        DexDiffResult result = new DexDiffResult();
        result.addAddedClass("com.example.New");

        assertThat(result.getAddedClasses()).containsExactly("com.example.New");
        assertThat(result.hasChanges()).isTrue();
    }

    @Test
    public void addDeletedClass_setsHasChangesAndAppends() {
        DexDiffResult result = new DexDiffResult();
        result.addDeletedClass("com.example.Gone");

        assertThat(result.getDeletedClasses()).containsExactly("com.example.Gone");
        assertThat(result.hasChanges()).isTrue();
    }

    @Test
    public void setModifiedClasses_recomputesHasChangesFromContent() {
        DexDiffResult result = new DexDiffResult();

        // 空列表 -> 无变更
        result.setModifiedClasses(new ArrayList<>());
        assertThat(result.hasChanges()).isFalse();

        // 非空列表 -> 有变更
        result.setModifiedClasses(new ArrayList<>(Arrays.asList("com.example.A")));
        assertThat(result.hasChanges()).isTrue();

        // 再次置空 -> 标记应被重算回 false
        result.setModifiedClasses(new ArrayList<>());
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    public void setModifiedClasses_toleratesNullAndClearsFlag() {
        DexDiffResult result = new DexDiffResult();
        result.addModifiedClass("com.example.A");
        assertThat(result.hasChanges()).isTrue();

        result.setModifiedClasses(null);

        assertThat(result.hasChanges()).isFalse();
        assertThat(result.getTotalChanges()).isZero();
    }

    @Test
    public void addMethods_tolerateNullLists() {
        DexDiffResult result = new DexDiffResult();
        result.setModifiedClasses(null);
        result.setAddedClasses(null);
        result.setDeletedClasses(null);

        result.addModifiedClass("m");
        result.addAddedClass("a");
        result.addDeletedClass("d");

        assertThat(result.getModifiedClasses()).containsExactly("m");
        assertThat(result.getAddedClasses()).containsExactly("a");
        assertThat(result.getDeletedClasses()).containsExactly("d");
    }

    @Test
    public void setHasChanges_canOverrideComputedValue() {
        // 显式 setter 优先于内容推导，用于上层强制指定标记
        DexDiffResult result = new DexDiffResult();
        result.setHasChanges(true);

        assertThat(result.hasChanges()).isTrue();
        assertThat(result.getTotalChanges()).isZero();
    }

    @Test
    public void getTotalChanges_sumsAllThreeLists() {
        DexDiffResult result = new DexDiffResult();
        result.addModifiedClass("m1");
        result.addModifiedClass("m2");
        result.addAddedClass("a1");
        result.addDeletedClass("d1");

        assertThat(result.getTotalChanges()).isEqualTo(4);
    }

    @Test
    public void getTotalChanges_isZeroForFreshInstance() {
        assertThat(new DexDiffResult().getTotalChanges()).isZero();
    }

    @Test
    public void toString_reportsCounts() {
        DexDiffResult result = new DexDiffResult("classes.dex");
        result.addModifiedClass("m");

        assertThat(result.toString())
                .contains("classes.dex")
                .contains("modifiedClasses=1");
    }
}
