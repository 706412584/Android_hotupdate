package com.orange.update.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewPager2 适配器
 * 管理 Fragment 页面
 */
public class ViewPagerAdapter extends FragmentStateAdapter {
    
    private final List<Fragment> fragments = new ArrayList<>();
    private final List<String> fragmentTitles = new ArrayList<>();
    
    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }
    
    public void addFragment(Fragment fragment, String title) {
        fragments.add(fragment);
        fragmentTitles.add(title);
    }
    
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments.get(position);
    }
    
    @Override
    public int getItemCount() {
        return fragments.size();
    }
    
    public String getPageTitle(int position) {
        return fragmentTitles.get(position);
    }
}
