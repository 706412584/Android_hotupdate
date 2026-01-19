const sqlite3 = require('sqlite3').verbose();
const path = require('path');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '../database.db');

const db = new sqlite3.Database(DB_PATH, (err) => {
  if (err) {
    console.error('数据库连接失败:', err);
    process.exit(1);
  }
});

console.log('开始迁移数据库...');

db.serialize(() => {
  // 检查 keystore_path 列是否存在
  db.all("PRAGMA table_info(apps)", (err, columns) => {
    if (err) {
      console.error('查询表结构失败:', err);
      process.exit(1);
    }

    const hasKeystorePath = columns.some(col => col.name === 'keystore_path');

    if (!hasKeystorePath) {
      console.log('添加 keystore_path 列...');
      db.run(`ALTER TABLE apps ADD COLUMN keystore_path VARCHAR(255)`, (err) => {
        if (err) {
          console.error('添加 keystore_path 列失败:', err);
          process.exit(1);
        }
        console.log('✅ keystore_path 列添加成功');
        db.close();
      });
    } else {
      console.log('✅ keystore_path 列已存在，无需迁移');
      db.close();
    }
  });
});
