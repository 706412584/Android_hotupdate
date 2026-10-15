package com.orange.patchgen.cli;

import com.orange.patchgen.PatchGenerator;
import com.orange.patchgen.callback.GeneratorCallback;
import com.orange.patchgen.config.EngineType;
import com.orange.patchgen.config.PatchMode;
import com.orange.patchgen.config.SigningConfig;
import com.orange.patchgen.model.DiffSummary;
import com.orange.patchgen.model.PatchInfo;
import com.orange.patchgen.model.PatchResult;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 补丁生成器命令行工具
 * 
 * Requirements: 8.1-8.11
 */
public class PatchGeneratorCli {

    private static final String VERSION = "1.0.0";
    private static final String PROGRAM_NAME = "patch-generator";
    
    private static boolean verbose = false;

    public static void main(String[] args) {
        Options options = buildOptions();
        CommandLineParser parser = new DefaultParser();
        
        try {
            CommandLine cmd = parser.parse(options, args);
            
            // Handle help option
            if (cmd.hasOption("help")) {
                printHelp(options);
                System.exit(0);
            }
            
            // Handle version option
            if (cmd.hasOption("version")) {
                printVersion();
                System.exit(0);
            }
            
            // Set verbose mode
            verbose = cmd.hasOption("verbose");
            
            // Validate required parameters
            validateRequiredParams(cmd);
            
            // Parse parameters
            File baseApk = new File(cmd.getOptionValue("base"));
            File newApk = new File(cmd.getOptionValue("new"));
            File output = new File(cmd.getOptionValue("output"));
            
            // Validate input files
            validateInputFiles(baseApk, newApk);
            
            // Build signing config if provided
            SigningConfig signingConfig = buildSigningConfig(cmd);
            
            // Parse engine type
            EngineType engineType = parseEngineType(cmd.getOptionValue("engine", "auto"));
            
            // Parse patch mode
            PatchMode patchMode = parsePatchMode(cmd.getOptionValue("mode", "full_dex"));

            // Parse excluded paths (comma-separated)
            List<String> excludedPaths = parseExcludedPaths(cmd.getOptionValue("exclude", ""));

            // Print start message
            printStartMessage(baseApk, newApk, output, engineType, patchMode);
            if (!excludedPaths.isEmpty()) {
                System.out.println("Excluded paths: " + excludedPaths);
            }

            // Build generator
            PatchGenerator generator = new PatchGenerator.Builder()
                    .baseApk(baseApk)
                    .newApk(newApk)
                    .output(output)
                    .signingConfig(signingConfig)
                    .engineType(engineType)
                    .patchMode(patchMode)
                    .config(com.orange.patchgen.config.GeneratorConfig.builder()
                            .excludedPaths(excludedPaths)
                            .build())
                    .callback(new ConsoleCallback())
                    .build();
            
            // Generate patch
            PatchResult result = generator.generate();
            
            // Print result
            printResult(result);
            
            // Exit with appropriate code
            System.exit(result.isSuccess() ? 0 : 1);
            
        } catch (ParseException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println();
            printHelp(options);
            System.exit(1);
        } catch (PatchGenerator.PatchGeneratorException e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(e.getErrorCode());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }

    /**
     * Build command line options
     */
    private static Options buildOptions() {
        Options options = new Options();
        
        // Required options
        options.addOption(Option.builder("b")
                .longOpt("base")
                .desc("Base APK file path (required)")
                .hasArg()
                .argName("FILE")
                .build());
        
        options.addOption(Option.builder("n")
                .longOpt("new")
                .desc("New APK file path (required)")
                .hasArg()
                .argName("FILE")
                .build());
        
        options.addOption(Option.builder("o")
                .longOpt("output")
                .desc("Output patch file path (required)")
                .hasArg()
                .argName("FILE")
                .build());
        
        // Signing options
        options.addOption(Option.builder("k")
                .longOpt("keystore")
                .desc("Keystore file path for signing")
                .hasArg()
                .argName("FILE")
                .build());
        
        options.addOption(Option.builder("a")
                .longOpt("key-alias")
                .desc("Key alias in keystore")
                .hasArg()
                .argName("ALIAS")
                .build());
        
        options.addOption(Option.builder("p")
                .longOpt("key-password")
                .desc("Key password")
                .hasArg()
                .argName("PASSWORD")
                .build());
        
        options.addOption(Option.builder()
                .longOpt("keystore-password")
                .desc("Keystore password (defaults to key password if not specified)")
                .hasArg()
                .argName("PASSWORD")
                .build());
        
        // Engine options
        options.addOption(Option.builder("e")
                .longOpt("engine")
                .desc("Engine type: auto, java, native (default: auto)")
                .hasArg()
                .argName("TYPE")
                .build());
        
        options.addOption(Option.builder("m")
                .longOpt("mode")
                .desc("Patch mode: full_dex, bsdiff (default: full_dex)")
                .hasArg()
                .argName("MODE")
                .build());

        // 排除不参与差异比较的路径（逗号分隔，APK 根相对路径）。
        // 用途：某些 assets 是每次构建都会变的产物（内嵌版本号/时间戳），
        // 体积大但对用户无意义——不排除会让补丁从约 1MB 膨胀到 17MB。
        options.addOption(Option.builder()
                .longOpt("exclude")
                .desc("Comma-separated paths (relative to APK root) excluded from diff, "
                        + "e.g. assets/data/common/tooling-api-all.jar")
                .hasArg()
                .argName("PATHS")
                .build());

        // Other options
        options.addOption(Option.builder("v")
                .longOpt("verbose")
                .desc("Enable verbose output")
                .build());
        
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Print this help message")
                .build());
        
        options.addOption(Option.builder()
                .longOpt("version")
                .desc("Print version information")
                .build());
        
        return options;
    }

    /**
     * Validate required parameters
     */
    private static void validateRequiredParams(CommandLine cmd) throws ParseException {
        if (!cmd.hasOption("base")) {
            throw new ParseException("Missing required option: --base");
        }
        if (!cmd.hasOption("new")) {
            throw new ParseException("Missing required option: --new");
        }
        if (!cmd.hasOption("output")) {
            throw new ParseException("Missing required option: --output");
        }
    }

    /**
     * Validate input files exist and are readable
     */
    private static void validateInputFiles(File baseApk, File newApk) throws ParseException {
        if (!baseApk.exists()) {
            throw new ParseException("Base APK not found: " + baseApk.getAbsolutePath());
        }
        if (!baseApk.canRead()) {
            throw new ParseException("Cannot read base APK: " + baseApk.getAbsolutePath());
        }
        if (!newApk.exists()) {
            throw new ParseException("New APK not found: " + newApk.getAbsolutePath());
        }
        if (!newApk.canRead()) {
            throw new ParseException("Cannot read new APK: " + newApk.getAbsolutePath());
        }
    }

    /**
     * Build signing config from command line options
     */
    private static SigningConfig buildSigningConfig(CommandLine cmd) {
        if (!cmd.hasOption("keystore")) {
            return null;
        }
        
        File keystoreFile = new File(cmd.getOptionValue("keystore"));
        String keyAlias = cmd.getOptionValue("key-alias");
        String keyPassword = cmd.getOptionValue("key-password");
        String keystorePassword = cmd.getOptionValue("keystore-password", keyPassword);
        
        if (keyAlias == null || keyPassword == null) {
            System.err.println("Warning: Keystore provided but key-alias or key-password missing. Signing disabled.");
            return null;
        }
        
        return new SigningConfig.Builder()
                .keystoreFile(keystoreFile)
                .keystorePassword(keystorePassword)
                .keyAlias(keyAlias)
                .keyPassword(keyPassword)
                .build();
    }

    /**
     * Parse engine type from string
     */
    private static EngineType parseEngineType(String value) throws ParseException {
        switch (value.toLowerCase()) {
            case "auto":
                return EngineType.AUTO;
            case "java":
                return EngineType.JAVA;
            case "native":
                return EngineType.NATIVE;
            default:
                throw new ParseException("Invalid engine type: " + value + ". Valid values: auto, java, native");
        }
    }

    /**
     * Parse patch mode from string
     */
    private static PatchMode parsePatchMode(String value) throws ParseException {
        switch (value.toLowerCase()) {
            case "full_dex":
            case "fulldex":
                return PatchMode.FULL_DEX;
            case "bsdiff":
                return PatchMode.BSDIFF;
            default:
                throw new ParseException("Invalid patch mode: " + value + ". Valid values: full_dex, bsdiff");
        }
    }

    /**
     * 解析 --exclude 的逗号分隔路径列表。
     *
     * <p>空白项被丢弃（容忍尾随逗号与多余空格），返回不可变列表。
     */
    private static List<String> parseExcludedPaths(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) {
            return result;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Print help message
     */
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(100);
        
        String header = "\nGenerate hot update patches by comparing two APK files.\n\n";
        String footer = "\nExamples:\n" +
                "  " + PROGRAM_NAME + " -b app-v1.0.apk -n app-v1.1.apk -o patch.zip\n" +
                "  " + PROGRAM_NAME + " --base app-v1.0.apk --new app-v1.1.apk --output patch.zip \\\n" +
                "                   --keystore keystore.jks --key-alias patch --key-password secret\n" +
                "  " + PROGRAM_NAME + " -b old.apk -n new.apk -o patch.zip -e native -m bsdiff -v\n";
        
        formatter.printHelp(PROGRAM_NAME, header, options, footer, true);
    }

    /**
     * Print version information
     */
    private static void printVersion() {
        System.out.println(PROGRAM_NAME + " version " + VERSION);
        System.out.println("Java version: " + System.getProperty("java.version"));
    }

    /**
     * Print start message
     */
    private static void printStartMessage(File baseApk, File newApk, File output, 
                                          EngineType engineType, PatchMode patchMode) {
        System.out.println("=".repeat(60));
        System.out.println("Patch Generator CLI v" + VERSION);
        System.out.println("=".repeat(60));
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Base APK:    " + baseApk.getAbsolutePath());
        System.out.println("  New APK:     " + newApk.getAbsolutePath());
        System.out.println("  Output:      " + output.getAbsolutePath());
        System.out.println("  Engine:      " + engineType.name().toLowerCase());
        System.out.println("  Mode:        " + patchMode.name().toLowerCase());
        System.out.println();
    }

    /**
     * Print generation result
     */
    private static void printResult(PatchResult result) {
        System.out.println();
        System.out.println("=".repeat(60));
        
        if (!result.isSuccess()) {
            System.out.println("FAILED");
            System.out.println("=".repeat(60));
            System.out.println("Error Code: " + result.getErrorCode());
            System.out.println("Error Message: " + result.getErrorMessage());
            return;
        }
        
        if (!result.hasPatch()) {
            System.out.println("NO CHANGES DETECTED");
            System.out.println("=".repeat(60));
            System.out.println("The two APKs are identical. No patch needed.");
            return;
        }
        
        System.out.println("SUCCESS");
        System.out.println("=".repeat(60));
        System.out.println();
        
        // Patch file info
        System.out.println("Patch File:");
        System.out.println("  Path:        " + result.getPatchFile().getAbsolutePath());
        System.out.println("  Size:        " + formatSize(result.getPatchSize()));
        System.out.println();
        
        // Size comparison
        System.out.println("Size Comparison:");
        System.out.println("  Base APK:    " + formatSize(result.getBaseApkSize()));
        System.out.println("  New APK:     " + formatSize(result.getNewApkSize()));
        System.out.println("  Patch:       " + formatSize(result.getPatchSize()));
        System.out.println("  Ratio:       " + String.format("%.2f%%", result.getCompressionRatio() * 100));
        System.out.println();
        
        // Diff summary
        DiffSummary summary = result.getDiffSummary();
        if (summary != null) {
            System.out.println("Changes Summary:");
            System.out.println("  Classes:     " + summary.getModifiedClasses() + " modified, " +
                    summary.getAddedClasses() + " added, " + summary.getDeletedClasses() + " deleted");
            System.out.println("  Resources:   " + summary.getModifiedResources() + " modified, " +
                    summary.getAddedResources() + " added, " + summary.getDeletedResources() + " deleted");
            System.out.println("  Assets:      " + summary.getModifiedAssets() + " modified, " +
                    summary.getAddedAssets() + " added, " + summary.getDeletedAssets() + " deleted");
            System.out.println();
        }
        
        // Patch info
        PatchInfo patchInfo = result.getPatchInfo();
        if (patchInfo != null) {
            System.out.println("Patch Info:");
            System.out.println("  Patch ID:    " + patchInfo.getPatchId());
            System.out.println("  Base Ver:    " + patchInfo.getBaseVersion() + " (" + patchInfo.getBaseVersionCode() + ")");
            System.out.println("  Target Ver:  " + patchInfo.getTargetVersion() + " (" + patchInfo.getTargetVersionCode() + ")");
            System.out.println("  Mode:        " + patchInfo.getPatchMode());
            System.out.println("  MD5:         " + patchInfo.getMd5());
            System.out.println("  Created:     " + formatTime(patchInfo.getCreateTime()));
            System.out.println();
        }
        
        // Generation time
        System.out.println("Generation Time: " + formatDuration(result.getGenerateTime()));
    }

    /**
     * Format file size to human readable string
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * Format timestamp to readable string
     */
    private static String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    /**
     * Format duration to readable string
     */
    private static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        } else if (millis < 60000) {
            return String.format("%.2f s", millis / 1000.0);
        } else {
            long minutes = millis / 60000;
            long seconds = (millis % 60000) / 1000;
            return String.format("%d min %d s", minutes, seconds);
        }
    }

    /**
     * Console callback for progress display
     */
    private static class ConsoleCallback implements GeneratorCallback {
        
        private String currentPhase = "";
        private int lastProgress = -1;

        @Override
        public void onParseStart(String apkPath) {
            currentPhase = "Parsing";
            System.out.println("[Parsing] " + new File(apkPath).getName());
        }

        @Override
        public void onParseProgress(int current, int total) {
            printProgress("Parsing", current, total);
        }

        @Override
        public void onCompareStart() {
            currentPhase = "Comparing";
            System.out.println("[Comparing] Analyzing differences...");
        }

        @Override
        public void onCompareProgress(int current, int total, String currentFile) {
            if (verbose) {
                System.out.println("  [" + current + "/" + total + "] " + currentFile);
            } else {
                printProgress("Comparing", current, total);
            }
        }

        @Override
        public void onPackStart() {
            currentPhase = "Packing";
            System.out.println("[Packing] Creating patch package...");
        }

        @Override
        public void onPackProgress(long current, long total) {
            int percent = (int) ((current * 100) / total);
            printProgress("Packing", percent, 100);
        }

        @Override
        public void onSignStart() {
            currentPhase = "Signing";
            System.out.println("[Signing] Signing patch...");
        }

        @Override
        public void onComplete(PatchResult result) {
            // Result will be printed by main method
        }

        @Override
        public void onError(int errorCode, String message) {
            System.err.println("[Error] " + message + " (code: " + errorCode + ")");
        }

        private void printProgress(String phase, int current, int total) {
            int percent = (total > 0) ? (current * 100 / total) : 0;
            if (percent != lastProgress) {
                lastProgress = percent;
                if (!verbose) {
                    System.out.print("\r[" + phase + "] " + percent + "%");
                    if (percent == 100) {
                        System.out.println();
                    }
                }
            }
        }
    }
}
