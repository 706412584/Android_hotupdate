package com.orange.patchgen.packer;

import com.orange.patchgen.model.PatchInfo;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 补丁打包器
 * 
 * 将差异内容打包为补丁 zip 文件，包含：
 * - patch.json: 补丁元信息
 * - classes.dex / classes[N].dex: 修改的 dex 文件
 * - res/: 修改的资源文件
 * - assets/: 修改的 assets 文件
 * - *.bsdiff: BsDiff 模式下的差异文件
 * 
 * Requirements: 4.1-4.6
 */
public class PatchPacker {

    private static final String PATCH_JSON = "patch.json";
    private static final String RES_DIR = "res";
    private static final String ASSETS_DIR = "assets";

    /**
     * 打包补丁
     * 
     * @param content 打包内容
     * @param outputFile 输出文件
     * @return 打包后的补丁文件
     * @throws PatchPackException 打包失败时抛出
     */
    public File pack(PackContent content, File outputFile) throws PatchPackException {
        if (content == null) {
            throw new PatchPackException("PackContent cannot be null");
        }
        if (!content.isValid()) {
            throw new PatchPackException("PackContent is not valid: PatchInfo is missing or invalid");
        }
        if (outputFile == null) {
            throw new PatchPackException("Output file cannot be null");
        }

        try {
            // 确保输出目录存在
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 删除已存在的文件（改进的删除逻辑）
            if (outputFile.exists()) {
                // 尝试删除，如果失败则等待后重试
                if (!outputFile.delete()) {
                    // 强制垃圾回收，释放可能的文件句柄
                    System.gc();
                    Thread.sleep(100);
                    
                    // 再次尝试删除
                    if (!outputFile.delete()) {
                        throw new PatchPackException("cannot delete old zip file");
                    }
                }
            }

            // 创建 zip 文件
            try (ZipFile zipFile = new ZipFile(outputFile)) {
                // 1. 添加 patch.json
                addPatchJson(zipFile, content.getPatchInfo());

                // 2. 添加 dex 文件
                if (content.hasDexChanges()) {
                    addDexFiles(zipFile, content.getDexFiles());
                }

                // 3. 添加资源目录
                if (content.hasResourceChanges()) {
                    addResourceDir(zipFile, content.getResDir());
                }

                // 4. 添加 resources.arsc（资源热更新必需）
                if (content.hasResourcesArsc()) {
                    addResourcesArsc(zipFile, content.getResourcesArsc());
                }

                // 5. 添加 assets 目录
                if (content.hasAssetChanges()) {
                    addAssetsDir(zipFile, content.getAssetsDir());
                }

                // 6. 添加 bsdiff 文件
                if (content.hasBsdiffFiles()) {
                    addBsdiffFiles(zipFile, content.getBsdiffFiles());
                }
            }

            // 更新 PatchInfo 中的文件大小和 MD5
            updatePatchInfoWithFileInfo(content.getPatchInfo(), outputFile);

            return outputFile;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PatchPackException("Failed to pack patch: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new PatchPackException("Failed to pack patch: " + e.getMessage(), e);
        }
    }

    /**
     * 添加 patch.json 到 zip
     */
    private void addPatchJson(ZipFile zipFile, PatchInfo patchInfo) throws IOException {
        String json = patchInfo.toJson();
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        
        ZipParameters params = createZipParameters(PATCH_JSON);
        zipFile.addStream(new java.io.ByteArrayInputStream(jsonBytes), params);
    }

    /**
     * 添加 dex 文件到 zip
     */
    private void addDexFiles(ZipFile zipFile, List<File> dexFiles) throws IOException {
        for (File dexFile : dexFiles) {
            if (dexFile != null && dexFile.exists()) {
                ZipParameters params = createZipParameters(dexFile.getName());
                zipFile.addFile(dexFile, params);
            }
        }
    }

    /**
     * 添加资源目录到 zip
     */
    private void addResourceDir(ZipFile zipFile, File resDir) throws IOException {
        if (resDir != null && resDir.exists() && resDir.isDirectory()) {
            addDirectoryToZip(zipFile, resDir, RES_DIR);
        }
    }

    /**
     * 添加 assets 目录到 zip
     */
    private void addAssetsDir(ZipFile zipFile, File assetsDir) throws IOException {
        if (assetsDir != null && assetsDir.exists() && assetsDir.isDirectory()) {
            addDirectoryToZip(zipFile, assetsDir, ASSETS_DIR);
        }
    }

    /**
     * 添加 resources.arsc 到 zip
     * 注意：resources.arsc 必须使用 STORE 模式（不压缩），否则 Android 无法加载
     */
    private void addResourcesArsc(ZipFile zipFile, File resourcesArsc) throws IOException {
        if (resourcesArsc != null && resourcesArsc.exists()) {
            // 读取文件内容
            byte[] content = java.nio.file.Files.readAllBytes(resourcesArsc.toPath());
            
            ZipParameters params = new ZipParameters();
            params.setFileNameInZip("resources.arsc");
            // 关键：resources.arsc 必须不压缩，否则 AssetManager.addAssetPath() 无法加载
            params.setCompressionMethod(CompressionMethod.STORE);
            
            // 使用流添加，确保不压缩
            zipFile.addStream(new java.io.ByteArrayInputStream(content), params);
        }
    }

    /**
     * 添加 bsdiff 文件到 zip
     */
    private void addBsdiffFiles(ZipFile zipFile, List<File> bsdiffFiles) throws IOException {
        for (File bsdiffFile : bsdiffFiles) {
            if (bsdiffFile != null && bsdiffFile.exists()) {
                ZipParameters params = createZipParameters(bsdiffFile.getName());
                zipFile.addFile(bsdiffFile, params);
            }
        }
    }

    /**
     * 递归添加目录到 zip
     */
    private void addDirectoryToZip(ZipFile zipFile, File sourceDir, String targetDirName) throws IOException {
        File[] files = sourceDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String entryPath = targetDirName + "/" + file.getName();
            if (file.isDirectory()) {
                // 递归添加子目录
                addDirectoryRecursive(zipFile, file, entryPath);
            } else {
                ZipParameters params = createZipParameters(entryPath);
                zipFile.addFile(file, params);
            }
        }
    }

    /**
     * 递归添加目录内容
     */
    private void addDirectoryRecursive(ZipFile zipFile, File dir, String basePath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String entryPath = basePath + "/" + file.getName();
            if (file.isDirectory()) {
                addDirectoryRecursive(zipFile, file, entryPath);
            } else {
                ZipParameters params = createZipParameters(entryPath);
                zipFile.addFile(file, params);
            }
        }
    }

    /**
     * 创建 zip 参数
     */
    private ZipParameters createZipParameters(String fileName) {
        ZipParameters params = new ZipParameters();
        params.setFileNameInZip(fileName);
        params.setCompressionMethod(CompressionMethod.DEFLATE);
        params.setCompressionLevel(CompressionLevel.NORMAL);
        return params;
    }

    /**
     * 更新 PatchInfo 中的文件信息
     */
    private void updatePatchInfoWithFileInfo(PatchInfo patchInfo, File patchFile) throws PatchPackException {
        try {
            patchInfo.setFileSize(patchFile.length());
            patchInfo.setMd5(calculateMd5(patchFile));
            patchInfo.setSha256(calculateSha256(patchFile));
        } catch (Exception e) {
            throw new PatchPackException("Failed to calculate file hash: " + e.getMessage(), e);
        }
    }

    /**
     * 计算文件 MD5
     */
    private String calculateMd5(File file) throws IOException, NoSuchAlgorithmException {
        return calculateHash(file, "MD5");
    }

    /**
     * 计算文件 SHA256
     */
    private String calculateSha256(File file) throws IOException, NoSuchAlgorithmException {
        return calculateHash(file, "SHA-256");
    }

    /**
     * 计算文件哈希
     */
    private String calculateHash(File file, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
