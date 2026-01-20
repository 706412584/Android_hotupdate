package com.orange.update;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 补丁信息类，包含补丁的元数据。
 * 支持 JSON 序列化和反序列化。
 */
public class PatchInfo {
    
    // JSON 字段名常量
    private static final String KEY_PATCH_ID = "patchId";
    private static final String KEY_PATCH_VERSION = "patchVersion";
    private static final String KEY_TARGET_APP_VERSION = "targetAppVersion";
    private static final String KEY_PACKAGE_NAME = "packageName";
    private static final String KEY_DOWNLOAD_URL = "downloadUrl";
    private static final String KEY_FILE_SIZE = "fileSize";
    private static final String KEY_MD5 = "md5";
    private static final String KEY_CREATE_TIME = "createTime";
    private static final String KEY_DESCRIPTION = "description";
    
    private String patchId;
    private String patchVersion;
    private String targetAppVersion;
    private String packageName;
    private String downloadUrl;
    private long fileSize;
    private String md5;
    private long createTime;
    private String description;
    
    public PatchInfo() {
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
    
    public String getTargetAppVersion() {
        return targetAppVersion;
    }
    
    public void setTargetAppVersion(String targetAppVersion) {
        this.targetAppVersion = targetAppVersion;
    }
    
    public String getPackageName() {
        return packageName;
    }
    
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    
    public String getDownloadUrl() {
        return downloadUrl;
    }
    
    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    /**
     * 将 PatchInfo 序列化为 JSON 字符串
     * @return JSON 字符串
     */
    public String toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put(KEY_PATCH_ID, patchId);
            json.put(KEY_PATCH_VERSION, patchVersion);
            json.put(KEY_TARGET_APP_VERSION, targetAppVersion);
            json.put(KEY_PACKAGE_NAME, packageName);
            json.put(KEY_DOWNLOAD_URL, downloadUrl);
            json.put(KEY_FILE_SIZE, fileSize);
            json.put(KEY_MD5, md5);
            json.put(KEY_CREATE_TIME, createTime);
            json.put(KEY_DESCRIPTION, description);
            return json.toString();
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize PatchInfo to JSON", e);
        }
    }
    
    /**
     * 从 JSON 字符串反序列化为 PatchInfo 对象
     * @param json JSON 字符串
     * @return PatchInfo 对象
     * @throws IllegalArgumentException 如果 JSON 格式无效或缺少必要字段
     */
    public static PatchInfo fromJson(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        
        try {
            JSONObject jsonObject = new JSONObject(json);
            PatchInfo patchInfo = new PatchInfo();
            
            // 验证并解析必要字段
            patchInfo.setPatchId(getRequiredString(jsonObject, KEY_PATCH_ID));
            patchInfo.setPatchVersion(getRequiredString(jsonObject, KEY_PATCH_VERSION));
            patchInfo.setDownloadUrl(getRequiredString(jsonObject, KEY_DOWNLOAD_URL));
            patchInfo.setMd5(getRequiredString(jsonObject, KEY_MD5));
            
            // 解析可选字段
            patchInfo.setTargetAppVersion(jsonObject.optString(KEY_TARGET_APP_VERSION, null));
            patchInfo.setPackageName(jsonObject.optString(KEY_PACKAGE_NAME, null));
            patchInfo.setFileSize(jsonObject.optLong(KEY_FILE_SIZE, 0));
            patchInfo.setCreateTime(jsonObject.optLong(KEY_CREATE_TIME, 0));
            patchInfo.setDescription(jsonObject.optString(KEY_DESCRIPTION, null));
            
            // 验证字段值
            patchInfo.validate();
            
            return patchInfo;
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从 JSONObject 获取必要的字符串字段
     */
    private static String getRequiredString(JSONObject json, String key) throws JSONException {
        if (!json.has(key) || json.isNull(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        String value = json.getString(key);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Field cannot be empty: " + key);
        }
        return value;
    }
    
    /**
     * 验证 PatchInfo 字段值的有效性
     * @throws IllegalArgumentException 如果字段值无效
     */
    public void validate() {
        if (patchId == null || patchId.isEmpty()) {
            throw new IllegalArgumentException("Patch ID cannot be null or empty");
        }
        if (patchVersion == null || patchVersion.isEmpty()) {
            throw new IllegalArgumentException("Patch version cannot be null or empty");
        }
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            throw new IllegalArgumentException("Download URL cannot be null or empty");
        }
        if (md5 == null || md5.isEmpty()) {
            throw new IllegalArgumentException("MD5 cannot be null or empty");
        }
        if (fileSize < 0) {
            throw new IllegalArgumentException("File size cannot be negative");
        }
        if (createTime < 0) {
            throw new IllegalArgumentException("Create time cannot be negative");
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        PatchInfo patchInfo = (PatchInfo) o;
        
        if (fileSize != patchInfo.fileSize) return false;
        if (createTime != patchInfo.createTime) return false;
        if (patchId != null ? !patchId.equals(patchInfo.patchId) : patchInfo.patchId != null)
            return false;
        if (patchVersion != null ? !patchVersion.equals(patchInfo.patchVersion) : patchInfo.patchVersion != null)
            return false;
        if (targetAppVersion != null ? !targetAppVersion.equals(patchInfo.targetAppVersion) : patchInfo.targetAppVersion != null)
            return false;
        if (packageName != null ? !packageName.equals(patchInfo.packageName) : patchInfo.packageName != null)
            return false;
        if (downloadUrl != null ? !downloadUrl.equals(patchInfo.downloadUrl) : patchInfo.downloadUrl != null)
            return false;
        if (md5 != null ? !md5.equals(patchInfo.md5) : patchInfo.md5 != null)
            return false;
        return description != null ? description.equals(patchInfo.description) : patchInfo.description == null;
    }
    
    @Override
    public int hashCode() {
        int result = patchId != null ? patchId.hashCode() : 0;
        result = 31 * result + (patchVersion != null ? patchVersion.hashCode() : 0);
        result = 31 * result + (targetAppVersion != null ? targetAppVersion.hashCode() : 0);
        result = 31 * result + (packageName != null ? packageName.hashCode() : 0);
        result = 31 * result + (downloadUrl != null ? downloadUrl.hashCode() : 0);
        result = 31 * result + (int) (fileSize ^ (fileSize >>> 32));
        result = 31 * result + (md5 != null ? md5.hashCode() : 0);
        result = 31 * result + (int) (createTime ^ (createTime >>> 32));
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return "PatchInfo{" +
                "patchId='" + patchId + '\'' +
                ", patchVersion='" + patchVersion + '\'' +
                ", targetAppVersion='" + targetAppVersion + '\'' +
                ", packageName='" + packageName + '\'' +
                ", downloadUrl='" + downloadUrl + '\'' +
                ", fileSize=" + fileSize +
                ", md5='" + md5 + '\'' +
                ", createTime=" + createTime +
                ", description='" + description + '\'' +
                '}';
    }
}
