package com.orange.update;

import android.app.Application;
import android.content.Context;

/**
 * 热更新 Application
 *
 * 在 attachBaseContext 中加载补丁，确保：
 * 1. DEX 补丁在类加载前注入
 * 2. 资源补丁在 Activity 创建前加载
 * 3. 所有组件都能使用更新后的代码和资源
 * 
 * 使用方法：
 * 1. 继承此类或在自己的 Application 中调用 HotUpdateHelper.loadPatchIfNeeded()
 * 2. 在 AndroidManifest.xml 中配置：android:name=".PatchApplication"
 * 
 * 单例模式说明：
 * - 推荐方式1：HotUpdateHelper.init(context) + getInstance()
 * - 推荐方式2：HotUpdateHelper.getInstance(context)
 * - 向后兼容：仍然支持 new HotUpdateHelper(context)
 */
public class PatchApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // 方式1：init + getInstance（最推荐，最简洁）
        HotUpdateHelper.init(base);
        HotUpdateHelper.getInstance().loadPatchIfNeeded();
        
        // 方式2：getInstance(context)（推荐，向后兼容）
        // HotUpdateHelper.getInstance(base).loadPatchIfNeeded();
        
        // 方式3：直接创建实例（向后兼容，仍然支持）
        // HotUpdateHelper helper = new HotUpdateHelper(base);
        // helper.loadPatchIfNeeded();
    }
}
