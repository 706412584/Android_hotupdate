const sqlite3 = require('sqlite3').verbose();
const path = require('path');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'data/database.db');

console.log('📂 数据库路径:', DB_PATH);

const db = new sqlite3.Database(DB_PATH, (err) => {
  if (err) {
    console.error('❌ 数据库连接失败:', err);
    process.exit(1);
  }
  console.log('✅ 数据库连接成功\n');
});

// 查询所有应用
db.all('SELECT id, app_id, app_name, package_name, status FROM apps', [], (err, apps) => {
  if (err) {
    console.error('查询应用失败:', err);
    return;
  }
  
  console.log('📱 所有应用:');
  console.log('='.repeat(100));
  apps.forEach(app => {
    console.log(`ID: ${app.id} | app_id: ${app.app_id} | 名称: ${app.app_name} | 包名: ${app.package_name} | 状态: ${app.status}`);
  });
  console.log('');
  
  // 查询所有补丁及其关联的应用
  db.all(`
    SELECT 
      p.id as patch_db_id,
      p.patch_id,
      p.version,
      p.base_version,
      p.status as patch_status,
      p.app_id as patch_app_id,
      p.created_at,
      a.id as app_db_id,
      a.app_id,
      a.app_name,
      a.package_name
    FROM patches p
    LEFT JOIN apps a ON p.app_id = a.id
    ORDER BY p.created_at DESC
  `, [], (err, patches) => {
    if (err) {
      console.error('查询补丁失败:', err);
      db.close();
      return;
    }
    
    console.log('📦 所有补丁及其关联:');
    console.log('='.repeat(100));
    patches.forEach(patch => {
      console.log(`补丁 ID: ${patch.patch_db_id} | patch_id: ${patch.patch_id}`);
      console.log(`  版本: ${patch.base_version} → ${patch.version} | 状态: ${patch.patch_status}`);
      console.log(`  关联的 app_id (数字): ${patch.patch_app_id}`);
      console.log(`  关联的应用: ${patch.app_name || '未找到'} (${patch.package_name || 'N/A'})`);
      console.log(`  应用 app_id (字符串): ${patch.app_id || 'N/A'}`);
      console.log(`  创建时间: ${patch.created_at}`);
      console.log('-'.repeat(100));
    });
    
    // 检查是否有孤立的补丁（app_id 不匹配）
    const orphanPatches = patches.filter(p => !p.app_name);
    if (orphanPatches.length > 0) {
      console.log('\n⚠️  发现孤立补丁（app_id 关联错误）:');
      orphanPatches.forEach(p => {
        console.log(`  - 补丁 ID: ${p.patch_db_id}, patch_id: ${p.patch_id}, app_id: ${p.patch_app_id}`);
      });
    }
    
    db.close();
  });
});
