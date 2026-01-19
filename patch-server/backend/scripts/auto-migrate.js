const sqlite3 = require('sqlite3').verbose();
const path = require('path');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, '../database.db');

const db = new sqlite3.Database(DB_PATH, (err) => {
  if (err) {
    console.error('数据库连接失败:', err);
    process.exit(1);
  }
});

console.log('🔍 检查数据库结构...\n');

// 需要的列定义
const requiredColumns = {
  apps: [
    { name: 'keystore_path', type: 'VARCHAR(255)', default: null }
  ],
  users: [
    { name: 'avatar', type: 'VARCHAR(255)', default: null },
    { name: 'status', type: 'VARCHAR(20)', default: "'active'" }
  ]
};

let migrationsNeeded = 0;
let migrationsCompleted = 0;

db.serialize(() => {
  const tables = Object.keys(requiredColumns);
  let tablesProcessed = 0;

  tables.forEach(tableName => {
    db.all(`PRAGMA table_info(${tableName})`, (err, columns) => {
      if (err) {
        console.error(`❌ 查询 ${tableName} 表结构失败:`, err);
        return;
      }

      const existingColumns = columns.map(col => col.name);
      const missingColumns = requiredColumns[tableName].filter(
        col => !existingColumns.includes(col.name)
      );

      if (missingColumns.length > 0) {
        console.log(`📋 表 ${tableName} 需要添加 ${missingColumns.length} 个列:`);
        missingColumns.forEach(col => {
          console.log(`   - ${col.name} (${col.type})`);
          migrationsNeeded++;
        });

        // 添加缺失的列
        missingColumns.forEach((col, index) => {
          const defaultClause = col.default ? ` DEFAULT ${col.default}` : '';
          const sql = `ALTER TABLE ${tableName} ADD COLUMN ${col.name} ${col.type}${defaultClause}`;
          
          db.run(sql, (err) => {
            if (err) {
              console.error(`   ❌ 添加 ${col.name} 失败:`, err.message);
            } else {
              console.log(`   ✅ 添加 ${col.name} 成功`);
              migrationsCompleted++;
            }

            // 检查是否所有迁移都完成
            if (index === missingColumns.length - 1) {
              tablesProcessed++;
              if (tablesProcessed === tables.length) {
                finishMigration();
              }
            }
          });
        });
      } else {
        console.log(`✅ 表 ${tableName} 结构完整`);
        tablesProcessed++;
        if (tablesProcessed === tables.length) {
          finishMigration();
        }
      }
    });
  });
});

function finishMigration() {
  setTimeout(() => {
    console.log('\n' + '='.repeat(50));
    if (migrationsNeeded === 0) {
      console.log('✅ 数据库结构已是最新，无需迁移');
    } else {
      console.log(`✅ 迁移完成: ${migrationsCompleted}/${migrationsNeeded} 个列已添加`);
    }
    console.log('='.repeat(50));
    db.close();
  }, 500);
}
