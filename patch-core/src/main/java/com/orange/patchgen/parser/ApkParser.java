package com.orange.patchgen.parser;

import com.orange.patchgen.callback.GeneratorErrorCode;
import com.orange.patchgen.model.ApkInfo;
import com.orange.patchgen.model.AssetInfo;
import com.orange.patchgen.model.DexInfo;
import com.orange.patchgen.model.ResourceInfo;
import net.dongliu.apk.parser.ApkFile;
import net.dongliu.apk.parser.bean.ApkMeta;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.io.FileUtils;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * APK 解析器
 * 
 * 负责解析 APK 文件，提取版本信息、dex 文件列表和资源文件列表。
 * 使用 apk-parser 库来可靠地解析 AndroidManifest.xml。
 * 
 * Requirements: 1.1, 2.1, 2.6
 */
public class ApkParser {

    private static final Pattern DEX_PATTERN = Pattern.compile("classes\\d*\\.dex");
    private static final String RES_DIR = "res/";
    private static final String ASSETS_DIR = "assets/";

    /**
     * 解析 APK 文件
     * 
     * @param apkFile APK 文件
     * @return APK 信息
     * @throws ParseException 解析失败时抛出
     */
    public ApkInfo parse(File apkFile) throws ParseException {
        validateApkFile(apkFile);

        ApkInfo apkInfo = new ApkInfo();
        
        try {
            // 使用 apk-parser 库解析 AndroidManifest.xml 获取版本信息
            try (ApkFile apk = new ApkFile(apkFile)) {
                ApkMeta apkMeta = apk.getApkMeta();
                apkInfo.setPackageName(apkMeta.getPackageName());
                apkInfo.setVersionCode((int) apkMeta.getVersionCode().longValue());
                apkInfo.setVersionName(apkMeta.getVersionName());
            }
            
            try (ZipFile zipFile = new ZipFile(apkFile)) {
                // 提取 dex 文件列表
                List<DexInfo> dexFiles = extractDexList(zipFile, apkFile);
                apkInfo.setDexFiles(dexFiles);

                // 提取资源文件列表
                List<ResourceInfo> resources = extractResourceList(zipFile);
                apkInfo.setResources(resources);

                // 提取 assets 文件列表
                List<AssetInfo> assets = extractAssetList(zipFile);
                apkInfo.setAssets(assets);
            }

        } catch (IOException e) {
            throw new ParseException("Failed to parse APK: " + e.getMessage(),
                    GeneratorErrorCode.ERROR_APK_PARSE_FAILED, e);
        }

        return apkInfo;
    }

    /**
     * 解压 APK 到指定目录
     * 
     * @param apkFile APK 文件
     * @param outputDir 输出目录
     * @throws IOException 解压失败时抛出
     */
    public void extract(File apkFile, File outputDir) throws IOException {
        if (!apkFile.exists()) {
            throw new FileNotFoundException("APK file not found: " + apkFile.getAbsolutePath());
        }

        if (outputDir.exists()) {
            FileUtils.deleteDirectory(outputDir);
        }
        outputDir.mkdirs();

        try (ZipFile zipFile = new ZipFile(apkFile)) {
            zipFile.extractAll(outputDir.getAbsolutePath());
        }
    }

    /**
     * 获取 APK 版本信息
     * 
     * @param apkFile APK 文件
     * @return 版本信息
     */
    public VersionInfo getVersionInfo(File apkFile) {
        try {
            validateApkFile(apkFile);
            try (ApkFile apk = new ApkFile(apkFile)) {
                ApkMeta apkMeta = apk.getApkMeta();
                VersionInfo versionInfo = new VersionInfo();
                versionInfo.setPackageName(apkMeta.getPackageName());
                versionInfo.setVersionCode((int) apkMeta.getVersionCode().longValue());
                versionInfo.setVersionName(apkMeta.getVersionName());
                return versionInfo;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 APK 文件
     */
    private void validateApkFile(File apkFile) throws ParseException {
        if (apkFile == null) {
            throw new ParseException("APK file is null",
                    GeneratorErrorCode.ERROR_INVALID_APK);
        }
        if (!apkFile.exists()) {
            throw new ParseException("APK file not found: " + apkFile.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!apkFile.isFile()) {
            throw new ParseException("Not a file: " + apkFile.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_INVALID_APK);
        }
        if (!apkFile.canRead()) {
            throw new ParseException("Cannot read APK file: " + apkFile.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_READ_FAILED);
        }
        
        // 验证是否为有效的 ZIP 文件
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            if (!zipFile.isValidZipFile()) {
                throw new ParseException("Invalid APK file (not a valid ZIP): " + apkFile.getAbsolutePath(),
                        GeneratorErrorCode.ERROR_INVALID_APK);
            }
        } catch (IOException e) {
            throw new ParseException("Failed to validate APK file: " + e.getMessage(),
                    GeneratorErrorCode.ERROR_INVALID_APK, e);
        }
    }


    /**
     * 提取 dex 文件列表
     */
    private List<DexInfo> extractDexList(ZipFile zipFile, File apkFile) throws ParseException {
        List<DexInfo> dexFiles = new ArrayList<>();
        
        try {
            List<FileHeader> headers = zipFile.getFileHeaders();
            
            for (FileHeader header : headers) {
                String fileName = header.getFileName();
                if (DEX_PATTERN.matcher(fileName).matches()) {
                    DexInfo dexInfo = new DexInfo();
                    dexInfo.setFileName(fileName);
                    dexInfo.setSize(header.getUncompressedSize());
                    
                    // 计算 MD5
                    try (InputStream is = zipFile.getInputStream(header)) {
                        dexInfo.setMd5(calculateMd5(is));
                    }
                    
                    // 提取类名列表
                    List<String> classNames = extractClassNames(apkFile, fileName);
                    dexInfo.setClassNames(classNames);
                    
                    dexFiles.add(dexInfo);
                }
            }
            
            // 按文件名排序 (classes.dex, classes2.dex, classes3.dex, ...)
            dexFiles.sort((a, b) -> {
                String nameA = a.getFileName();
                String nameB = b.getFileName();
                if (nameA.equals("classes.dex")) return -1;
                if (nameB.equals("classes.dex")) return 1;
                return nameA.compareTo(nameB);
            });
            
        } catch (IOException e) {
            throw new ParseException("Failed to extract dex files: " + e.getMessage(),
                    GeneratorErrorCode.ERROR_DEX_PARSE_FAILED, e);
        }
        
        return dexFiles;
    }

    /**
     * 使用 dexlib2 提取 dex 文件中的类名
     */
    private List<String> extractClassNames(File apkFile, String dexFileName) {
        List<String> classNames = new ArrayList<>();
        
        try {
            // 先解压 dex 文件到临时目录
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "apk_parser_" + System.currentTimeMillis());
            tempDir.mkdirs();
            
            try (ZipFile zipFile = new ZipFile(apkFile)) {
                FileHeader dexHeader = zipFile.getFileHeader(dexFileName);
                if (dexHeader != null) {
                    zipFile.extractFile(dexHeader, tempDir.getAbsolutePath());
                    File dexFile = new File(tempDir, dexFileName);
                    
                    // 使用 dexlib2 解析 dex 文件。必须 forDexVersion(38)：getDefault()
                    // 是 API 15 指令表，误解码 API 26+ 指令导致 truncated（见 DexDiffer）。
                    org.jf.dexlib2.iface.DexFile parsedDex = DexFileFactory.loadDexFile(dexFile, Opcodes.forDexVersion(38));
                    
                    for (ClassDef classDef : parsedDex.getClasses()) {
                        String className = classDef.getType();
                        // 转换格式: Lcom/example/Class; -> com.example.Class
                        if (className.startsWith("L") && className.endsWith(";")) {
                            className = className.substring(1, className.length() - 1).replace('/', '.');
                        }
                        classNames.add(className);
                    }
                    
                    // 清理临时文件
                    dexFile.delete();
                }
            } finally {
                // 清理临时目录
                FileUtils.deleteDirectory(tempDir);
            }
        } catch (Exception e) {
            // 如果 dexlib2 解析失败，返回空列表
        }
        
        return classNames;
    }

    /**
     * 提取资源文件列表
     */
    private List<ResourceInfo> extractResourceList(ZipFile zipFile) throws IOException {
        List<ResourceInfo> resources = new ArrayList<>();
        
        List<FileHeader> headers = zipFile.getFileHeaders();
        
        for (FileHeader header : headers) {
            String fileName = header.getFileName();
            
            // 只处理 res/ 目录下的文件
            if (fileName.startsWith(RES_DIR) && !header.isDirectory()) {
                ResourceInfo resourceInfo = new ResourceInfo();
                resourceInfo.setRelativePath(fileName);
                resourceInfo.setSize(header.getUncompressedSize());
                resourceInfo.setType(ResourceInfo.ResourceType.RES);
                
                // 计算 MD5
                try (InputStream is = zipFile.getInputStream(header)) {
                    resourceInfo.setMd5(calculateMd5(is));
                }
                
                resources.add(resourceInfo);
            }
        }
        
        // 添加 resources.arsc
        FileHeader arscHeader = zipFile.getFileHeader("resources.arsc");
        if (arscHeader != null) {
            ResourceInfo arscInfo = new ResourceInfo();
            arscInfo.setRelativePath("resources.arsc");
            arscInfo.setSize(arscHeader.getUncompressedSize());
            arscInfo.setType(ResourceInfo.ResourceType.OTHER);
            
            try (InputStream is = zipFile.getInputStream(arscHeader)) {
                arscInfo.setMd5(calculateMd5(is));
            }
            
            resources.add(arscInfo);
        }
        
        return resources;
    }

    /**
     * 提取 assets 文件列表
     */
    private List<AssetInfo> extractAssetList(ZipFile zipFile) throws IOException {
        List<AssetInfo> assets = new ArrayList<>();
        
        List<FileHeader> headers = zipFile.getFileHeaders();
        
        for (FileHeader header : headers) {
            String fileName = header.getFileName();
            
            // 只处理 assets/ 目录下的文件
            if (fileName.startsWith(ASSETS_DIR) && !header.isDirectory()) {
                AssetInfo assetInfo = new AssetInfo();
                // 存储相对于 assets/ 的路径
                assetInfo.setRelativePath(fileName.substring(ASSETS_DIR.length()));
                assetInfo.setSize(header.getUncompressedSize());
                
                // 计算 MD5
                try (InputStream is = zipFile.getInputStream(header)) {
                    assetInfo.setMd5(calculateMd5(is));
                }
                
                assets.add(assetInfo);
            }
        }
        
        return assets;
    }

    /**
     * 计算输入流的 MD5 哈希值
     */
    private String calculateMd5(InputStream is) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("MD5 algorithm not available", e);
        }
    }
}
