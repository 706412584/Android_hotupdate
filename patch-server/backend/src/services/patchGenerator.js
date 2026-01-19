const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');

class PatchGenerator {
  constructor() {
    // patch-cli JAR 文件路径
    this.jarPath = process.env.PATCH_CLI_JAR || path.join(__dirname, '../../../patch-cli.jar');
    this.javaPath = process.env.JAVA_PATH || 'java';
  }

  /**
   * 检查 patch-cli 是否可用
   */
  async checkAvailable() {
    return new Promise((resolve) => {
      // 检查 JAR 文件
      if (!fs.existsSync(this.jarPath)) {
        resolve({ 
          available: false, 
          error: 'patch-cli JAR 文件未找到',
          details: {
            jarPath: this.jarPath,
            suggestion: '请配置环境变量 PATCH_CLI_JAR 或编译 patch-cli'
          }
        });
        return;
      }

      // 检查 Java
      const process = spawn(this.javaPath, ['-version']);
      let javaVersion = '';
      
      process.stderr.on('data', (data) => {
        javaVersion += data.toString();
      });

      process.on('close', (code) => {
        if (code === 0) {
          resolve({ 
            available: true,
            javaVersion: javaVersion.split('\n')[0]
          });
        } else {
          resolve({ 
            available: false, 
            error: 'Java 未安装或不可用',
            details: {
              suggestion: '请安装 Java 11 或更高版本'
            }
          });
        }
      });

      process.on('error', () => {
        resolve({ 
          available: false, 
          error: 'Java 未安装',
          details: {
            suggestion: '请安装 Java 11 或更高版本，并确保 java 命令在 PATH 中'
          }
        });
      });
    });
  }

  /**
   * 生成补丁
   * @param {Object} options 生成选项
   * @param {string} options.baseApk 基准 APK 路径
   * @param {string} options.newApk 新版本 APK 路径
   * @param {string} options.output 输出补丁路径
   * @param {string} options.keystore Keystore 文件路径（可选）
   * @param {string} options.keystorePassword Keystore 密码（可选）
   * @param {string} options.keyAlias 密钥别名（可选）
   * @param {string} options.keyPassword 密钥密码（可选）
   * @param {string} options.engine 引擎类型（可选）
   * @param {string} options.mode 补丁模式（可选）
   * @param {boolean} options.verbose 详细日志（可选）
   * @param {Function} onProgress 进度回调
   */
  async generate(options, onProgress) {
    return new Promise((resolve, reject) => {
      const args = ['-jar', this.jarPath];

      // 必需参数
      args.push('--base', options.baseApk);
      args.push('--new', options.newApk);
      args.push('--output', options.output);

      // 签名参数
      if (options.keystore) {
        args.push('--keystore', options.keystore);
        args.push('--keystore-password', options.keystorePassword);
        args.push('--key-alias', options.keyAlias);
        args.push('--key-password', options.keyPassword);
      }

      // 可选参数
      if (options.engine) {
        args.push('--engine', options.engine);
      }
      if (options.mode) {
        args.push('--mode', options.mode);
      }
      if (options.verbose) {
        args.push('--verbose');
      }

      console.log('执行命令:', this.javaPath, args.join(' '));

      const process = spawn(this.javaPath, args);
      let output = '';
      let errorOutput = '';

      process.stdout.on('data', (data) => {
        const text = data.toString();
        output += text;
        console.log(text);

        // 解析进度
        const progressMatch = text.match(/\[(\d+)%\]/);
        if (progressMatch && onProgress) {
          onProgress(parseInt(progressMatch[1]));
        }
      });

      process.stderr.on('data', (data) => {
        const text = data.toString();
        errorOutput += text;
        console.error(text);
      });

      process.on('close', (code) => {
        if (code === 0) {
          // 解析输出获取补丁信息
          const sizeMatch = output.match(/Patch Size:\s*([^\n]+)/);
          const compressionMatch = output.match(/Compression:\s*([^\n]+)/);
          const timeMatch = output.match(/Generation Time:\s*([^\n]+)/);

          resolve({
            success: true,
            output: options.output,
            size: sizeMatch ? sizeMatch[1] : 'Unknown',
            compression: compressionMatch ? compressionMatch[1] : 'Unknown',
            time: timeMatch ? timeMatch[1] : 'Unknown',
            log: output
          });
        } else {
          reject({
            success: false,
            code,
            error: errorOutput || output,
            log: output
          });
        }
      });

      process.on('error', (error) => {
        reject({
          success: false,
          error: error.message,
          log: output
        });
      });
    });
  }

  /**
   * 获取补丁信息（不生成，只分析）
   */
  async analyze(baseApk, newApk) {
    // 这里可以添加分析逻辑
    // 比如比较 APK 大小、版本号等
    return {
      baseSize: fs.statSync(baseApk).size,
      newSize: fs.statSync(newApk).size,
      estimatedPatchSize: 'Unknown'
    };
  }
}

module.exports = new PatchGenerator();
