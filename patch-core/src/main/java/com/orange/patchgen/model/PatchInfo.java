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

    /**
     * 补丁携带资源时，base 与 new 的资源 ID 是否对齐（见 docs/RESOURCE_ID_STABILITY.md）。
     *
     * <p>补丁整体替换 resources.arsc，而资源 ID 会被内联进 dex 字节码与二进制 XML。
     * 若新版增删资源导致既有资源 ID 重排，未被差分 dex 覆盖的旧代码会静默引用到
     * 错误资源。消费侧（如 ACS）据此字段决定是否放行含资源的补丁：
     * {@code optBoolean("resourceIdsStable", false)}——缺失视为不稳定。
     *
     * <p>null 表示补丁不含资源变更（此字段无意义，JSON 中省略）。
     */
    private Boolean resourceIdsStable;

    /**
     * base APK 的 R 类静态 int 字段是否带 ACC_FINAL（dex 层实测，见
     * {@code com.orange.patchgen.parser.RFieldAnalyzer}）。
     *
     * <p>final 表示资源 ID 被编译期内联进调用点——资源漂移的放大器。仅为诊断
     * 信息：null = 未能判定（如 R 类被裁剪），true/false = 实测结果。
     */
    private Boolean rFieldsFinal;

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
     * 资源 ID 是否对齐；null = 补丁不含资源变更。
     *
     * <p>注意返回类型是 {@link Boolean}：Gson 会把缺失字段反序列化为 null，
     * 与「显式 false」区分开。消费侧判断用
     * {@code optBoolean("resourceIdsStable", false)}，两者等价于不放行。
     */
    public Boolean getResourceIdsStable() {
        return resourceIdsStable;
    }

    public void setResourceIdsStable(Boolean resourceIdsStable) {
        this.resourceIdsStable = resourceIdsStable;
    }

    /** base APK 的 R 类字段是否 final；null = 未能判定。 */
    public Boolean getRFieldsFinal() {
        return rFieldsFinal;
    }

    public void setRFieldsFinal(Boolean rFieldsFinal) {
        this.rFieldsFinal = rFieldsFinal;
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
