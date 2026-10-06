package com.orange.patchgen.callback;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeneratorErrorCode 单元测试
 *
 * 覆盖错误码取值区间与 getErrorMessage() 的映射。
 */
public class GeneratorErrorCodeTest {

    @Test
    public void errorCodes_fallInDocumentedThousandsRange() {
        assertThat(GeneratorErrorCode.ERROR_FILE_NOT_FOUND).isBetween(1000, 1999);
        assertThat(GeneratorErrorCode.ERROR_INVALID_APK).isBetween(2000, 2999);
        assertThat(GeneratorErrorCode.ERROR_VERSION_MISMATCH).isBetween(3000, 3999);
        assertThat(GeneratorErrorCode.ERROR_KEYSTORE_NOT_FOUND).isBetween(4000, 4999);
        assertThat(GeneratorErrorCode.ERROR_NATIVE_LIB_NOT_FOUND).isBetween(5000, 5999);
        assertThat(GeneratorErrorCode.ERROR_CANCELLED).isBetween(6000, 6999);
    }

    @Test
    public void errorCodes_areAllDistinct() {
        int[] codes = {
                GeneratorErrorCode.ERROR_FILE_NOT_FOUND,
                GeneratorErrorCode.ERROR_FILE_READ_FAILED,
                GeneratorErrorCode.ERROR_FILE_WRITE_FAILED,
                GeneratorErrorCode.ERROR_INSUFFICIENT_SPACE,
                GeneratorErrorCode.ERROR_PERMISSION_DENIED,
                GeneratorErrorCode.ERROR_INVALID_APK,
                GeneratorErrorCode.ERROR_APK_PARSE_FAILED,
                GeneratorErrorCode.ERROR_DEX_PARSE_FAILED,
                GeneratorErrorCode.ERROR_MANIFEST_PARSE_FAILED,
                GeneratorErrorCode.ERROR_VERSION_MISMATCH,
                GeneratorErrorCode.ERROR_COMPARE_FAILED,
                GeneratorErrorCode.ERROR_NO_CHANGES,
                GeneratorErrorCode.ERROR_KEYSTORE_NOT_FOUND,
                GeneratorErrorCode.ERROR_INVALID_KEY,
                GeneratorErrorCode.ERROR_SIGNING_FAILED,
                GeneratorErrorCode.ERROR_KEY_LOAD_FAILED,
                GeneratorErrorCode.ERROR_NATIVE_LIB_NOT_FOUND,
                GeneratorErrorCode.ERROR_NATIVE_INIT_FAILED,
                GeneratorErrorCode.ERROR_BSDIFF_FAILED,
                GeneratorErrorCode.ERROR_BSPATCH_FAILED,
                GeneratorErrorCode.ERROR_CANCELLED,
                GeneratorErrorCode.ERROR_TIMEOUT,
                GeneratorErrorCode.ERROR_UNKNOWN,
        };

        Set<Integer> unique = new HashSet<>();
        for (int code : codes) {
            assertThat(unique.add(code)).as("重复的错误码: %d", code).isTrue();
        }
        assertThat(unique).hasSize(codes.length);
    }

    @Test
    public void getErrorMessage_mapsKnownCodes() {
        assertThat(GeneratorErrorCode.getErrorMessage(GeneratorErrorCode.ERROR_FILE_NOT_FOUND))
                .isEqualTo("File not found");
        assertThat(GeneratorErrorCode.getErrorMessage(GeneratorErrorCode.ERROR_INVALID_APK))
                .isEqualTo("Invalid APK file");
        assertThat(GeneratorErrorCode.getErrorMessage(GeneratorErrorCode.ERROR_CANCELLED))
                .isEqualTo("Operation cancelled");
    }

    @Test
    public void getErrorMessage_fallsBackToUnknownForUnmappedCode() {
        // ERROR_UNKNOWN 本身没有 case 分支，因此会落到 default
        assertThat(GeneratorErrorCode.getErrorMessage(GeneratorErrorCode.ERROR_UNKNOWN))
                .isEqualTo("Unknown error");
        assertThat(GeneratorErrorCode.getErrorMessage(-1)).isEqualTo("Unknown error");
        assertThat(GeneratorErrorCode.getErrorMessage(0)).isEqualTo("Unknown error");
    }

    @Test
    public void everyDeclaredConstantHasAMessageMapping() {
        // 除 ERROR_UNKNOWN（走 default）外，其余常量都应映射到非 "Unknown error" 文案
        int[] declared = {
                GeneratorErrorCode.ERROR_FILE_NOT_FOUND,
                GeneratorErrorCode.ERROR_FILE_READ_FAILED,
                GeneratorErrorCode.ERROR_FILE_WRITE_FAILED,
                GeneratorErrorCode.ERROR_INSUFFICIENT_SPACE,
                GeneratorErrorCode.ERROR_PERMISSION_DENIED,
                GeneratorErrorCode.ERROR_INVALID_APK,
                GeneratorErrorCode.ERROR_APK_PARSE_FAILED,
                GeneratorErrorCode.ERROR_DEX_PARSE_FAILED,
                GeneratorErrorCode.ERROR_MANIFEST_PARSE_FAILED,
                GeneratorErrorCode.ERROR_VERSION_MISMATCH,
                GeneratorErrorCode.ERROR_COMPARE_FAILED,
                GeneratorErrorCode.ERROR_NO_CHANGES,
                GeneratorErrorCode.ERROR_KEYSTORE_NOT_FOUND,
                GeneratorErrorCode.ERROR_INVALID_KEY,
                GeneratorErrorCode.ERROR_SIGNING_FAILED,
                GeneratorErrorCode.ERROR_KEY_LOAD_FAILED,
                GeneratorErrorCode.ERROR_NATIVE_LIB_NOT_FOUND,
                GeneratorErrorCode.ERROR_NATIVE_INIT_FAILED,
                GeneratorErrorCode.ERROR_BSDIFF_FAILED,
                GeneratorErrorCode.ERROR_BSPATCH_FAILED,
                GeneratorErrorCode.ERROR_CANCELLED,
                GeneratorErrorCode.ERROR_TIMEOUT,
        };

        for (int code : declared) {
            assertThat(GeneratorErrorCode.getErrorMessage(code))
                    .as("错误码 %d 缺少文案映射", code)
                    .isNotEqualTo("Unknown error");
        }
    }

    @Test
    public void classIsNotInstantiable() throws Exception {
        Constructor<GeneratorErrorCode> ctor = GeneratorErrorCode.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(ctor.getModifiers())).isTrue();
    }
}
