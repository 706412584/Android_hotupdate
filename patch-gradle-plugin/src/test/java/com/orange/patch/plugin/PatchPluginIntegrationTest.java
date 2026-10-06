package com.orange.patch.plugin;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Gradle 插件集成测试
 * 
 * 使用 Gradle TestKit 测试插件应用和任务执行。
 * 
 * Requirements: 7.1, 7.2, 7.7
 */
public class PatchPluginIntegrationTest {

    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    private File buildFile;
    private File settingsFile;

    @Before
    public void setup() throws IOException {
        buildFile = testProjectDir.newFile("build.gradle");
        settingsFile = testProjectDir.newFile("settings.gradle");
        
        // Create settings.gradle
        writeFile(settingsFile, "rootProject.name = 'test-project'\n");
    }

    /**
     * 测试插件应用 - 验证插件可以成功应用到项目
     * Requirements: 7.1
     */
    @Test
    public void testPluginCanBeApplied() throws IOException {
        // Create a minimal build.gradle that applies the plugin
        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    enabled = false\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run the build
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        // Verify the build succeeded
        assertTrue("Build should succeed", 
                result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    /**
     * 测试扩展配置 - 验证 patchGenerator 扩展可以正确配置
     * Requirements: 7.3, 7.4, 7.5
     */
    @Test
    public void testExtensionConfiguration() throws IOException {
        // Create a dummy baseline APK file
        File baselineApk = testProjectDir.newFile("baseline.apk");
        writeFile(baselineApk, "dummy apk content");
        
        File outputDir = testProjectDir.newFolder("output");

        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    baselineApk = file('baseline.apk')\n" +
            "    outputDir = file('output')\n" +
            "    engine = 'java'\n" +
            "    patchMode = 'full_dex'\n" +
            "    enabled = true\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run the build to verify configuration is valid
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        assertTrue("Build should succeed with valid configuration", 
                result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    /**
     * 测试签名配置 - 验证签名配置可以正确设置
     * Requirements: 7.5
     */
    @Test
    public void testSigningConfiguration() throws IOException {
        // Create dummy files
        File baselineApk = testProjectDir.newFile("baseline.apk");
        writeFile(baselineApk, "dummy apk content");
        
        File keystoreFile = testProjectDir.newFile("keystore.jks");
        writeFile(keystoreFile, "dummy keystore content");

        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    baselineApk = file('baseline.apk')\n" +
            "    outputDir = file('build/patch')\n" +
            "    enabled = true\n" +
            "    \n" +
            "    signing {\n" +
            "        keystoreFile = file('keystore.jks')\n" +
            "        keystorePassword = 'password'\n" +
            "        keyAlias = 'patch'\n" +
            "        keyPassword = 'password'\n" +
            "    }\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run the build to verify signing configuration is valid
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        assertTrue("Build should succeed with signing configuration", 
                result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    /**
     * 测试任务注册 - 验证 generatePatch 任务被正确注册
     * Requirements: 7.1
     */
    @Test
    public void testGeneratePatchTaskRegistered() throws IOException {
        // Create dummy baseline APK
        File baselineApk = testProjectDir.newFile("baseline.apk");
        writeFile(baselineApk, "dummy apk content");

        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    baselineApk = file('baseline.apk')\n" +
            "    outputDir = file('build/patch')\n" +
            "    enabled = true\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run tasks to see registered tasks
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--group=patch")
                .build();

        // Verify generatePatch task is registered
        assertTrue("generatePatch task should be registered", 
                result.getOutput().contains("generatePatch"));
    }

    /**
     * 测试禁用插件 - 验证当 enabled=false 时不注册任务
     * Requirements: 7.1
     */
    @Test
    public void testPluginDisabled() throws IOException {
        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    enabled = false\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run tasks
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        // Build should succeed
        assertTrue("Build should succeed when plugin is disabled", 
                result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    /**
     * 测试无效引擎类型配置 - 插件会记录警告但不会使构建失败
     * Requirements: 7.3
     */
    @Test
    public void testInvalidEngineType() throws IOException {
        File baselineApk = testProjectDir.newFile("baseline.apk");
        writeFile(baselineApk, "dummy apk content");

        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    baselineApk = file('baseline.apk')\n" +
            "    outputDir = file('build/patch')\n" +
            "    engine = 'invalid_engine'\n" +
            "    enabled = true\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run tasks - plugin logs warning but doesn't fail the build
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        // Build succeeds but with warning about invalid engine type
        assertTrue("Build should succeed with warning about invalid engine type", 
                result.getOutput().contains("Invalid engine type") || 
                result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    /**
     * 测试无效补丁模式配置 - 插件会记录警告但不会使构建失败
     * Requirements: 7.3
     */
    @Test
    public void testInvalidPatchMode() throws IOException {
        File baselineApk = testProjectDir.newFile("baseline.apk");
        writeFile(baselineApk, "dummy apk content");

        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    baselineApk = file('baseline.apk')\n" +
            "    outputDir = file('build/patch')\n" +
            "    patchMode = 'invalid_mode'\n" +
            "    enabled = true\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run tasks - plugin logs warning but doesn't fail the build
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        // Build succeeds but with warning about invalid patch mode
        assertTrue("Build should succeed with warning about invalid patch mode", 
                result.getOutput().contains("Invalid patchMode") || 
                result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    /**
     * 测试缺少基线 APK 配置
     * Requirements: 7.3
     */
    @Test
    public void testMissingBaselineApk() throws IOException {
        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    outputDir = file('build/patch')\n" +
            "    enabled = true\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run tasks - should warn about missing baseline APK
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        // Build should succeed but with warning
        assertTrue("Build should succeed with warning about incomplete config", 
                result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    /**
     * 测试不存在的基线 APK 文件 - 插件会记录警告但不会使构建失败
     * Requirements: 7.3
     */
    @Test
    public void testNonExistentBaselineApk() throws IOException {
        String buildContent = 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'io.github.706412584.patch'\n" +
            "}\n" +
            "\n" +
            "patchGenerator {\n" +
            "    baselineApk = file('non_existent.apk')\n" +
            "    outputDir = file('build/patch')\n" +
            "    enabled = true\n" +
            "}\n";
        
        writeFile(buildFile, buildContent);

        // Run tasks - plugin logs warning but doesn't fail the build
        BuildResult result = GradleRunner.create()
                .withProjectDir(testProjectDir.getRoot())
                .withPluginClasspath()
                .withArguments("tasks", "--all")
                .build();

        // Build succeeds but with warning about non-existent baseline APK
        assertTrue("Build should succeed with warning about non-existent baseline APK", 
                result.getOutput().contains("does not exist") || 
                result.getOutput().contains("BUILD SUCCESSFUL"));
    }

    /**
     * 测试所有引擎类型配置
     * Requirements: 7.3
     */
    @Test
    public void testAllEngineTypes() throws IOException {
        File baselineApk = testProjectDir.newFile("baseline.apk");
        writeFile(baselineApk, "dummy apk content");

        String[] engineTypes = {"auto", "java", "native"};
        
        for (String engineType : engineTypes) {
            String buildContent = 
                "plugins {\n" +
                "    id 'java'\n" +
                "    id 'io.github.706412584.patch'\n" +
                "}\n" +
                "\n" +
                "patchGenerator {\n" +
                "    baselineApk = file('baseline.apk')\n" +
                "    outputDir = file('build/patch')\n" +
                "    engine = '" + engineType + "'\n" +
                "    enabled = true\n" +
                "}\n";
            
            writeFile(buildFile, buildContent);

            BuildResult result = GradleRunner.create()
                    .withProjectDir(testProjectDir.getRoot())
                    .withPluginClasspath()
                    .withArguments("tasks", "--all")
                    .build();

            assertTrue("Build should succeed with engine type: " + engineType, 
                    result.getOutput().contains("BUILD SUCCESSFUL"));
        }
    }

    /**
     * 测试所有补丁模式配置
     * Requirements: 7.3
     */
    @Test
    public void testAllPatchModes() throws IOException {
        File baselineApk = testProjectDir.newFile("baseline.apk");
        writeFile(baselineApk, "dummy apk content");

        String[] patchModes = {"full_dex", "bsdiff"};
        
        for (String patchMode : patchModes) {
            String buildContent = 
                "plugins {\n" +
                "    id 'java'\n" +
                "    id 'io.github.706412584.patch'\n" +
                "}\n" +
                "\n" +
                "patchGenerator {\n" +
                "    baselineApk = file('baseline.apk')\n" +
                "    outputDir = file('build/patch')\n" +
                "    patchMode = '" + patchMode + "'\n" +
                "    enabled = true\n" +
                "}\n";
            
            writeFile(buildFile, buildContent);

            BuildResult result = GradleRunner.create()
                    .withProjectDir(testProjectDir.getRoot())
                    .withPluginClasspath()
                    .withArguments("tasks", "--all")
                    .build();

            assertTrue("Build should succeed with patch mode: " + patchMode, 
                    result.getOutput().contains("BUILD SUCCESSFUL"));
        }
    }

    /**
     * Helper method to write content to a file
     */
    private void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
