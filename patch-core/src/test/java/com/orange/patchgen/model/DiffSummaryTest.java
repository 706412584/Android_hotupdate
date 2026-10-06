package com.orange.patchgen.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DiffSummary 单元测试
 *
 * 覆盖 hasChanges() 与 getTotalChanges() 的统计口径。
 */
public class DiffSummaryTest {

    @Test
    public void constructor_initializesEmptyModifiedFiles() {
        DiffSummary summary = new DiffSummary();

        assertThat(summary.getModifiedFiles()).isEmpty();
    }

    @Test
    public void freshSummary_reportsNoChanges() {
        DiffSummary summary = new DiffSummary();

        assertThat(summary.hasChanges()).isFalse();
        assertThat(summary.getTotalChanges()).isZero();
    }

    @Test
    public void hasChangesTrue_whenAnySingleCounterPositive() {
        // 九类计数任意一类大于 0，都应判定为有变更
        DiffSummary summary = new DiffSummary();
        summary.setModifiedClasses(1);
        assertThat(summary.hasChanges()).isTrue();

        assertThat(newWith(s -> s.setAddedClasses(1)).hasChanges()).isTrue();
        assertThat(newWith(s -> s.setDeletedClasses(1)).hasChanges()).isTrue();
        assertThat(newWith(s -> s.setModifiedResources(1)).hasChanges()).isTrue();
        assertThat(newWith(s -> s.setAddedResources(1)).hasChanges()).isTrue();
        assertThat(newWith(s -> s.setDeletedResources(1)).hasChanges()).isTrue();
        assertThat(newWith(s -> s.setModifiedAssets(1)).hasChanges()).isTrue();
        assertThat(newWith(s -> s.setAddedAssets(1)).hasChanges()).isTrue();
        assertThat(newWith(s -> s.setDeletedAssets(1)).hasChanges()).isTrue();
    }

    @Test
    public void totalChanges_sumsAllNineCounters() {
        DiffSummary summary = new DiffSummary();
        summary.setModifiedClasses(1);
        summary.setAddedClasses(2);
        summary.setDeletedClasses(3);
        summary.setModifiedResources(4);
        summary.setAddedResources(5);
        summary.setDeletedResources(6);
        summary.setModifiedAssets(7);
        summary.setAddedAssets(8);
        summary.setDeletedAssets(9);

        assertThat(summary.getTotalChanges()).isEqualTo(45);
    }

    @Test
    public void addModifiedFile_appendsToInternalList() {
        DiffSummary summary = new DiffSummary();

        summary.addModifiedFile("classes.dex");
        summary.addModifiedFile("res/layout/a.xml");

        assertThat(summary.getModifiedFiles())
                .containsExactly("classes.dex", "res/layout/a.xml");
    }

    @Test
    public void addModifiedFile_toleratesNullList() {
        DiffSummary summary = new DiffSummary();
        summary.setModifiedFiles(null);

        summary.addModifiedFile("x");

        assertThat(summary.getModifiedFiles()).containsExactly("x");
    }

    @Test
    public void modifiedFilesDoNotAffectHasChanges() {
        // hasChanges() 只看九类计数，modifiedFiles 列表不参与判定
        DiffSummary summary = new DiffSummary();
        summary.addModifiedFile("classes.dex");

        assertThat(summary.hasChanges()).isFalse();
        assertThat(summary.getTotalChanges()).isZero();
    }

    private static DiffSummary newWith(java.util.function.Consumer<DiffSummary> mutation) {
        DiffSummary summary = new DiffSummary();
        mutation.accept(summary);
        return summary;
    }
}
