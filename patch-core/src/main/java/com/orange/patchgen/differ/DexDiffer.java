package com.orange.patchgen.differ;

import com.orange.patchgen.callback.GeneratorErrorCode;
import com.orange.patchgen.model.ApkInfo;
import com.orange.patchgen.model.DexInfo;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Field;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.Instruction;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Dex 差异比较器
 * 
 * 负责比较两个 dex 文件的差异，识别修改、新增、删除的类。
 * 使用 dexlib2 库解析 dex 文件。
 * 
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6
 */
public class DexDiffer {

    /**
     * 比较两个 Dex 文件
     * 
     * @param baseDex 基准 dex 文件
     * @param newDex 新版本 dex 文件
     * @return 差异比较结果
     * @throws DexDiffException 比较失败时抛出
     */
    public DexDiffResult compare(File baseDex, File newDex) throws DexDiffException {
        validateDexFile(baseDex, "base");
        validateDexFile(newDex, "new");

        String dexName = newDex.getName();
        DexDiffResult result = new DexDiffResult(dexName);

        try {
            // 解析两个 dex 文件
            Map<String, String> baseClassHashes = parseDexClasses(baseDex);
            Map<String, String> newClassHashes = parseDexClasses(newDex);

            // 比较类差异
            compareClasses(baseClassHashes, newClassHashes, result);

        } catch (IOException e) {
            throw new DexDiffException("Failed to compare dex files: " + e.getMessage(),
                    GeneratorErrorCode.ERROR_DEX_PARSE_FAILED, e);
        }

        return result;
    }

    /**
     * 比较两个 APK 的所有 Dex 文件
     * 
     * @param baseApk 基准 APK 信息
     * @param newApk 新版本 APK 信息
     * @return 所有 dex 的差异比较结果列表
     * @throws DexDiffException 比较失败时抛出
     */
    public List<DexDiffResult> compareAll(ApkInfo baseApk, ApkInfo newApk) throws DexDiffException {
        if (baseApk == null || newApk == null) {
            throw new DexDiffException("APK info cannot be null",
                    GeneratorErrorCode.ERROR_COMPARE_FAILED);
        }

        List<DexDiffResult> results = new ArrayList<>();
        
        // 获取所有 dex 文件名
        Set<String> allDexNames = new HashSet<>();
        Map<String, DexInfo> baseDexMap = new HashMap<>();
        Map<String, DexInfo> newDexMap = new HashMap<>();

        if (baseApk.getDexFiles() != null) {
            for (DexInfo dex : baseApk.getDexFiles()) {
                baseDexMap.put(dex.getFileName(), dex);
                allDexNames.add(dex.getFileName());
            }
        }

        if (newApk.getDexFiles() != null) {
            for (DexInfo dex : newApk.getDexFiles()) {
                newDexMap.put(dex.getFileName(), dex);
                allDexNames.add(dex.getFileName());
            }
        }

        // 比较每个 dex 文件
        for (String dexName : allDexNames) {
            DexInfo baseDex = baseDexMap.get(dexName);
            DexInfo newDex = newDexMap.get(dexName);

            DexDiffResult result = new DexDiffResult(dexName);

            if (baseDex == null && newDex != null) {
                // 新增的 dex 文件，所有类都是新增的
                if (newDex.getClassNames() != null) {
                    for (String className : newDex.getClassNames()) {
                        result.addAddedClass(className);
                    }
                }
            } else if (baseDex != null && newDex == null) {
                // 删除的 dex 文件，所有类都是删除的
                if (baseDex.getClassNames() != null) {
                    for (String className : baseDex.getClassNames()) {
                        result.addDeletedClass(className);
                    }
                }
            } else if (baseDex != null && newDex != null) {
                // 两个 dex 都存在，需要详细比较
                // 首先检查 MD5，如果相同则无需详细比较
                if (baseDex.getMd5() != null && baseDex.getMd5().equals(newDex.getMd5())) {
                    // MD5 相同，无变化
                    continue;
                }
                
                // MD5 不同，需要详细比较类
                compareClassLists(baseDex.getClassNames(), newDex.getClassNames(), result);
            }

            if (result.hasChanges()) {
                results.add(result);
            }
        }

        return results;
    }


    /**
     * 生成补丁 Dex（包含修改和新增的类）
     * 
     * @param diff 差异比较结果
     * @param newDexFile 新版本 dex 文件
     * @param outputDir 输出目录
     * @return 生成的补丁 dex 文件，如果没有需要打包的类则返回 null
     * @throws DexDiffException 生成失败时抛出
     */
    public File generatePatchDex(DexDiffResult diff, File newDexFile, File outputDir) throws DexDiffException {
        if (diff == null || !diff.hasChanges()) {
            return null;
        }

        // 收集需要包含在补丁中的类（修改的 + 新增的）
        Set<String> classesToInclude = new HashSet<>();
        if (diff.getModifiedClasses() != null) {
            classesToInclude.addAll(diff.getModifiedClasses());
        }
        if (diff.getAddedClasses() != null) {
            classesToInclude.addAll(diff.getAddedClasses());
        }

        if (classesToInclude.isEmpty()) {
            return null;
        }

        validateDexFile(newDexFile, "new");

        try {
            // 解析新版本 dex 文件
            DexFile newDex = DexFileFactory.loadDexFile(newDexFile, Opcodes.getDefault());

            // 扩展类列表，包含内部类和 Lambda 类
            Set<String> expandedClasses = expandClassesWithInnerAndLambda(classesToInclude, newDex);

            // 筛选需要的类
            List<ClassDef> patchClasses = new ArrayList<>();
            for (ClassDef classDef : newDex.getClasses()) {
                String className = convertDexTypeToClassName(classDef.getType());
                if (expandedClasses.contains(className)) {
                    patchClasses.add(classDef);
                }
            }

            if (patchClasses.isEmpty()) {
                return null;
            }

            // 确保输出目录存在
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            // 生成补丁 dex 文件
            File patchDexFile = new File(outputDir, diff.getDexName());
            
            // 使用 dexlib2 写入新的 dex 文件
            writePatchDex(patchClasses, patchDexFile, newDex.getOpcodes());

            return patchDexFile;

        } catch (IOException e) {
            throw new DexDiffException("Failed to generate patch dex: " + e.getMessage(),
                    GeneratorErrorCode.ERROR_DEX_PARSE_FAILED, e);
        }
    }
    
    /**
     * 扩展类列表，包含内部类、匿名类和 Lambda 类
     * 
     * 当一个类被修改时，它的内部类、匿名类和 Lambda 类也需要包含在补丁中，
     * 因为它们可能被主类引用，且它们的字节码可能也发生了变化。
     * 
     * @param baseClasses 基础类列表
     * @param dexFile DEX 文件
     * @return 扩展后的类列表
     */
    private Set<String> expandClassesWithInnerAndLambda(Set<String> baseClasses, DexFile dexFile) {
        Set<String> expandedClasses = new HashSet<>(baseClasses);
        
        // 收集所有 DEX 中的类名
        Set<String> allClassNames = new HashSet<>();
        for (ClassDef classDef : dexFile.getClasses()) {
            allClassNames.add(convertDexTypeToClassName(classDef.getType()));
        }
        
        // 对于每个基础类，查找其内部类、匿名类和 Lambda 类
        for (String baseClass : baseClasses) {
            // 内部类和匿名类的命名模式: OuterClass$InnerClass, OuterClass$1, OuterClass$2
            // Lambda 类的命名模式: OuterClass$$ExternalSyntheticLambda0
            String prefix = baseClass + "$";
            String lambdaPrefix = baseClass + "$$";
            
            for (String className : allClassNames) {
                if (className.startsWith(prefix) || className.startsWith(lambdaPrefix)) {
                    expandedClasses.add(className);
                }
            }
        }
        
        return expandedClasses;
    }

    /**
     * 验证 dex 文件
     */
    private void validateDexFile(File dexFile, String name) throws DexDiffException {
        if (dexFile == null) {
            throw new DexDiffException(name + " dex file is null",
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!dexFile.exists()) {
            throw new DexDiffException(name + " dex file not found: " + dexFile.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_NOT_FOUND);
        }
        if (!dexFile.isFile()) {
            throw new DexDiffException(name + " dex is not a file: " + dexFile.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_DEX_PARSE_FAILED);
        }
        if (!dexFile.canRead()) {
            throw new DexDiffException("Cannot read " + name + " dex file: " + dexFile.getAbsolutePath(),
                    GeneratorErrorCode.ERROR_FILE_READ_FAILED);
        }
    }

    /**
     * 解析 dex 文件，提取所有类及其签名哈希
     * 
     * @param dexFile dex 文件
     * @return 类名到签名哈希的映射
     */
    private Map<String, String> parseDexClasses(File dexFile) throws IOException {
        Map<String, String> classHashes = new HashMap<>();

        DexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());

        for (ClassDef classDef : dex.getClasses()) {
            String className = convertDexTypeToClassName(classDef.getType());
            String classHash = calculateClassHash(classDef);
            classHashes.put(className, classHash);
        }

        return classHashes;
    }

    /**
     * 计算类的签名哈希
     * 
     * 基于类的完整定义（字段、方法、方法实现）计算哈希值，
     * 用于判断类是否被修改。
     * 
     * @param classDef 类定义
     * @return 类签名哈希（MD5）
     */
    String calculateClassHash(ClassDef classDef) {
        StringBuilder sb = new StringBuilder();

        // 类基本信息
        sb.append(classDef.getType());
        sb.append("|");
        sb.append(classDef.getAccessFlags());
        sb.append("|");
        if (classDef.getSuperclass() != null) {
            sb.append(classDef.getSuperclass());
        }
        sb.append("|");

        // 接口
        List<String> interfaces = new ArrayList<>();
        for (String iface : classDef.getInterfaces()) {
            interfaces.add(iface);
        }
        Collections.sort(interfaces);
        sb.append(String.join(",", interfaces));
        sb.append("|");

        // 字段（按名称排序）
        List<String> fieldSignatures = new ArrayList<>();
        for (Field field : classDef.getFields()) {
            fieldSignatures.add(getFieldSignature(field));
        }
        Collections.sort(fieldSignatures);
        sb.append(String.join(",", fieldSignatures));
        sb.append("|");

        // 方法（按名称排序）
        List<String> methodSignatures = new ArrayList<>();
        for (Method method : classDef.getMethods()) {
            methodSignatures.add(getMethodSignature(method));
        }
        Collections.sort(methodSignatures);
        sb.append(String.join(",", methodSignatures));

        return md5(sb.toString());
    }


    /**
     * 获取字段签名
     */
    private String getFieldSignature(Field field) {
        return field.getName() + ":" + field.getType() + ":" + field.getAccessFlags();
    }

    /**
     * 获取方法签名（包含方法实现的哈希）
     */
    private String getMethodSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getName());
        sb.append("(");
        
        // 参数类型
        List<String> params = new ArrayList<>();
        for (CharSequence param : method.getParameterTypes()) {
            params.add(param.toString());
        }
        sb.append(String.join(",", params));
        sb.append(")");
        sb.append(method.getReturnType());
        sb.append(":");
        sb.append(method.getAccessFlags());

        // 方法实现哈希
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            sb.append(":");
            sb.append(getImplementationHash(impl));
        }

        return sb.toString();
    }

    /**
     * 获取方法实现的哈希
     * 
     * 包含指令序列和字符串常量，确保字符串修改也能被检测到
     */
    private String getImplementationHash(MethodImplementation impl) {
        StringBuilder sb = new StringBuilder();
        sb.append(impl.getRegisterCount());
        sb.append("|");

        // 指令序列（包含字符串常量）
        for (Instruction instruction : impl.getInstructions()) {
            sb.append(instruction.getOpcode().name);
            
            // 提取字符串常量
            // const-string 和 const-string/jumbo 指令包含字符串引用
            if (instruction instanceof org.jf.dexlib2.iface.instruction.ReferenceInstruction) {
                org.jf.dexlib2.iface.instruction.ReferenceInstruction refInst = 
                    (org.jf.dexlib2.iface.instruction.ReferenceInstruction) instruction;
                org.jf.dexlib2.iface.reference.Reference ref = refInst.getReference();
                
                // 如果是字符串引用，包含字符串内容
                if (ref instanceof org.jf.dexlib2.iface.reference.StringReference) {
                    org.jf.dexlib2.iface.reference.StringReference strRef = 
                        (org.jf.dexlib2.iface.reference.StringReference) ref;
                    sb.append("[STR:");
                    sb.append(strRef.getString());
                    sb.append("]");
                }
            }
            
            sb.append(";");
        }

        return md5(sb.toString());
    }

    /**
     * 比较两个类哈希映射，生成差异结果
     */
    private void compareClasses(Map<String, String> baseClassHashes, 
                                Map<String, String> newClassHashes,
                                DexDiffResult result) {
        // 查找修改和删除的类
        for (Map.Entry<String, String> entry : baseClassHashes.entrySet()) {
            String className = entry.getKey();
            String baseHash = entry.getValue();

            if (newClassHashes.containsKey(className)) {
                String newHash = newClassHashes.get(className);
                if (!baseHash.equals(newHash)) {
                    // 类被修改
                    result.addModifiedClass(className);
                }
            } else {
                // 类被删除
                result.addDeletedClass(className);
            }
        }

        // 查找新增的类
        for (String className : newClassHashes.keySet()) {
            if (!baseClassHashes.containsKey(className)) {
                result.addAddedClass(className);
            }
        }
    }

    /**
     * 比较两个类名列表（简化版本，用于 APK 级别比较）
     */
    private void compareClassLists(List<String> baseClasses, List<String> newClasses, 
                                   DexDiffResult result) {
        Set<String> baseSet = new HashSet<>();
        Set<String> newSet = new HashSet<>();

        if (baseClasses != null) {
            baseSet.addAll(baseClasses);
        }
        if (newClasses != null) {
            newSet.addAll(newClasses);
        }

        // 查找删除的类
        for (String className : baseSet) {
            if (!newSet.contains(className)) {
                result.addDeletedClass(className);
            }
        }

        // 查找新增的类
        for (String className : newSet) {
            if (!baseSet.contains(className)) {
                result.addAddedClass(className);
            }
        }

        // 注意：这个简化版本无法检测修改的类，
        // 因为只有类名列表，没有类内容的哈希
        // 完整比较需要使用 compare(File, File) 方法
    }

    /**
     * 将 dex 类型格式转换为标准类名格式
     * 例如: Lcom/example/Class; -> com.example.Class
     */
    private String convertDexTypeToClassName(String dexType) {
        if (dexType == null) {
            return null;
        }
        if (dexType.startsWith("L") && dexType.endsWith(";")) {
            return dexType.substring(1, dexType.length() - 1).replace('/', '.');
        }
        return dexType;
    }

    /**
     * 将标准类名格式转换为 dex 类型格式
     * 例如: com.example.Class -> Lcom/example/Class;
     */
    private String convertClassNameToDexType(String className) {
        if (className == null) {
            return null;
        }
        return "L" + className.replace('.', '/') + ";";
    }

    /**
     * 写入补丁 dex 文件
     */
    private void writePatchDex(List<ClassDef> classes, File outputFile, Opcodes opcodes) throws IOException {
        // 使用 dexlib2 的 DexFileFactory 写入 dex 文件
        org.jf.dexlib2.writer.io.FileDataStore dataStore = 
            new org.jf.dexlib2.writer.io.FileDataStore(outputFile);
        
        // 创建一个包含指定类的 DexFile
        DexFile patchDex = new DexFile() {
            @Override
            public Set<? extends ClassDef> getClasses() {
                return new LinkedHashSet<>(classes);
            }

            @Override
            public Opcodes getOpcodes() {
                return opcodes;
            }
        };

        // 写入 dex 文件
        org.jf.dexlib2.writer.pool.DexPool.writeTo(dataStore, patchDex);
    }

    /**
     * 计算字符串的 MD5 哈希
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5 应该总是可用的
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
