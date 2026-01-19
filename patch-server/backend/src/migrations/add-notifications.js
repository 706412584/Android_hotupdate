const db = require('../models/database');

async function addNotificationsTable() {
  console.log('开始创建 notifications 表...\n');
  
  try {
    // 检查表是否已存在
    const tableExists = await db.get(`
      SELECT name FROM sqlite_master 
      WHERE type='table' AND name='notifications'
    `);
    
    if (tableExists) {
      console.log('✅ notifications 表已存在，无需创建');
      return;
    }
    
    // 创建通知表
    await db.run(`
      CREATE TABLE notifications (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        type VARCHAR(50) NOT NULL,
        title VARCHAR(200) NOT NULL,
        message TEXT NOT NULL,
        link VARCHAR(500),
        data TEXT,
        is_read INTEGER DEFAULT 0,
        read_at DATETIME,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
      )
    `);
    
    console.log('✓ 已创建 notifications 表');
    
    // 创建索引
    await db.run(`
      CREATE INDEX idx_notifications_user_id ON notifications(user_id)
    `);
    
    await db.run(`
      CREATE INDEX idx_notifications_is_read ON notifications(is_read)
    `);
    
    await db.run(`
      CREATE INDEX idx_notifications_created_at ON notifications(created_at)
    `);
    
    console.log('✓ 已创建索引');
    
    console.log('\n✅ 通知表创建完成！');
    
  } catch (error) {
    console.error('❌ 创建通知表失败:', error);
    throw error;
  }
}

// 运行迁移
addNotificationsTable()
  .then(() => {
    console.log('\n迁移成功完成！');
    process.exit(0);
  })
  .catch(error => {
    console.error('\n迁移失败:', error);
    process.exit(1);
  });
