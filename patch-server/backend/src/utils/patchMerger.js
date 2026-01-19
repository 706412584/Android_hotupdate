const fs = require('fs').promises;
const path = require('path');
const { exec } = require('child_process');
const { promisify } = require('util');
const execAsync = promisify(exec);

/**
 * 补丁合并工具
 * 
 * 功能：
 * - 合并多个增量补丁为一个完整补丁
 * - 支持跨版本更新（如 v1.0 → v1.3 合并 v1.0→v1.1, v1.1→v1.2, v1.2→v1.3）
 * - 自动计算合并后的 MD5
 */
class PatchMerger {
  constructor(patchCliPath) {
    // patch-cli JAR 文件路径
    this.patchCliPath = patchCliPath || path.join(__dirname, '../../../patch-cli.jar');
  }

  /**
   * 检查 patch-cli 是否可用
   */
  async checkPatchCli() {
    try {
      await fs.access(this.patchCliPath);
      return true;
    } catch (error) {
      console.error('patch-cli not found at:', this.patchCliPath);
      return false;
    }
  }

  /**
   * 合并补丁链
   * 
   * @param {Array} patchChain - 补丁链数组，按顺序排列
   *   例如: [
   *     { baseVersion: '1.0', version: '1.1', filePath: '/path/to/patch_1.0_to_1.1.zip' },
   *     { baseVersion: '1.1', version: '1.2', filePath: '/path/to/patch_1.1_to_1.2.zip' },
   *     { baseVersion: '1.2', version: '1.3', filePath: '/path/to/patch_1.2_to_1.3.zip' }
   *   ]
   * @param {string} baseApkPath - 基础 APK 路径（起始版本）
   * @param {string} outputPath - 输出合并补丁的路径
   * @param {Object} options - 可选参数
   * @returns {Promise<Object>} 合并结果
   */
  async mergePatchChain(patchChain, baseApkPath, outputPath, options = {}) {
    if (!patchChain || patchChain.length === 0) {
      throw new Error('补丁链不能为空');
    }

    // 验证补丁链的连续性
    this.validatePatchChain(patchChain);

    console.log(`开始合并补丁链: ${patchChain[0].baseVersion} → ${patchChain[patchChain.length - 1].version}`);
    console.log(`补丁数量: ${patchChain.length}`);

    // 如果只有一个补丁，直接复制
    if (patchChain.length === 1) {
      console.log('只有一个补丁，直接复制');
      await fs.copyFile(patchChain[0].filePath, outputPath);
      
      const stats = await fs.stat(outputPath);
      return {
        success: true,
        baseVersion: patchChain[0].baseVersion,
        targetVersion: patchChain[0].version,
        patchCount: 1,
        outputPath,
        fileSize: stats.size
      };
    }

    // 多个补丁需要合并
    // 策略：依次应用每个补丁，最后生成完整补丁
    const tempDir = path.join(path.dirname(outputPath), 'temp_merge_' + Date.now());
    await fs.mkdir(tempDir, { recursive: true });

    try {
      // 1. 复制基础 APK 到临时目录
      const currentApk = path.join(tempDir, 'current.apk');
      await fs.copyFile(baseApkPath, currentApk);

      // 2. 依次应用每个补丁
      for (let i = 0; i < patchChain.length; i++) {
        const patch = patchChain[i];
        console.log(`应用补丁 ${i + 1}/${patchChain.length}: ${patch.baseVersion} → ${patch.version}`);
        
        const nextApk = path.join(tempDir, `version_${patch.version}.apk`);
        
        // 使用 patch-cli 应用补丁（这里需要实现补丁应用逻辑）
        // 注意：patch-cli 目前只支持生成补丁，不支持应用补丁
        // 所以我们需要另一种策略
        
        // 实际上，我们应该直接从原始 APK 生成完整补丁
        // 而不是依次应用补丁
        throw new Error('补丁合并功能需要原始 APK 文件，请使用 generateMergedPatch 方法');
      }

    } finally {
      // 清理临时目录
      await this.cleanupTempDir(tempDir);
    }
  }

  /**
   * 从原始 APK 生成合并补丁
   * 
   * 这是推荐的方法：直接从基础版本 APK 和目标版本 APK 生成完整补丁
   * 
   * @param {string} baseApkPath - 基础版本 APK 路径
   * @param {string} targetApkPath - 目标版本 APK 路径
   * @param {string} outputPath - 输出补丁路径
   * @param {Object} options - 可选参数
   * @returns {Promise<Object>} 生成结果
   */
  async generateMergedPatch(baseApkPath, targetApkPath, outputPath, options = {}) {
    console.log(`生成合并补丁: ${path.basename(baseApkPath)} → ${path.basename(targetApkPath)}`);

    // 检查 patch-cli 是否可用
    if (!await this.checkPatchCli()) {
      throw new Error('patch-cli 不可用，请确保 patch-cli.jar 存在');
    }

    // 检查输入文件
    await fs.access(baseApkPath);
    await fs.access(targetApkPath);

    // 构建命令
    const cmd = [
      'java', '-jar', this.patchCliPath,
      '--base', baseApkPath,
      '--new', targetApkPath,
      '--output', outputPath
    ];

    // 添加签名参数（如果提供）
    if (options.keystore) {
      cmd.push('--keystore', options.keystore);
      cmd.push('--keystore-password', options.keystorePassword);
      cmd.push('--key-alias', options.keyAlias);
      cmd.push('--key-password', options.keyPassword);
    }

    // 添加其他参数
    if (options.engineType) {
      cmd.push('--engine', options.engineType);
    }
    if (options.patchMode) {
      cmd.push('--patch-mode', options.patchMode);
    }

    console.log('执行命令:', cmd.join(' '));

    try {
      const { stdout, stderr } = await execAsync(cmd.join(' '), {
        maxBuffer: 10 * 1024 * 1024 // 10MB buffer
      });

      if (stderr) {
        console.warn('patch-cli stderr:', stderr);
      }
      if (stdout) {
        console.log('patch-cli stdout:', stdout);
      }

      // 检查输出文件
      const stats = await fs.stat(outputPath);
      
      // 计算 MD5
      const md5 = await this.calculateMd5(outputPath);

      return {
        success: true,
        outputPath,
        fileSize: stats.size,
        md5,
        stdout,
        stderr
      };

    } catch (error) {
      console.error('生成补丁失败:', error);
      throw new Error(`生成补丁失败: ${error.message}`);
    }
  }

  /**
   * 验证补丁链的连续性
   */
  validatePatchChain(patchChain) {
    for (let i = 0; i < patchChain.length - 1; i++) {
      const current = patchChain[i];
      const next = patchChain[i + 1];
      
      if (current.version !== next.baseVersion) {
        throw new Error(
          `补丁链不连续: ${current.baseVersion}→${current.version} 和 ${next.baseVersion}→${next.version}`
        );
      }
    }
  }

  /**
   * 计算文件 MD5
   */
  async calculateMd5(filePath) {
    const crypto = require('crypto');
    const fileBuffer = await fs.readFile(filePath);
    const hashSum = crypto.createHash('md5');
    hashSum.update(fileBuffer);
    return hashSum.digest('hex');
  }

  /**
   * 清理临时目录
   */
  async cleanupTempDir(tempDir) {
    try {
      await fs.rm(tempDir, { recursive: true, force: true });
      console.log('临时目录已清理:', tempDir);
    } catch (error) {
      console.warn('清理临时目录失败:', error.message);
    }
  }

  /**
   * 查找补丁链
   * 
   * 从数据库中查找从 baseVersion 到 targetVersion 的补丁链
   * 
   * @param {Object} db - 数据库实例
   * @param {string} appId - 应用 ID
   * @param {string} baseVersion - 起始版本
   * @param {string} targetVersion - 目标版本
   * @returns {Promise<Array>} 补丁链数组
   */
  async findPatchChain(db, appId, baseVersion, targetVersion) {
    // 使用广度优先搜索查找补丁链
    const queue = [[{ version: baseVersion, path: [] }]];
    const visited = new Set([baseVersion]);

    while (queue.length > 0) {
      const current = queue.shift();
      const lastNode = current[current.length - 1];

      // 查找从当前版本出发的所有补丁
      const patches = await db.all(`
        SELECT id, patch_id, base_version, version, file_path, file_size, md5
        FROM patches
        WHERE app_id = (SELECT id FROM apps WHERE app_id = ?)
          AND base_version = ?
          AND status = 'active'
        ORDER BY created_at DESC
      `, [appId, lastNode.version]);

      for (const patch of patches) {
        // 找到目标版本
        if (patch.version === targetVersion) {
          return [...lastNode.path, {
            id: patch.id,
            patchId: patch.patch_id,
            baseVersion: patch.base_version,
            version: patch.version,
            filePath: patch.file_path,
            fileSize: patch.file_size,
            md5: patch.md5
          }];
        }

        // 继续搜索
        if (!visited.has(patch.version)) {
          visited.add(patch.version);
          queue.push([...current, {
            version: patch.version,
            path: [...lastNode.path, {
              id: patch.id,
              patchId: patch.patch_id,
              baseVersion: patch.base_version,
              version: patch.version,
              filePath: patch.file_path,
              fileSize: patch.file_size,
              md5: patch.md5
            }]
          }]);
        }
      }
    }

    // 未找到补丁链
    return null;
  }
}

module.exports = PatchMerger;
