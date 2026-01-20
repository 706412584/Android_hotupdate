# HotUpdateHelper 单例模式使用说明

## 概述

从 1.3.7 版本开始，`HotUpdateHelper` 支持单例模式，提供三种使用方式，完全向后兼容。

## 为什么使用单例模式？

1. **节省内存**：避免重复创建实例
2. **状态共享**：多个地方使用同一个实例，状态一致
3. **线程安全**：使用双重检查锁定（DCL）保证线程安全
4. **使用便捷**：初始化后无需每次传入 context
5. **最佳实践**：符合 Android 开发规范

## 使用方式

### 方式1：init + getInstance（最推荐）⭐⭐⭐⭐⭐

```java
public class PatchApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 初始化（只需一次）
        HotUpdateHelper.init(base);
        
        // 使用（无需传 context）
        HotUpdateHelper.getInstance().loadPatchIfNeeded();
    }
}
```

**优点：**
- ✅ 最简洁：初始化后无需每次传 context
- ✅ 最安全：未初始化会抛出清晰的异常提示
- ✅ 最高效：只创建一个实例
- ✅ 最优雅：代码更清晰易读

**在 Activity 中使用：**
```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 直接使用，无需传 context
        HotUpdateHelper.getInstance().applyPatch(patchFile, callback);
    }
}
```

### 方式2：getInstance(context)（推荐）⭐⭐⭐⭐

```java
public class PatchApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 自动初始化并使用
        HotUpdateHelper.getInstance(base).loadPatchIfNeeded();
    }
}
```

**优点：**
- ✅ 自动初始化：如果未初始化会自动初始化
- ✅ 向后兼容：与旧版本 API 风格一致
- ✅ 灵活性高：可以在任何地方调用

### 方式3：直接创建（向后兼容）⭐⭐⭐

```java
public class PatchApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 直接创建实例（仍然支持）
        HotUpdateHelper helper = new HotUpdateHelper(base);
        helper.loadPatchIfNeeded();
    }
}
```

**说明：**
- 为了向后兼容，仍然支持直接 `new HotUpdateHelper(context)`
- 旧代码无需修改，可以继续使用
- 但推荐迁移到方式1或方式2

## 完整示例

### Application 中初始化

```java
public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 初始化 HotUpdateHelper
        HotUpdateHelper.init(base);
        
        // 加载已应用的补丁
        HotUpdateHelper.getInstance().loadPatchIfNeeded();
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // 设置全局日志回调（可选）
        HotUpdateHelper.setGlobalLogCallback(new LogCallback() {
            @Override
            public void onLog(LogLevel level, String tag, String message) {
                // 处理日志
            }
        });
    }
}
```

### Activity 中应用补丁

```java
public class MainActivity extends AppCompatActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 应用补丁按钮
        findViewById(R.id.btnApplyPatch).setOnClickListener(v -> {
            File patchFile = new File(getExternalFilesDir(null), "patch.zip");
            
            // 直接使用，无需传 context
            HotUpdateHelper.getInstance().applyPatch(patchFile, new HotUpdateHelper.Callback() {
                @Override
                public void onProgress(int percent, String message) {
                    runOnUiThread(() -> {
                        progressBar.setProgress(percent);
                        tvStatus.setText(message);
                    });
                }
                
                @Override
                public void onSuccess(PatchResult result) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "补丁应用成功！", Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "补丁应用失败: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
        
        // 查询补丁状态
        if (HotUpdateHelper.getInstance().hasAppliedPatch()) {
            PatchInfo patchInfo = HotUpdateHelper.getInstance().getAppliedPatchInfo();
            tvPatchInfo.setText("当前补丁版本: " + patchInfo.getPatchVersion());
        }
    }
}
```

### Service 中使用

```java
public class UpdateService extends Service {
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 直接使用，无需传 context
        HotUpdateHelper helper = HotUpdateHelper.getInstance();
        
        if (helper.hasAppliedPatch()) {
            Log.i(TAG, "已应用补丁: " + helper.getAppliedPatchId());
        }
        
        return START_NOT_STICKY;
    }
}
```

## API 对比

| API | 使用方式 | 优点 | 推荐度 |
|-----|---------|------|--------|
| `init(context)` + `getInstance()` | 初始化一次，后续无需传 context | 最简洁、最安全 | ⭐⭐⭐⭐⭐ |
| `getInstance(context)` | 每次传 context，自动初始化 | 灵活、向后兼容 | ⭐⭐⭐⭐ |
| `new HotUpdateHelper(context)` | 直接创建实例 | 向后兼容 | ⭐⭐⭐ |

## 实现原理

### 双重检查锁定（DCL）

```java
public class HotUpdateHelper {
    // 单例实例（使用 volatile 保证线程安全）
    private static volatile HotUpdateHelper sInstance;
    private static final Object sLock = new Object();
    
    /**
     * 初始化单例实例
     */
    public static void init(Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new HotUpdateHelper(context);
                }
            }
        }
    }
    
    /**
     * 获取单例实例（无参数）
     */
    public static HotUpdateHelper getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException(
                "HotUpdateHelper not initialized. Please call init(context) first."
            );
        }
        return sInstance;
    }
    
    /**
     * 获取单例实例（带 context 参数，向后兼容）
     */
    public static HotUpdateHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (sLock) {
                if (sInstance == null) {
                    sInstance = new HotUpdateHelper(context);
                }
            }
        }
        return sInstance;
    }
    
    /**
     * 构造函数（向后兼容）
     */
    public HotUpdateHelper(Context context) {
        // 初始化代码...
    }
}
```

**关键点：**
1. `volatile` 关键字：防止指令重排序
2. 双重检查：第一次检查避免不必要的同步，第二次检查确保只创建一个实例
3. `synchronized` 锁：保证线程安全
4. 异常提示：未初始化时抛出清晰的异常信息

## 迁移指南

### 从旧版本迁移

**旧代码：**
```java
// Application
HotUpdateHelper helper = new HotUpdateHelper(base);
helper.loadPatchIfNeeded();

// Activity
HotUpdateHelper helper = new HotUpdateHelper(this);
helper.applyPatch(patchFile, callback);
```

**新代码（推荐）：**
```java
// Application - 初始化
HotUpdateHelper.init(base);
HotUpdateHelper.getInstance().loadPatchIfNeeded();

// Activity - 直接使用
HotUpdateHelper.getInstance().applyPatch(patchFile, callback);
```

**迁移步骤：**
1. 在 Application.attachBaseContext() 中添加 `HotUpdateHelper.init(context)`
2. 全局搜索 `new HotUpdateHelper(` 或 `HotUpdateHelper.getInstance(context)`
3. 替换为 `HotUpdateHelper.getInstance()`
4. 测试验证

**注意：**
- 旧代码无需立即修改，可以继续使用
- 建议逐步迁移到新的 API
- 三种方式可以混用（但不推荐）

## 常见问题

### Q1: 必须调用 init() 吗？

**A:** 不是必须的。如果使用 `getInstance(context)` 或 `new HotUpdateHelper(context)`，会自动初始化。但推荐使用 `init()` + `getInstance()`，代码更简洁。

### Q2: init() 应该在哪里调用？

**A:** 推荐在 `Application.attachBaseContext()` 或 `Application.onCreate()` 中调用。

### Q3: 如果忘记调用 init() 会怎样？

**A:** 如果直接调用 `getInstance()`（无参数），会抛出 `IllegalStateException`，提示需要先调用 `init(context)`。

### Q4: 可以多次调用 init() 吗？

**A:** 可以，但只有第一次调用会创建实例，后续调用会被忽略。

### Q5: 单例模式是线程安全的吗？

**A:** 是的。使用了双重检查锁定（DCL）+ `volatile` 关键字，保证线程安全。

### Q6: 可以混用三种方式吗？

**A:** 可以，但不推荐。建议统一使用 `init()` + `getInstance()` 的方式。

### Q7: 单例实例什么时候释放？

**A:** 单例实例会在应用进程结束时自动释放。如果需要手动释放，可以调用 `helper.release()`。

### Q8: 在 attachBaseContext 中使用单例安全吗？

**A:** 安全。`HotUpdateHelper` 使用延迟初始化，在 `attachBaseContext` 阶段可以安全使用。

## 性能对比

| 方式 | 内存占用 | 创建耗时 | 使用便捷性 | 推荐度 |
|------|---------|---------|-----------|--------|
| init + getInstance | 低（只创建一次） | 首次较慢，后续极快 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| getInstance(context) | 低（只创建一次） | 首次较慢，后续极快 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| new HotUpdateHelper | 高（每次创建） | 每次都需要初始化 | ⭐⭐⭐ | ⭐⭐⭐ |

## 最佳实践

### 1. 在 Application 中初始化

```java
public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // 初始化 HotUpdateHelper
        HotUpdateHelper.init(base);
        
        // 加载补丁
        HotUpdateHelper.getInstance().loadPatchIfNeeded();
    }
}
```

### 2. 在 Activity 中使用

```java
public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 直接使用，无需传 context
        HotUpdateHelper.getInstance().applyPatch(patchFile, callback);
    }
}
```

### 3. 设置全局日志回调

```java
HotUpdateHelper.setGlobalLogCallback(new LogCallback() {
    @Override
    public void onLog(LogLevel level, String tag, String message) {
        // 处理日志
    }
});
```

### 4. 查询补丁状态

```java
HotUpdateHelper helper = HotUpdateHelper.getInstance();

if (helper.hasAppliedPatch()) {
    PatchInfo info = helper.getAppliedPatchInfo();
    Log.i(TAG, "补丁版本: " + info.getPatchVersion());
}
```

### 5. 释放资源（可选）

```java
@Override
protected void onDestroy() {
    super.onDestroy();
    // 通常不需要手动释放，应用退出时会自动释放
    // HotUpdateHelper.getInstance().release();
}
```

## 错误处理

### 未初始化异常

```java
try {
    HotUpdateHelper.getInstance().applyPatch(patchFile, callback);
} catch (IllegalStateException e) {
    // 未初始化，需要先调用 init(context)
    Log.e(TAG, "HotUpdateHelper not initialized", e);
    HotUpdateHelper.init(this);
    HotUpdateHelper.getInstance().applyPatch(patchFile, callback);
}
```

### 推荐做法

```java
// 在 Application 中初始化，避免异常
public class MyApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        HotUpdateHelper.init(base);  // 确保初始化
    }
}

// 在其他地方直接使用
HotUpdateHelper.getInstance().applyPatch(patchFile, callback);
```

## 总结

- ⭐⭐⭐⭐⭐ 最推荐：`HotUpdateHelper.init(context)` + `getInstance()`
- ⭐⭐⭐⭐ 推荐：`HotUpdateHelper.getInstance(context)`
- ⭐⭐⭐ 向后兼容：`new HotUpdateHelper(context)`
- ✅ 线程安全：使用双重检查锁定（DCL）
- ✅ 节省内存：只创建一个实例
- ✅ 使用便捷：初始化后无需每次传 context
- ✅ 无需修改旧代码：完全向后兼容
- ✅ 清晰的异常提示：未初始化时会抛出明确的异常信息
