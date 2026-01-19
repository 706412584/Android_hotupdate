const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const util = require('util');
const execPromise = util.promisify(exec);

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '../database.db');
const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(__dirname, '../uploads');
const BACKUP_DIR = process.env.BACKUP_DIR || path.join(__dirname, '../backups');

// 确保备份目录存在
if (!fs.existsSync(BACKUP_DIR)) {
  fs.mkdirSync(BACKUP_DIR, { recursive: true });
}

async function backupDatabase() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').split('T')[0];
  const backupFile = path.join(BACKUP_DIR, `database-${timestamp}.db`);
  
  try {
    // 复制数据库文件
    fs.copyFileSync(DB_PATH, backupFile);
    console.log(`✅ 数据库备份成功: ${backupFile}`);
    return backupFile;
  } catch (error) {
    console.error('❌ 数据库备份失败:', error);
    throw error;
  }
}

async function backupUploads() {
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').split('T')[0];
  const backupFile = path.join(BACKUP_DIR, `uploads-${timestamp}.tar.gz`);
  
  try {
    // 使用 tar 压缩上传目录
    if (process.platform === 'win32') {
      // Windows: 使用 PowerShell 压缩
      const cmd = `powershell Compress-Archive -Path "${UPLOAD_DIR}\\*" -DestinationPath "${backupFile.replace('.tar.gz', '.zip')}" -Force`;
      await execPromise(cmd);
      console.log(`✅ 文件备份成功: ${backupFile.replace('.tar.gz', '.zip')}`);
      return backupFile.replace('.tar.gz', '.zip');
    } else {
      // Linux/Mac: 使用 tar
      const cmd = `tar -czf "${backupFile}" -C "${path.dirname(UPLOAD_DIR)}" "${path.basename(UPLOAD_DIR)}"`;
      await execPromise(cmd);
      console.log(`✅ 文件备份成功: ${backupFile}`);
      return backupFile;
    }
  } catch (error) {
    console.error('❌ 文件备份失败:', error);
    throw error;
  }
}

async function cleanOldBackups(keepDays = 7) {
  try {
    const files = fs.readdirSync(BACKUP_DIR);
    const now = Date.now();
    const maxAge = keepDays * 24 * 60 * 60 * 1000;
    
    let deletedCount = 0;
    
    files.forEach(file => {
      const filePath = path.join(BACKUP_DIR, file);
      const stats = fs.statSync(filePath);
      const age = now - stats.mtimeMs;
      
      if (age > maxAge) {
        fs.unlinkSync(filePath);
        deletedCount++;
        console.log(`🗑️  删除旧备份: ${file}`);
      }
    });
    
    if (deletedCount > 0) {
      console.log(`✅ 清理完成，删除了 ${deletedCount} 个旧备份`);
    } else {
      console.log('✅ 无需清理旧备份');
    }
  } catch (error) {
    console.error('❌ 清理旧备份失败:', error);
  }
}

async function backup() {
  console.log('🔄 开始备份...\n');
  
  try {
    // 备份数据库
    await backupDatabase();
    
    // 备份上传文件
    if (fs.existsSync(UPLOAD_DIR)) {
      await backupUploads();
    } else {
      console.log('⚠️  上传目录不存在，跳过文件备份');
    }
    
    // 清理旧备份
    await cleanOldBackups(7);
    
    console.log('\n✅ 备份完成！');
  } catch (error) {
    console.error('\n❌ 备份失败:', error);
    process.exit(1);
  }
}

// 如果直接运行此脚本
if (require.main === module) {
  backup();
}

module.exports = { backup, backupDatabase, backupUploads, cleanOldBackups };
