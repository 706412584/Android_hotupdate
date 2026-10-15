package com.orange.patchgen.differ;

import java.util.ArrayList;
import java.util.List;

/**
 * 资源差异比较结果
 * 
 * 包含两个资源目录之间的差异信息：修改、新增、删除的文件。
 * 
 * Requirements: 3.2, 3.3, 3.4
 */
public class ResourceDiffResult {
    private List<FileChange> modifiedFiles;
    private List<FileChange> addedFiles;
    private List<String> deletedFiles;
    private boolean hasChanges;

    public ResourceDiffResult() {
        this.modifiedFiles = new ArrayList<>();
        this.addedFiles = new ArrayList<>();
        this.deletedFiles = new ArrayList<>();
        this.hasChanges = false;
    }

    public List<FileChange> getModifiedFiles() {
        return modifiedFiles;
    }

    public void setModifiedFiles(List<FileChange> modifiedFiles) {
        this.modifiedFiles = modifiedFiles;
        updateHasChanges();
    }

    public void addModifiedFile(FileChange fileChange) {
        if (this.modifiedFiles == null) {
            this.modifiedFiles = new ArrayList<>();
        }
        this.modifiedFiles.add(fileChange);
        this.hasChanges = true;
    }

    public List<FileChange> getAddedFiles() {
        return addedFiles;
    }

    public void setAddedFiles(List<FileChange> addedFiles) {
        this.addedFiles = addedFiles;
        updateHasChanges();
    }

    public void addAddedFile(FileChange fileChange) {
        if (this.addedFiles == null) {
            this.addedFiles = new ArrayList<>();
        }
        this.addedFiles.add(fileChange);
        this.hasChanges = true;
    }

    public List<String> getDeletedFiles() {
        return deletedFiles;
    }

    public void setDeletedFiles(List<String> deletedFiles) {
        this.deletedFiles = deletedFiles;
        updateHasChanges();
    }

    public void addDeletedFile(String relativePath) {
        if (this.deletedFiles == null) {
            this.deletedFiles = new ArrayList<>();
        }
        this.deletedFiles.add(relativePath);
        this.hasChanges = true;
    }

    public boolean hasChanges() {
        return hasChanges;
    }

    public void setHasChanges(boolean hasChanges) {
        this.hasChanges = hasChanges;
    }

    /**
     * 移除所有满足 {@code exclude} 的条目，并重算 {@link #hasChanges()}。
     *
     * <p><b>为什么在结果层过滤而不是在扫描层</b>：排除是「哪些差异不该进补丁」的
     * 决策，与「目录里有什么」无关。在这里过滤一次，{@code hasChanges()} 判定、
     * {@code copyChangedResources} 复制、以及 {@code PatchInfo.changes} 统计三处
     * **同时**生效；若分散到三处各自判断，任何一处漏掉都会让被排除的文件重新混进补丁。
     *
     * @param exclude 接收「相对路径」，返回 true 表示排除
     */
    public void removeIf(java.util.function.Predicate<String> exclude) {
        if (exclude == null) {
            return;
        }
        if (modifiedFiles != null) {
            modifiedFiles.removeIf(c -> c != null && exclude.test(c.getRelativePath()));
        }
        if (addedFiles != null) {
            addedFiles.removeIf(c -> c != null && exclude.test(c.getRelativePath()));
        }
        if (deletedFiles != null) {
            deletedFiles.removeIf(p -> p != null && exclude.test(p));
        }
        updateHasChanges();
    }

    private void updateHasChanges() {
        this.hasChanges = (modifiedFiles != null && !modifiedFiles.isEmpty()) ||
                          (addedFiles != null && !addedFiles.isEmpty()) ||
                          (deletedFiles != null && !deletedFiles.isEmpty());
    }

    /**
     * 获取所有变更文件的总数
     */
    public int getTotalChanges() {
        int count = 0;
        if (modifiedFiles != null) count += modifiedFiles.size();
        if (addedFiles != null) count += addedFiles.size();
        if (deletedFiles != null) count += deletedFiles.size();
        return count;
    }

    /**
     * 获取所有修改文件的相对路径列表
     */
    public List<String> getModifiedFilePaths() {
        List<String> paths = new ArrayList<>();
        if (modifiedFiles != null) {
            for (FileChange change : modifiedFiles) {
                paths.add(change.getRelativePath());
            }
        }
        return paths;
    }

    /**
     * 获取所有新增文件的相对路径列表
     */
    public List<String> getAddedFilePaths() {
        List<String> paths = new ArrayList<>();
        if (addedFiles != null) {
            for (FileChange change : addedFiles) {
                paths.add(change.getRelativePath());
            }
        }
        return paths;
    }

    /**
     * 合并另一个 ResourceDiffResult 到当前结果
     */
    public void merge(ResourceDiffResult other) {
        if (other == null) return;
        
        if (other.modifiedFiles != null) {
            for (FileChange change : other.modifiedFiles) {
                addModifiedFile(change);
            }
        }
        if (other.addedFiles != null) {
            for (FileChange change : other.addedFiles) {
                addAddedFile(change);
            }
        }
        if (other.deletedFiles != null) {
            for (String path : other.deletedFiles) {
                addDeletedFile(path);
            }
        }
    }

    @Override
    public String toString() {
        return "ResourceDiffResult{" +
                "modifiedFiles=" + (modifiedFiles != null ? modifiedFiles.size() : 0) +
                ", addedFiles=" + (addedFiles != null ? addedFiles.size() : 0) +
                ", deletedFiles=" + (deletedFiles != null ? deletedFiles.size() : 0) +
                ", hasChanges=" + hasChanges +
                '}';
    }
}
