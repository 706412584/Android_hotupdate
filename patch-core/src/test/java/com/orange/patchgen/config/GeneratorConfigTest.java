package com.orange.patchgen.config;

import org.junit.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeneratorConfig 单元测试
 *
 * 覆盖 Builder 默认值与各 setter 的链式行为。
 */
public class GeneratorConfigTest {

    @Test
    public void builderDefaults_matchDocumentedValues() {
        GeneratorConfig config = GeneratorConfig.builder().build();

        assertThat(config.getEngineType()).isEqualTo(EngineType.AUTO);
        assertThat(config.getPatchMode()).isEqualTo(PatchMode.FULL_DEX);
        assertThat(config.getThreadCount())
                .isEqualTo(Runtime.getRuntime().availableProcessors());
        assertThat(config.getMaxMemory())
                .isEqualTo(Runtime.getRuntime().maxMemory());
        assertThat(config.isVerbose()).isFalse();
        assertThat(config.getTempDir())
                .isEqualTo(new File(System.getProperty("java.io.tmpdir")));
    }

    @Test
    public void builderOverridesAllFields() {
        File tempDir = new File("/tmp/custom");

        GeneratorConfig config = GeneratorConfig.builder()
                .engineType(EngineType.NATIVE)
                .patchMode(PatchMode.BSDIFF)
                .threadCount(3)
                .maxMemory(1024L)
                .verbose(true)
                .tempDir(tempDir)
                .build();

        assertThat(config.getEngineType()).isEqualTo(EngineType.NATIVE);
        assertThat(config.getPatchMode()).isEqualTo(PatchMode.BSDIFF);
        assertThat(config.getThreadCount()).isEqualTo(3);
        assertThat(config.getMaxMemory()).isEqualTo(1024L);
        assertThat(config.isVerbose()).isTrue();
        assertThat(config.getTempDir()).isEqualTo(tempDir);
    }

    @Test
    public void builderIsReusableAndProducesIndependentInstances() {
        GeneratorConfig.Builder builder = GeneratorConfig.builder();

        GeneratorConfig first = builder.engineType(EngineType.JAVA).build();
        GeneratorConfig second = builder.engineType(EngineType.NATIVE).build();

        assertThat(first.getEngineType()).isEqualTo(EngineType.JAVA);
        assertThat(second.getEngineType()).isEqualTo(EngineType.NATIVE);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    public void engineType_hasExpectedConstants() {
        assertThat(EngineType.values())
                .containsExactly(EngineType.AUTO, EngineType.JAVA, EngineType.NATIVE);
    }

    @Test
    public void patchMode_hasExpectedConstants() {
        assertThat(PatchMode.values())
                .containsExactly(PatchMode.FULL_DEX, PatchMode.BSDIFF);
    }
}
