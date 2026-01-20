package com.orange.patchgen.signer;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * ZipSigner 库包装类
 *
 * 提供对 kellinwood ZipSigner 库的封装，用于支持 JKS keystore 的原生加载和签名。
 *
 * 功能：
 * - 使用 JKS 对 ZIP 文件进行签名（原生支持）
 * - 作为 Native JKS 解析方案的备选方案
 */
public class ZipSignerHelper {

    private static final String TAG = "ZipSignerHelper";

    /**
     * 使用 ZipSigner 对 ZIP 文件进行签名（支持 JKS）
     *
     * @param inputFile 输入 ZIP 文件
     * @param outputFile 输出签名后的 ZIP 文件
     * @param keystoreFile keystore 文件（支持 JKS/BKS）
     * @param keystorePassword keystore 密码
     * @param keyAlias 密钥别名
     * @param keyPassword 密钥密码
     * @param zipSignerJarPath ZipSigner JAR 文件路径
     * @return 签名是否成功
     */
    public static boolean signZipWithJks(File inputFile, File outputFile,
                                         File keystoreFile, String keystorePassword,
                                         String keyAlias, String keyPassword,
                                         String zipSignerJarPath) {
        try {
            System.out.println("[ZipSignerHelper] 使用 ZipSigner 签名: " + inputFile.getName());
            System.out.println("[ZipSignerHelper]   Keystore: " + keystoreFile.getName());
            System.out.println("[ZipSignerHelper]   别名: " + keyAlias);
            System.out.println("[ZipSignerHelper]   ZipSigner JAR: " + zipSignerJarPath);

            // 动态加载 ZipSigner JAR
            File zipSignerJar = new File(zipSignerJarPath);
            if (!zipSignerJar.exists()) {
                System.err.println("[ZipSignerHelper] ✗ ZipSigner JAR 不存在: " + zipSignerJarPath);
                return false;
            }

            // 创建类加载器
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{zipSignerJar.toURI().toURL()},
                ZipSignerHelper.class.getClassLoader()
            );

            // 加载 ZipSigner 类
            Class<?> zipSignerClass = classLoader.loadClass("kellinwood.security.zipsigner.ZipSigner");
            Object zipSigner = zipSignerClass.newInstance();

            // 设置为自定义密钥模式
            Method setKeymodeMethod = zipSignerClass.getMethod("setKeymode", String.class);
            setKeymodeMethod.invoke(zipSigner, "custom");

            // 转换为 URL
            URL keystoreUrl = keystoreFile.toURI().toURL();

            // 执行签名
            // signZip(URL keystoreUrl, String keystoreType, char[] keystorePass,
            //         String keyAlias, char[] keyPass, String signatureAlgorithm,
            //         String inputFilename, String outputFilename)
            Method signZipMethod = zipSignerClass.getMethod("signZip",
                URL.class, String.class, char[].class,
                String.class, char[].class, String.class,
                String.class, String.class);

            // 强制使用 BKS 模式，让 ZipSigner 使用 BouncyCastle 加载 JKS
            // 这样可以绕过 Android 不支持 JKS 的限制
            signZipMethod.invoke(zipSigner,
                keystoreUrl,
                "BKS",  // 强制使用 BKS 模式（BouncyCastle）来加载 JKS
                keystorePassword.toCharArray(),
                keyAlias,
                keyPassword.toCharArray(),
                "SHA1withRSA",  // 签名算法
                inputFile.getAbsolutePath(),
                outputFile.getAbsolutePath()
            );

            System.out.println("[ZipSignerHelper] ✓ ZipSigner 签名成功");
            System.out.println("[ZipSignerHelper]   输出文件: " + outputFile.getName());
            System.out.println("[ZipSignerHelper]   输出大小: " + formatSize(outputFile.length()));

            return true;

        } catch (Exception e) {
            System.err.println("[ZipSignerHelper] ZipSigner 签名失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查 ZipSigner 库是否可用
     *
     * @param zipSignerJarPath ZipSigner JAR 文件路径
     * @return 是否可用
     */
    public static boolean isAvailable(String zipSignerJarPath) {
        try {
            File zipSignerJar = new File(zipSignerJarPath);
            if (!zipSignerJar.exists()) {
                System.out.println("[ZipSignerHelper] ✗ ZipSigner JAR 不存在: " + zipSignerJarPath);
                return false;
            }

            // 尝试加载 ZipSigner 类
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{zipSignerJar.toURI().toURL()},
                ZipSignerHelper.class.getClassLoader()
            );
            classLoader.loadClass("kellinwood.security.zipsigner.ZipSigner");
            System.out.println("[ZipSignerHelper] ✓ ZipSigner 库可用");
            return true;
        } catch (Exception e) {
            System.out.println("[ZipSignerHelper] ✗ ZipSigner 库不可用: " + e.getMessage());
            return false;
        }
    }

    /**
     * 格式化文件大小
     */
    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }
}
