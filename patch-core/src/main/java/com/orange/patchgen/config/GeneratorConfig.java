package com.orange.patchgen.config;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * 生成器配置
 *
 * Requirements: 7.3-7.5
 */
public class GeneratorConfig {
    private EngineType engineType;
    private PatchMode patchMode;
    private int threadCount;
    private long maxMemory;
    private boolean verbose;
    private File tempDir;

    /**
     * 不参与差异比较的资源/资产路径（相对 APK 根的路径，如
     * {@code assets/data/common/tooling-api-all.jar}）。
     *
     * <p><b>为什么需要</b>：某些 assets 是**每次构建都会变**的产物（内嵌版本号、
     * 打包时间戳等），内容对用户无意义却体积巨大。实测 ACS 的
     * {@code assets/data/common/tooling-api-all.jar} 有 16.9MB，r11→r12 之间
     * 内部只有 {@code BuildInfo.class}（版本号）一个文件不同——但它让补丁从
     * 约 1MB 膨胀到 17MB（占补丁体积 99%），且每次发版必然触发。
     *
     * <p>排除后这些文件既不进补丁、也不触发"有变化"判定。风险是：如果某个被排除
     * 的文件真的需要更新，用户拿不到——因此**只应排除确认与业务逻辑无关的构建产物**。
     */
    private List<String> excludedPaths;

    private GeneratorConfig(Builder builder) {
        this.engineType = builder.engineType;
        this.patchMode = builder.patchMode;
        this.threadCount = builder.threadCount;
        this.maxMemory = builder.maxMemory;
        this.verbose = builder.verbose;
        this.tempDir = builder.tempDir;
        this.excludedPaths = builder.excludedPaths;
    }

    public EngineType getEngineType() {
        return engineType;
    }

    public PatchMode getPatchMode() {
        return patchMode;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public long getMaxMemory() {
        return maxMemory;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public File getTempDir() {
        return tempDir;
    }

    /** 不参与差异比较的路径（不可变列表，永不为 null）。 */
    public List<String> getExcludedPaths() {
        return excludedPaths == null ? Collections.<String>emptyList() : excludedPaths;
    }

    /**
     * 该路径是否应被排除。
     *
     * <p>匹配规则：全等，或位于以该路径为前缀的目录下（配置 {@code res/foo}
     * 可排除 {@code res/foo/bar.xml}）。
     */
    public boolean isExcluded(String relativePath) {
        if (relativePath == null) {
            return false;
        }
        String normalized = relativePath.replace('\\', '/');
        for (String excluded : getExcludedPaths()) {
            if (excluded == null || excluded.isEmpty()) {
                continue;
            }
            String e = excluded.replace('\\', '/');
            if (normalized.equals(e) || normalized.startsWith(e + "/")) {
                return true;
            }
        }
        return false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EngineType engineType = EngineType.AUTO;
        private PatchMode patchMode = PatchMode.FULL_DEX;
        private int threadCount = Runtime.getRuntime().availableProcessors();
        private long maxMemory = Runtime.getRuntime().maxMemory();
        private boolean verbose = false;
        private File tempDir = new File(System.getProperty("java.io.tmpdir"));
        private List<String> excludedPaths = Collections.emptyList();

        public Builder engineType(EngineType type) {
            this.engineType = type;
            return this;
        }

        public Builder patchMode(PatchMode mode) {
            this.patchMode = mode;
            return this;
        }

        public Builder threadCount(int count) {
            this.threadCount = count;
            return this;
        }

        public Builder maxMemory(long bytes) {
            this.maxMemory = bytes;
            return this;
        }

        public Builder verbose(boolean verbose) {
            this.verbose = verbose;
            return this;
        }

        public Builder tempDir(File dir) {
            this.tempDir = dir;
            return this;
        }

        /** 设置不参与差异比较的路径列表；null 视为空列表。 */
        public Builder excludedPaths(List<String> paths) {
            this.excludedPaths = paths == null ? Collections.<String>emptyList() : paths;
            return this;
        }

        public GeneratorConfig build() {
            return new GeneratorConfig(this);
        }
    }
}
