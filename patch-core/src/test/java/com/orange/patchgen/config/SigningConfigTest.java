package com.orange.patchgen.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SigningConfig 单元测试
 *
 * 覆盖 isValid() 的校验规则与 loadPrivateKey() 的错误分支。
 * 注意：JKS 内容为伪造，因此只断言「加载失败」而非成功路径。
 */
public class SigningConfigTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File existingFile(String name) throws IOException {
        return tempFolder.newFile(name);
    }

    @Test
    public void isValid_falseWhenNothingConfigured() {
        SigningConfig config = new SigningConfig.Builder().build();

        assertThat(config.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenKeystoreFileMissing() {
        SigningConfig config = new SigningConfig.Builder()
                .keystoreFile(new File(tempFolder.getRoot(), "nope.jks"))
                .keystorePassword("pw")
                .keyAlias("alias")
                .keyPassword("pw")
                .build();

        assertThat(config.isValid()).isFalse();
    }

    @Test
    public void isValid_falseWhenAnyCredentialMissing() throws IOException {
        File keystore = existingFile("app.jks");

        // 缺少 keystorePassword
        assertThat(new SigningConfig.Builder()
                .keystoreFile(keystore).keyAlias("a").keyPassword("p").build()
                .isValid()).isFalse();

        // 缺少 keyAlias
        assertThat(new SigningConfig.Builder()
                .keystoreFile(keystore).keystorePassword("p").keyPassword("p").build()
                .isValid()).isFalse();

        // 缺少 keyPassword
        assertThat(new SigningConfig.Builder()
                .keystoreFile(keystore).keystorePassword("p").keyAlias("a").build()
                .isValid()).isFalse();

        // 密码为空串同样无效
        assertThat(new SigningConfig.Builder()
                .keystoreFile(keystore).keystorePassword("").keyAlias("a").keyPassword("p")
                .build().isValid()).isFalse();
    }

    @Test
    public void isValid_trueWhenAllKeystoreFieldsPresent() throws IOException {
        SigningConfig config = new SigningConfig.Builder()
                .keystoreFile(existingFile("app.jks"))
                .keystorePassword("pw")
                .keyAlias("alias")
                .keyPassword("pw")
                .build();

        assertThat(config.isValid()).isTrue();
    }

    @Test
    public void isValid_trueWhenPemFileExistsEvenWithoutKeystore() throws IOException {
        SigningConfig config = new SigningConfig.Builder()
                .pemFile(existingFile("key.pem"))
                .build();

        assertThat(config.isValid()).isTrue();
    }

    @Test
    public void loadPrivateKey_throwsWhenNoKeySourceConfigured() {
        SigningConfig config = new SigningConfig.Builder().build();

        assertThatThrownBy(config::loadPrivateKey)
                .isInstanceOf(SigningConfig.SigningException.class)
                .hasMessageContaining("No valid key source configured");
    }

    @Test
    public void loadPrivateKey_throwsForPemBecauseNotImplemented() throws IOException {
        SigningConfig config = new SigningConfig.Builder()
                .pemFile(existingFile("key.pem"))
                .build();

        assertThatThrownBy(config::loadPrivateKey)
                .isInstanceOf(SigningConfig.SigningException.class)
                .hasMessageContaining("PEM file loading not yet implemented");
    }

    @Test
    public void loadPrivateKey_throwsForCorruptKeystore() throws IOException {
        File bogus = existingFile("bogus.jks");
        java.nio.file.Files.write(bogus.toPath(), "not a real keystore".getBytes());

        SigningConfig config = new SigningConfig.Builder()
                .keystoreFile(bogus)
                .keystorePassword("pw")
                .keyAlias("alias")
                .keyPassword("pw")
                .build();

        assertThatThrownBy(config::loadPrivateKey)
                .isInstanceOf(SigningConfig.SigningException.class)
                .hasMessageContaining("Failed to load private key from keystore");
    }

    @Test
    public void gettersReturnConfiguredValues() throws IOException {
        File keystore = existingFile("app.jks");
        File sourceApk = existingFile("app.apk");

        SigningConfig config = new SigningConfig.Builder()
                .keystoreFile(keystore)
                .keystorePassword("kp")
                .keyAlias("al")
                .keyPassword("pw")
                .sourceApk(sourceApk)
                .build();

        assertThat(config.getKeystoreFile()).isEqualTo(keystore);
        assertThat(config.getKeystorePassword()).isEqualTo("kp");
        assertThat(config.getKeyAlias()).isEqualTo("al");
        assertThat(config.getKeyPassword()).isEqualTo("pw");
        assertThat(config.getSourceApk()).isEqualTo(sourceApk);
        assertThat(config.getPemFile()).isNull();
    }
}
