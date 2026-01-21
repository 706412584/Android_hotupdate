package com.orange.update;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.orange.update.adapter.ViewPagerAdapter;
import com.orange.update.fragment.PatchApplyFragment;
import com.orange.update.fragment.PatchGenerateFragment;
import com.orange.update.fragment.SystemInfoFragment;
import com.orange.update.helper.PermissionHelper;

/**
 * 主 Activity
 * 使用 Fragment + ViewPager2 + TabLayout 架构
 * 代码量从 3649 行减少到 ~100 行
 */
public class MainActivity extends AppCompatActivity {
    
    private PermissionHelper permissionHelper;
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);
        
        // 初始化权限助手
        permissionHelper = new PermissionHelper(this);
        
        // 检查权限
        checkPermissions();
        
        // 初始化视图
        initViews();
        setupViewPager();
    }
    
    private void initViews() {
        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);
    }
    
    private void setupViewPager() {
        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        
        // 添加 Fragment
        adapter.addFragment(new PatchGenerateFragment(), "补丁生成");
        adapter.addFragment(new PatchApplyFragment(), "补丁应用");
        adapter.addFragment(new SystemInfoFragment(), "系统信息");
        
        viewPager.setAdapter(adapter);
        
        // 关联 TabLayout 和 ViewPager2，并设置图标
        new TabLayoutMediator(tabLayout, viewPager,
            (tab, position) -> {
                tab.setText(adapter.getPageTitle(position));
                // 设置图标
                switch (position) {
                    case 0:
                        tab.setIcon(R.drawable.ic_patch_generate);
                        break;
                    case 1:
                        tab.setIcon(R.drawable.ic_patch_apply);
                        break;
                    case 2:
                        tab.setIcon(R.drawable.ic_system_info);
                        break;
                }
            }
        ).attach();
    }
    
    private void checkPermissions() {
        permissionHelper.checkAndRequestStoragePermission(new PermissionHelper.PermissionCallback() {
            @Override
            public void onPermissionGranted() {
                // 权限已授予
            }
            
            @Override
            public void onPermissionDenied() {
                // 权限被拒绝
            }
        });
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHelper.onRequestPermissionsResult(requestCode, grantResults);
    }
}
