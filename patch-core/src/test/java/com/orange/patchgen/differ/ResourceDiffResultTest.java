package com.orange.patchgen.differ;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResourceDiffResult 单元测试
 *
 * 覆盖三类文件的增删改记录、路径提取与 merge() 合并语义。
 */
public class ResourceDiffResultTest {

    @Test
    public void defaultConstructor_initializesEmptyAndNoChanges() {
        ResourceDiffResult result = new ResourceDiffResult();

        assertThat(result.getModifiedFiles()).isEmpty();
        assertThat(result.getAddedFiles()).isEmpty();
        assertThat(result.getDeletedFiles()).isEmpty();
        assertThat(result.hasChanges()).isFalse();
        assertThat(result.getTotalChanges()).isZero();
    }

    @Test
    public void addModifiedFile_setsHasChanges() {
        ResourceDiffResult result = new ResourceDiffResult();

        result.addModifiedFile(FileChange.modified("res/a.xml", "o", "n", 1L, 2L));

        assertThat(result.getModifiedFiles()).hasSize(1);
        assertThat(result.hasChanges()).isTrue();
        assertThat(result.getTotalChanges()).isEqualTo(1);
    }

    @Test
    public void addAddedFile_setsHasChanges() {
        ResourceDiffResult result = new ResourceDiffResult();
        result.addAddedFile(FileChange.added("res/new.xml", "m", 5L));

        assertThat(result.getAddedFiles()).hasSize(1);
        assertThat(result.hasChanges()).isTrue();
    }

    @Test
    public void addDeletedFile_setsHasChanges() {
        ResourceDiffResult result = new ResourceDiffResult();
        result.addDeletedFile("res/gone.xml");

        assertThat(result.getDeletedFiles()).containsExactly("res/gone.xml");
        assertThat(result.hasChanges()).isTrue();
    }

    @Test
    public void setModifiedFiles_recomputesFlagFromContent() {
        ResourceDiffResult result = new ResourceDiffResult();

        result.setModifiedFiles(new ArrayList<>());
        assertThat(result.hasChanges()).isFalse();

        result.setModifiedFiles(new ArrayList<>(
                Arrays.asList(FileChange.modified("res/a.xml", "o", "n", 1L, 2L))));
        assertThat(result.hasChanges()).isTrue();

        result.setModifiedFiles(new ArrayList<>());
        assertThat(result.hasChanges()).isFalse();
    }

    @Test
    public void setMethods_tolerateNull() {
        ResourceDiffResult result = new ResourceDiffResult();
        result.addDeletedFile("res/x.xml");

        result.setModifiedFiles(null);
        result.setAddedFiles(null);
        result.setDeletedFiles(null);

        assertThat(result.hasChanges()).isFalse();
        assertThat(result.getTotalChanges()).isZero();
    }

    @Test
    public void addMethods_tolerateNullLists() {
        ResourceDiffResult result = new ResourceDiffResult();
        result.setModifiedFiles(null);
        result.setAddedFiles(null);
        result.setDeletedFiles(null);

        result.addModifiedFile(FileChange.modified("res/m.xml", "o", "n", 1L, 2L));
        result.addAddedFile(FileChange.added("res/a.xml", "m", 1L));
        result.addDeletedFile("res/d.xml");

        assertThat(result.getTotalChanges()).isEqualTo(3);
    }

    @Test
    public void getModifiedFilePaths_extractsRelativePathsInOrder() {
        ResourceDiffResult result = new ResourceDiffResult();
        result.addModifiedFile(FileChange.modified("res/a.xml", "o", "n", 1L, 2L));
        result.addModifiedFile(FileChange.modified("res/b.xml", "o", "n", 1L, 2L));

        assertThat(result.getModifiedFilePaths())
                .containsExactly("res/a.xml", "res/b.xml");
    }

    @Test
    public void getAddedFilePaths_extractsRelativePathsInOrder() {
        ResourceDiffResult result = new ResourceDiffResult();
        result.addAddedFile(FileChange.added("res/new1.xml", "m", 1L));
        result.addAddedFile(FileChange.added("res/new2.xml", "m", 1L));

        assertThat(result.getAddedFilePaths())
                .containsExactly("res/new1.xml", "res/new2.xml");
    }

    @Test
    public void getModifiedFilePaths_isEmptyWhenNoModifiedFiles() {
        assertThat(new ResourceDiffResult().getModifiedFilePaths()).isEmpty();
        assertThat(new ResourceDiffResult().getAddedFilePaths()).isEmpty();
    }

    @Test
    public void merge_appendsAllThreeCategories() {
        ResourceDiffResult target = new ResourceDiffResult();
        target.addModifiedFile(FileChange.modified("res/existing.xml", "o", "n", 1L, 2L));

        ResourceDiffResult other = new ResourceDiffResult();
        other.addModifiedFile(FileChange.modified("res/other-m.xml", "o", "n", 1L, 2L));
        other.addAddedFile(FileChange.added("res/other-a.xml", "m", 1L));
        other.addDeletedFile("res/other-d.xml");

        target.merge(other);

        assertThat(target.getModifiedFilePaths())
                .containsExactly("res/existing.xml", "res/other-m.xml");
        assertThat(target.getAddedFilePaths()).containsExactly("res/other-a.xml");
        assertThat(target.getDeletedFiles()).containsExactly("res/other-d.xml");
        assertThat(target.getTotalChanges()).isEqualTo(4);
    }

    @Test
    public void merge_isNoOpForNull() {
        ResourceDiffResult target = new ResourceDiffResult();

        target.merge(null);

        assertThat(target.hasChanges()).isFalse();
        assertThat(target.getTotalChanges()).isZero();
    }

    @Test
    public void merge_intoEmptyResultSetsHasChanges() {
        ResourceDiffResult target = new ResourceDiffResult();
        ResourceDiffResult other = new ResourceDiffResult();
        other.addAddedFile(FileChange.added("res/a.xml", "m", 1L));

        target.merge(other);

        assertThat(target.hasChanges()).isTrue();
    }
}
