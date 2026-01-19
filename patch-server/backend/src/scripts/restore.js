const fs = require('fs').promises;
const path = require('path');
const { exec } = require('child_process');
const { promisify } = require('util');

const execAsync = promisify(exec);

/**
 * 恢复数据库和文件
 * @param {string} backupFile - 备份文件路径
 */
async function restore(backupFile) {
  console.log('开始恢复数据...');
  console.log('备份文件:', backupFile);

  try {
    // 检查备份文件是否存在
    await fs.access(backupFile);
  } catch (error) {
    throw new Error('备份文件不存在: ' + backupFile);
  }

  const backupDir = path.dirname(backupFile);
  const tempDir = path.join(backupDir, 'temp_restore_' + Date.now());
  const dbPath = path.join(__dirname, '../../data/database.db');
  const uploadsDir = path.join(__dirname, '../../uploads');

  try {
    // 创建临时目录
    await fs.mkdir(tempDir, { recursive: true });
    console.log('✅ 临时目录已创建:', tempDir);

    // 解压备份文件
    console.log('📦 正在解压备份文件...');
    const isWindows = process.platform === 'win32';
    
    if (isWindows) {
      // Windows: 使用 PowerShell 解压
      const psCommand = `Expand-Archive -Path "${backupFile}" -DestinationPath "${tempDir}" -Force`;
      await execAsync(`powershell -Command "${psCommand}"`);
    } else {
      // Linux/Mac: 使用 tar
      await execAsync(`tar -xzf "${backupFile}" -C "${tempDir}"`);
    }
    console.log('✅ 备份文件已解压');

    // 备份当前数据（以防恢复失败）
    const currentBackupDir = path.join(backupDir, 'before_restore_' + Date.now());
    await fs.mkdir(currentBackupDir, { recursive: true });
    
    try {
      await fs.copyFile(dbPath, path.join(currentBackupDir, 'database.db'));
      console.log('✅ 当前数据库已备份');
    } catch (error) {
      console.warn('⚠️  备份当前数据库失败:', error.message);
    }

    // 恢复数据库
    console.log('💾 正在恢复数据库...');
    const backupDbPath = path.join(tempDir, 'database.db');
    
    try {
      await fs.access(backupDbPath);
      await fs.copyFile(backupDbPath, dbPath);
      console.log('✅ 数据库已恢复');
    } catch (error) {
      throw new Error('恢复数据库失败: ' + error.message);
    }

    // 恢复上传文件
    console.log('📁 正在恢复上传文件...');
    const backupUploadsDir = path.join(tempDir, 'uploads');
    
    try {
      await fs.access(backupUploadsDir);
      
      // 删除当前 uploads 目录
      try {
        await fs.rm(uploadsDir, { recursive: true, force: true });
      } catch (error) {
        console.warn('⚠️  删除当前上传目录失败:', error.message);
      }

      // 复制备份的 uploads 目录
      await copyDir(backupUploadsDir, uploadsDir);
      console.log('✅ 上传文件已恢复');
    } catch (error) {
      console.warn('⚠️  恢复上传文件失败:', error.message);
    }

    // 清理临时目录
    await fs.rm(tempDir, { recursive: true, force: true });
    console.log('✅ 临时文件已清理');

    console.log('🎉 数据恢复完成！');
    console.log('⚠️  请重启服务以应用更改');

    return {
      success: true,
      message: '数据恢复完成，请重启服务',
      currentBackup: currentBackupDir
    };
  } catch (error) {
    // 清理临时目录
    try {
      await fs.rm(tempDir, { recursive: true, force: true });
    } catch (e) {
      // 忽略清理错误
    }

    console.error('❌ 恢复失败:', error);
    throw error;
  }
}

/**
 * 递归复制目录
 */
async function copyDir(src, dest) {
  await fs.mkdir(dest, { recursive: true });
  const entries = await fs.readdir(src, { withFileTypes: true });

  for (const entry of entries) {
    const srcPath = path.join(src, entry.name);
    const destPath = path.join(dest, entry.name);

    if (entry.isDirectory()) {
      await copyDir(srcPath, destPath);
    } else {
      await fs.copyFile(srcPath, destPath);
    }
  }
}

// 如果直接运行此脚本
if (require.main === module) {
  const backupFile = process.argv[2];
  
  if (!backupFile) {
    console.error('用法: node restore.js <备份文件路径>');
    process.exit(1);
  }

  restore(backupFile)
    .then((result) => {
      console.log(result);
      process.exit(0);
    })
    .catch((error) => {
      console.error('恢复失败:', error);
      process.exit(1);
    });
}

module.exports = { restore };
