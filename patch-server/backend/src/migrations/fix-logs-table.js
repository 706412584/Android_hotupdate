const db = require('../models/database');

async function fixLogsTable() {
  console.log('开始修复 logs 表结构...\n');
  
  try {
    // 检查当前表结构
    const columns = await db.query("PRAGMA table_info(logs)");
    console.log('当前表结构:');
    console.table(columns);
    
    const columnNames = columns.map(col => col.name);
    
    // 检查是否需要迁移
    const needsMigration = 
      columnNames.includes('resource') || 
      !columnNames.includes('username') || 
      !columnNames.includes('user_agent') ||
      !columnNames.includes('resource_type');
    
    if (!needsMigration) {
      console.log('\n✅ 表结构已是最新，无需迁移');
      return;
    }
    
    console.log('\n需要迁移表结构...');
    
    // 1. 重命名旧表
    await db.run('ALTER TABLE logs RENAME TO logs_old');
    console.log('✓ 已重命名旧表为 logs_old');
    
    // 2. 创建新表
    await db.run(`
      CREATE TABLE logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER,
        username VARCHAR(50),
        action VARCHAR(50) NOT NULL,
        resource_type VARCHAR(50),
        resource_id INTEGER,
        details TEXT,
        ip_address VARCHAR(45),
        user_agent TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id)
      )
    `);
    console.log('✓ 已创建新表结构');
    
    // 3. 迁移数据
    const oldData = await db.query('SELECT * FROM logs_old');
    console.log(`✓ 找到 ${oldData.length} 条旧数据`);
    
    if (oldData.length > 0) {
      for (const row of oldData) {
        await db.run(`
          INSERT INTO logs (
            id, user_id, username, action, resource_type, resource_id,
            details, ip_address, user_agent, created_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        `, [
          row.id,
          row.user_id,
          row.username || 'unknown',
          row.action,
          row.resource || row.resource_type || null,
          row.resource_id,
          row.details,
          row.ip_address,
          row.user_agent || null,
          row.created_at
        ]);
      }
      console.log(`✓ 已迁移 ${oldData.length} 条数据`);
    }
    
    // 4. 删除旧表
    await db.run('DROP TABLE logs_old');
    console.log('✓ 已删除旧表');
    
    // 5. 验证新表
    const newColumns = await db.query("PRAGMA table_info(logs)");
    console.log('\n新表结构:');
    console.table(newColumns);
    
    const count = await db.get('SELECT COUNT(*) as total FROM logs');
    console.log(`\n✅ 迁移完成！当前日志总数: ${count.total}`);
    
  } catch (error) {
    console.error('❌ 迁移失败:', error);
    throw error;
  }
}

// 运行迁移
fixLogsTable()
  .then(() => {
    console.log('\n迁移成功完成！');
    process.exit(0);
  })
  .catch(error => {
    console.error('\n迁移失败:', error);
    process.exit(1);
  });
