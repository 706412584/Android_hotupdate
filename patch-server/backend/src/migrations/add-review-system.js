const db = require('../models/database');

async function migrate() {
  try {
    console.log('开始迁移：添加审核系统...');

    // 1. 为 apps 表添加审核相关字段
    await db.run(`
      ALTER TABLE apps ADD COLUMN review_status TEXT DEFAULT 'approved'
    `).catch(() => console.log('review_status 字段已存在'));

    await db.run(`
      ALTER TABLE apps ADD COLUMN review_note TEXT
    `).catch(() => console.log('review_note 字段已存在'));

    await db.run(`
      ALTER TABLE apps ADD COLUMN reviewed_at DATETIME
    `).catch(() => console.log('reviewed_at 字段已存在'));

    await db.run(`
      ALTER TABLE apps ADD COLUMN reviewed_by INTEGER
    `).catch(() => console.log('reviewed_by 字段已存在'));

    // 2. 创建系统配置表
    await db.run(`
      CREATE TABLE IF NOT EXISTS system_config (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        key TEXT UNIQUE NOT NULL,
        value TEXT NOT NULL,
        description TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
      )
    `);

    // 3. 插入默认配置
    await db.run(`
      INSERT OR IGNORE INTO system_config (key, value, description)
      VALUES ('app_review_enabled', 'false', '是否启用应用审核')
    `);

    await db.run(`
      INSERT OR IGNORE INTO system_config (key, value, description)
      VALUES ('auto_approve_admin', 'true', '管理员创建的应用是否自动通过审核')
    `);

    console.log('✅ 审核系统迁移完成');
  } catch (error) {
    console.error('❌ 迁移失败:', error);
    throw error;
  }
}

// 如果直接运行此脚本
if (require.main === module) {
  migrate()
    .then(() => process.exit(0))
    .catch(() => process.exit(1));
}

module.exports = { migrate };
