# 补丁生成的限制和注意事项

## ✅ 已修复：字符串常量修改现在可以被检测

### 修复说明

**版本**: 1.3.2+  
**修复日期**: 2026-01-19

之前的版本中，修改代码中的硬编码字符串常量不会被检测到。现在已经修复，字符串常量的任何修改都会被正确识别。

### 修复前后对比

#### 修复前（v1.3.1 及更早版本）

```
测试 APK:
- com.xianmr.TiecodeDebugApplication1.0.apk - 包含"开始游戏"
- com.xianmr.TiecodeDebugApplication1.1.apk - 包含"开始游戏1"

结果:
============================================================
NO CHANGES DETECTED
============================================================
The two APKs are identical. No patch needed.
```

#### 修复后（v1.3.2+）

```
测试 APK:
- com.xianmr.TiecodeDebugApplication1.0.apk - 包含"开始游戏"
- com.xianmr.TiecodeDebugApplication1.1.apk - 包含"开始游戏1"

结果:
============================================================
SUCCESS
============================================================
Patch File: 6.31 KB
Changes Summary:
  Classes:     1 modified, 0 added, 0 deleted
```

### 技术实现

修改了 `DexDiffer.java` 中的 `getImplementationHash` 方法，在计算方法哈希时包含字符串常量：

```java
private String getImplementationHash(MethodImplementation impl) {
    StringBuilder sb = new StringBuilder();
    sb.append(impl.getRegisterCount());
    sb.append("|");

    // 指令序列（包含字符串常量）
    for (Instruction instruction : impl.getInstructions()) {
        sb.append(instruction.getOpcode().name);
        
        // 提取字符串常量
        if (instruction instanceof ReferenceInstruction) {
            ReferenceInstruction refInst = (ReferenceInstruction) instruction;
            Reference ref = refInst.getReference();
            
            // 如果是字符串引用，包含字符串内容
            if (ref instanceof StringReference) {
                StringReference strRef = (StringReference) ref;
                sb.append("[STR:");
                sb.append(strRef.getString());
                sb.append("]");
            }
        }
        
        sb.append(";");
    }

    return md5(sb.toString());
}
```

### 现在可以检测的修改

✅ **字符串常量修改**
```java
// 修改前
String text = "开始游戏";

// 修改后
String text = "开始游戏1";
```

✅ **字符串拼接修改**
```java
// 修改前
String msg = "Hello " + "World";

// 修改后
String msg = "Hello " + "Android";
```

✅ **日志消息修改**
```java
// 修改前
Log.d(TAG, "User logged in");

// 修改后
Log.d(TAG, "User logged in successfully");
```

### 仍然建议使用资源文件

虽然现在可以检测字符串常量的修改，但仍然**强烈建议**将用户可见的文本放在资源文件中：

**推荐做法**：
```xml
<!-- res/values/strings.xml -->
<resources>
    <string name="start_game">开始游戏</string>
</resources>
```

```java
// 代码中使用
String text = getString(R.string.start_game);
```

**优点**：
- ✅ 支持多语言国际化
- ✅ 更容易维护和修改
- ✅ 符合 Android 最佳实践
- ✅ 可以在不重新编译的情况下修改文本

---

## 历史问题记录（已修复）

### 问题描述（v1.3.1 及更早版本）

当你只修改代码中的**硬编码字符串常量**（String literals），而不修改类结构、方法或字段时，patch-cli 会报告"NO CHANGES DETECTED"，不会生成补丁。

### 原因分析

#### DexDiffer 的旧比较逻辑

旧版本的 `calculateClassHash` 方法只比较：
1. ✅ 类的类型和访问标志
2. ✅ 父类和接口
3. ✅ 字段签名（名称、类型、访问标志）
4. ✅ 方法签名（名称、参数、返回值、访问标志）
5. ✅ 方法实现的**字节码指令**

但不比较：
1. ❌ 字符串常量池（String pool）
2. ❌ 数字常量
3. ❌ 其他常量池数据

### 为什么最初这样设计？

这是一个**有意的设计决策**，原因包括：

1. **性能考虑**：字符串常量池可能很大，逐字节比较会很慢
2. **热更新的目的**：热更新主要用于修复代码逻辑错误，而不是修改文本
3. **国际化最佳实践**：字符串应该放在资源文件（strings.xml）中，而不是硬编码
4. **避免误报**：编译器优化可能改变常量池的布局，但不影响功能

但实际使用中发现，很多开发者确实会修改硬编码的字符串（如日志消息、调试信息等），因此决定修复这个限制。

---

## 其他注意事项

### 仍然不会被检测的修改

以下修改仍然不会触发补丁生成：

1. **注释修改**：注释在编译后会被移除
   ```java
   // 修改注释不会触发补丁
   public void method() { }
   ```

2. **代码格式化**：不影响字节码
   ```java
   // 格式化不会触发补丁
   public void method(){return;}  // vs
   public void method() {
       return;
   }
   ```

3. **变量重命名**（局部变量）：可能被编译器优化
   ```java
   // 局部变量重命名可能不会触发补丁
   int count = 0;  // vs  int num = 0;
   ```

### 建议

1. **使用资源文件**：所有用户可见的文本都应该放在资源文件中
2. **测试补丁**：生成补丁后，在测试环境验证是否包含了预期的修改
3. **查看补丁内容**：解压补丁文件，检查 `patch_info.json` 中的变更列表
4. **代码审查**：确保修改会影响类的结构或方法实现

---

**文档版本**: 2.0  
**最后更新**: 2026-01-19  
**修复版本**: v1.3.2+  
**相关文件**: 
- `patch-core/src/main/java/com/orange/patchgen/differ/DexDiffer.java`
- `patch-core/src/main/java/com/orange/patchgen/PatchGenerator.java`
