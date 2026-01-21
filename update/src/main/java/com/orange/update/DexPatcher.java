package com.orange.update;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.ChecksSdkIntAtLeast;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import dalvik.system.BaseDexClassLoader;
import dalvik.system.DexClassLoader;

/**
 * Dex 注入器，负责将补丁 dex 注入到 ClassLoader 的 dexElements 数组中。
 * 
 * 技术原理：
 * Android 的类加载采用双亲委派机制，通过反射修改 BaseDexClassLoader 的 
 * pathList.dexElements 数组，将补丁 dex 插入到最前面，这样加载类时会优先
 * 从补丁中查找，从而实现类的替换。
 * 
 * 兼容性：
 * - API 21-23: 标准 DexClassLoader 方案
 * - API 24-25: 需要处理混合编译模式
 * - API 26-28: 需要处理 InMemoryDexClassLoader
 * - API 29+: 需要处理非 SDK 接口限制
 */
public class DexPatcher {
    
    private static final String TAG = "DexPatcher";
    
    // 反射字段名
    private static final String FIELD_PATH_LIST = "pathList";
    private static final String FIELD_DEX_ELEMENTS = "dexElements";
    private static final String FIELD_NATIVE_LIBRARY_DIRECTORIES = "nativeLibraryDirectories";
    private static final String FIELD_NATIVE_LIBRARY_PATH_ELEMENTS = "nativeLibraryPathElements";
    
    /**
     * 将补丁 dex 注入到当前 ClassLoader
     * 
     * @param context 应用上下文
     * @param patchDexPath 补丁 dex 文件路径
     * @throws PatchException 如果注入失败
     */
    public static void injectPatchDex(Context context, String patchDexPath) throws PatchException {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (patchDexPath == null || patchDexPath.isEmpty()) {
            throw new IllegalArgumentException("Patch dex path cannot be null or empty");
        }
        
        File patchFile = new File(patchDexPath);
        if (!patchFile.exists()) {
            throw new PatchException(UpdateErrorCode.ERROR_FILE_NOT_FOUND, 
                    "Patch dex file not found: " + patchDexPath);
        }
        
        File optimizedDir = getOptimizedDir(context);
        injectPatchDex(context, patchDexPath, optimizedDir);
    }
    
    /**
     * 将补丁 dex 注入到当前 ClassLoader
     * 
     * @param context 应用上下文
     * @param patchDexPath 补丁 dex 文件路径
     * @param optimizedDir dex 优化输出目录
     * @throws PatchException 如果注入失败
     */
    public static void injectPatchDex(Context context, String patchDexPath, File optimizedDir) 
            throws PatchException {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        }
        if (patchDexPath == null || patchDexPath.isEmpty()) {
            throw new IllegalArgumentException("Patch dex path cannot be null or empty");
        }
        if (optimizedDir == null) {
            throw new IllegalArgumentException("Optimized directory cannot be null");
        }
        
        File patchFile = new File(patchDexPath);
        if (!patchFile.exists()) {
            throw new PatchException(UpdateErrorCode.ERROR_FILE_NOT_FOUND, 
                    "Patch dex file not found: " + patchDexPath);
        }
        
        // 确保优化目录存在
        if (!optimizedDir.exists() && !optimizedDir.mkdirs()) {
            throw new PatchException(UpdateErrorCode.ERROR_APPLY_FAILED, 
                    "Failed to create optimized directory: " + optimizedDir.getPath());
        }
        
        try {
            int sdkVersion = Build.VERSION.SDK_INT;
            Log.d(TAG, "Injecting patch dex, SDK version: " + sdkVersion);
            
            if (sdkVersion >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+)
                injectPatchForQ(context, patchDexPath, optimizedDir);
            } else if (sdkVersion >= Build.VERSION_CODES.N) {
                // Android 7.0+ (API 24+)
                injectPatchForN(context, patchDexPath, optimizedDir);
            } else {
                // Android 5.0-6.0 (API 21-23)
                injectPatchStandard(context, patchDexPath, optimizedDir);
            }
            
            Log.i(TAG, "Patch dex injected successfully: " + patchDexPath);
            
        } catch (PatchException e) {
            throw e;
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject patch dex", e);
            throw new PatchException(UpdateErrorCode.ERROR_APPLY_FAILED, 
                    "Failed to inject patch dex: " + e.getMessage(), e);
        }
    }

    
    /**
     * 标准注入方案 (Android 5.0-6.0, API 21-23)
     */
    private static void injectPatchStandard(Context context, String patchDexPath, File optimizedDir) 
            throws Exception {
        // 1. 获取当前应用的 ClassLoader (PathClassLoader)
        ClassLoader classLoader = context.getClassLoader();
        
        // 2. 获取 pathList 对象
        Object pathList = getPathList(classLoader);
        
        // 3. 获取原始 dexElements 数组
        Object[] oldElements = getDexElements(pathList);
        
        // 4. 使用 DexClassLoader 加载补丁 dex
        DexClassLoader patchClassLoader = new DexClassLoader(
                patchDexPath,
                optimizedDir.getAbsolutePath(),
                null,
                classLoader.getParent()
        );
        
        // 5. 获取补丁的 dexElements
        Object patchPathList = getPathList(patchClassLoader);
        Object[] patchElements = getDexElements(patchPathList);
        
        // 6. 合并数组：补丁 dex 在前，原始 dex 在后
        Object[] newElements = combineArray(patchElements, oldElements);
        
        // 7. 将合并后的数组设置回 pathList
        setDexElements(pathList, newElements);
    }
    
    /**
     * Android 7.0+ 注入方案 (API 24-25)
     * 需要处理混合编译模式 (ART + JIT)
     */
    private static void injectPatchForN(Context context, String patchDexPath, File optimizedDir) 
            throws Exception {
        // Android 7.0+ 的处理方式与标准方案类似，但需要注意：
        // 1. 混合编译模式下，部分代码可能已经被 AOT 编译
        // 2. 需要确保补丁 dex 能够正确触发 JIT 编译
        
        ClassLoader classLoader = context.getClassLoader();
        Object pathList = getPathList(classLoader);
        Object[] oldElements = getDexElements(pathList);
        
        // 使用 DexClassLoader 加载补丁
        DexClassLoader patchClassLoader = new DexClassLoader(
                patchDexPath,
                optimizedDir.getAbsolutePath(),
                null,
                classLoader.getParent()
        );
        
        Object patchPathList = getPathList(patchClassLoader);
        Object[] patchElements = getDexElements(patchPathList);
        
        // 合并数组
        Object[] newElements = combineArray(patchElements, oldElements);
        setDexElements(pathList, newElements);
        
        Log.d(TAG, "Patch injected for Android N+");
    }
    
    /**
     * Android 10+ 注入方案 (API 29+)
     * 需要处理非 SDK 接口限制和可写目录限制
     */
    private static void injectPatchForQ(Context context, String patchDexPath, File optimizedDir) 
            throws Exception {
        // Android 10+ 不允许从可写目录加载 DEX 文件
        // 需要将补丁文件复制到代码缓存目录（只读）
        
        File patchFile = new File(patchDexPath);
        File codeCacheDir = context.getCodeCacheDir();
        File readOnlyPatchFile = new File(codeCacheDir, "patch_" + System.currentTimeMillis() + ".zip");
        
        // 复制补丁文件到代码缓存目录
        try {
            copyFile(patchFile, readOnlyPatchFile);
            // 设置为只读
            readOnlyPatchFile.setReadOnly();
            Log.d(TAG, "Copied patch to code cache dir: " + readOnlyPatchFile.getPath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy patch file", e);
            throw new Exception("Failed to copy patch to code cache directory", e);
        }
        
        ClassLoader classLoader = context.getClassLoader();
        Object pathList = getPathList(classLoader);
        Object[] oldElements = getDexElements(pathList);
        
        // 使用只读的补丁文件路径
        DexClassLoader patchClassLoader = new DexClassLoader(
                readOnlyPatchFile.getAbsolutePath(),
                optimizedDir.getAbsolutePath(),
                null,
                classLoader.getParent()
        );
        
        Object patchPathList = getPathList(patchClassLoader);
        Object[] patchElements = getDexElements(patchPathList);
        
        // 合并数组
        Object[] newElements = combineArray(patchElements, oldElements);
        setDexElements(pathList, newElements);
        
        Log.d(TAG, "Patch injected for Android Q+ from read-only location");
    }
    
    /**
     * 复制文件
     */
    private static void copyFile(File source, File dest) throws Exception {
        java.io.FileInputStream fis = null;
        java.io.FileOutputStream fos = null;
        try {
            fis = new java.io.FileInputStream(source);
            fos = new java.io.FileOutputStream(dest);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                fos.write(buffer, 0, length);
            }
            fos.flush();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    
    // ==================== 反射工具方法 ====================
    
    /**
     * 获取 ClassLoader 的 pathList 对象
     * 
     * @param classLoader ClassLoader 实例
     * @return pathList 对象
     * @throws Exception 如果反射失败
     */
    private static Object getPathList(ClassLoader classLoader) throws Exception {
        if (!(classLoader instanceof BaseDexClassLoader)) {
            throw new IllegalArgumentException("ClassLoader is not a BaseDexClassLoader");
        }
        
        Field pathListField = BaseDexClassLoader.class.getDeclaredField(FIELD_PATH_LIST);
        pathListField.setAccessible(true);
        return pathListField.get(classLoader);
    }
    
    /**
     * 获取 pathList 中的 dexElements 数组
     * 
     * @param pathList pathList 对象
     * @return dexElements 数组
     * @throws Exception 如果反射失败
     */
    private static Object[] getDexElements(Object pathList) throws Exception {
        Field dexElementsField = pathList.getClass().getDeclaredField(FIELD_DEX_ELEMENTS);
        dexElementsField.setAccessible(true);
        return (Object[]) dexElementsField.get(pathList);
    }
    
    /**
     * 设置 pathList 中的 dexElements 数组
     * 
     * @param pathList pathList 对象
     * @param dexElements 新的 dexElements 数组
     * @throws Exception 如果反射失败
     */
    private static void setDexElements(Object pathList, Object[] dexElements) throws Exception {
        Field dexElementsField = pathList.getClass().getDeclaredField(FIELD_DEX_ELEMENTS);
        dexElementsField.setAccessible(true);
        dexElementsField.set(pathList, dexElements);
    }
    
    /**
     * 合并两个数组，第一个数组的元素放在前面
     * 补丁 dex 需要放在前面，这样类加载时会优先从补丁中查找
     * 
     * @param first 第一个数组（补丁 dex）
     * @param second 第二个数组（原始 dex）
     * @return 合并后的数组
     */
    public static Object[] combineArray(Object[] first, Object[] second) {
        if (first == null || first.length == 0) {
            return second;
        }
        if (second == null || second.length == 0) {
            return first;
        }
        
        // 创建新数组，类型与原数组相同
        Class<?> componentType = first.getClass().getComponentType();
        Object[] combined = (Object[]) Array.newInstance(
                componentType,
                first.length + second.length
        );
        
        // 复制第一个数组（补丁）到前面
        System.arraycopy(first, 0, combined, 0, first.length);
        // 复制第二个数组（原始）到后面
        System.arraycopy(second, 0, combined, first.length, second.length);
        
        return combined;
    }
    
    /**
     * 获取 dex 优化输出目录
     * 
     * @param context 应用上下文
     * @return 优化目录
     */
    public static File getOptimizedDir(Context context) {
        File optimizedDir = new File(context.getFilesDir(), "update/odex");
        if (!optimizedDir.exists()) {
            optimizedDir.mkdirs();
        }
        return optimizedDir;
    }

    
    // ==================== 版本兼容性检查 ====================
    
    /**
     * 检查当前 Android 版本是否支持热更新
     * 
     * @return 是否支持
     */
    public static boolean isSupported() {
        int sdkVersion = Build.VERSION.SDK_INT;
        // 支持 Android 5.0 (API 21) 到 Android 14 (API 34)
        return sdkVersion >= Build.VERSION_CODES.LOLLIPOP && sdkVersion <= 34;
    }
    
    /**
     * 获取当前 Android 版本的兼容性级别
     * 
     * @return 兼容性级别描述
     */
    public static String getCompatibilityLevel() {
        int sdkVersion = Build.VERSION.SDK_INT;
        
        if (sdkVersion < Build.VERSION_CODES.LOLLIPOP) {
            return "UNSUPPORTED: Android version too low (< 5.0)";
        } else if (sdkVersion <= Build.VERSION_CODES.M) {
            return "FULL: Standard DexClassLoader (Android 5.0-6.0)";
        } else if (sdkVersion <= Build.VERSION_CODES.N_MR1) {
            return "FULL: Mixed compilation mode (Android 7.0-7.1)";
        } else if (sdkVersion <= Build.VERSION_CODES.P) {
            return "FULL: InMemoryDexClassLoader support (Android 8.0-9.0)";
        } else if (sdkVersion <= 34) {
            return "LIMITED: Non-SDK interface restrictions (Android 10+)";
        } else {
            return "UNKNOWN: Untested Android version (> 14)";
        }
    }
    
    /**
     * 检查是否需要特殊处理
     * 
     * @return 是否需要特殊处理
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    public static boolean requiresSpecialHandling() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
    
    // ==================== 调试工具方法 ====================
    
    /**
     * 打印当前 ClassLoader 的 dexElements 信息（调试用）
     * 
     * @param context 应用上下文
     */
    public static void printDexElements(Context context) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            Object pathList = getPathList(classLoader);
            Object[] dexElements = getDexElements(pathList);
            
            Log.d(TAG, "=== DexElements Info ===");
            Log.d(TAG, "Total elements: " + dexElements.length);
            
            for (int i = 0; i < dexElements.length; i++) {
                Object element = dexElements[i];
                Log.d(TAG, "Element[" + i + "]: " + element.toString());
            }
            
            Log.d(TAG, "========================");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to print dex elements", e);
        }
    }
    
    /**
     * 检查补丁是否已经注入
     * 
     * @param context 应用上下文
     * @param patchDexPath 补丁 dex 路径
     * @return 是否已注入
     */
    public static boolean isPatchInjected(Context context, String patchDexPath) {
        try {
            ClassLoader classLoader = context.getClassLoader();
            Object pathList = getPathList(classLoader);
            Object[] dexElements = getDexElements(pathList);
            
            File patchFile = new File(patchDexPath);
            String patchName = patchFile.getName();
            
            for (Object element : dexElements) {
                String elementStr = element.toString();
                if (elementStr.contains(patchName)) {
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to check patch injection status", e);
            return false;
        }
    }
    
    /**
     * 补丁异常类
     */
    public static class PatchException extends Exception {
        private final int errorCode;
        
        public PatchException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public PatchException(int errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
    }
}
