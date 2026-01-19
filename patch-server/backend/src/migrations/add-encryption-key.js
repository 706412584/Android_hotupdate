const db = require('../models/database');

async function migrate() {
  try {
    console.log('开始迁移：添加加密密钥字段...');
    
    // 检查字段是否已存在
    const tableInfo = await db.query("PRAGMA table_info(apps)");
    const hasEncryptionKey = tableInfo.some(col => col.name === 'encryption_key');
    
    if (!hasEncryptionKey) {
      // 添加 encryption_key 字段
      await db.run(`
        ALTER TABLE apps 
        ADD COLUMN encryption_key VARCHAR(64)
      `);
      console.log('✅ 已添加 encryption_key 字段');
    } else {
      console.log('⚠️  encryption_key 字段已存在，跳过');
    }
    
    // 检查 encryption_enabled 字段
    const hasEncryptionEnabled = tableInfo.some(col => col.name === 'encryption_enabled');
    
    if (!hasEncryptionEnabled) {
      // 添加 encryption_enabled 字段
      await db.run(`
        ALTER TABLE apps 
        ADD COLUMN encryption_enabled BOOLEAN DEFAULT 0
      `);
      console.log('✅ 已添加 encryption_enabled 字段');
    } else {
      console.log('⚠️  encryption_enabled 字段已存在，跳过');
    }
    
    console.log('✅ 迁移完成');
    process.exit(0);
  } catch (error) {
    console.error('❌ 迁移失败:', error);
    process.exit(1);
  }
}

// 如果直接运行此脚本
if (require.main === module) {
  migrate();
}

module.exports = migrate;
