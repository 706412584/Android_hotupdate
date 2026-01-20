package com.orange.patchgen.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 补丁元信息 (patch.json)
 * 
 * Requirements: 5.1-5.8
 */
public class PatchInfo {
    private String patchId;
    private String patchVersion;
    private String packageName;
    private String baseVersion;
    private int baseVersionCode;
    private String targetVersion;
    private int targetVersionCode;
    private String patchMode;       // full_dex or bsdiff
    private String md5;
    private String sha256;
    private long createTime;
    private long fileSize;
    private String description;
    private PatchChanges changes;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public PatchInfo() {
        this.changes = new PatchChanges();
        this.createTime = System.currentTimeMillis();
    }

    // JSON serialization
    public String toJson() {
        return GSON.toJson(this);
    }

    public static PatchInfo fromJson(String json) {
        return GSON.fromJson(json, PatchInfo.class);
    }

    // Getters and Setters
    public String getPatchId() {
        return patchId;
    }

    public void setPatchId(String patchId) {
        this.patchId = patchId;
    }

    public String getPatchVersion() {
        return patchVersion;
    }

    public void setPatchVersion(String patchVersion) {
        this.patchVersion = patchVersion;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(String baseVersion) {
        this.baseVersion = baseVersion;
    }

    public int getBaseVersionCode() {
        return baseVersionCode;
    }

    public void setBaseVersionCode(int baseVersionCode) {
        this.baseVersionCode = baseVersionCode;
    }

    public String getTargetVersion() {
        return targetVersion;
    }

    public void setTargetVersion(String targetVersion) {
        this.targetVersion = targetVersion;
    }

    public int getTargetVersionCode() {
        return targetVersionCode;
    }

    public void setTargetVersionCode(int targetVersionCode) {
        this.targetVersionCode = targetVersionCode;
    }

    public String getPatchMode() {
        return patchMode;
    }

    public void setPatchMode(String patchMode) {
        this.patchMode = patchMode;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PatchChanges getChanges() {
        return changes;
    }

    public void setChanges(PatchChanges changes) {
        this.changes = changes;
    }

    /**
     * 验证 PatchInfo 是否包含所有必需字段
     */
    public boolean isValid() {
        return patchId != null && !patchId.isEmpty() &&
               patchVersion != null && !patchVersion.isEmpty() &&
               baseVersion != null && !baseVersion.isEmpty() &&
               targetVersion != null && !targetVersion.isEmpty() &&
               md5 != null && md5.length() == 32 &&
               createTime > 0 &&
               changes != null;
    }
}
