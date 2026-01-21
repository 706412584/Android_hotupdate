const sqlite3 = require('sqlite3').verbose();
const path = require('path');

const DB_PATH = path.join(__dirname, 'backups/database-2026-01-19.db');

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
  console.log('='.repeat(120));
  apps.forEach(app => {
    console.log(`数据库ID: ${app.id.toString().padEnd(3)} | app_id: ${app.app_id.padEnd(30)} | 名称: ${app.app_name.padEnd(20)} | 包名: ${app.package_name.padEnd(30)} | 状态: ${app.status}`);
  });
  console.log('\n');
  
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
    console.log('='.repeat(120));
    patches.forEach(patch => {
      console.log(`\n补丁数据库ID: ${patch.patch_db_id} | patch_id: ${patch.patch_id}`);
      console.log(`  版本: ${patch.base_version} → ${patch.version} | 状态: ${patch.patch_status}`);
      console.log(`  补丁的 app_id 字段值 (数字): ${patch.patch_app_id}`);
      if (patch.app_name) {
        console.log(`  ✅ 关联到应用: ${patch.app_name} (包名: ${patch.package_name})`);
        console.log(`     应用数据库ID: ${patch.app_db_id} | 应用 app_id: ${patch.app_id}`);
      } else {
        console.log(`  ❌ 未找到关联的应用！app_id=${patch.patch_app_id} 在 apps 表中不存在`);
      }
      console.log(`  创建时间: ${patch.created_at}`);
      console.log('-'.repeat(120));
    });
    
    // 检查是否有孤立的补丁（app_id 不匹配）
    const orphanPatches = patches.filter(p => !p.app_name);
    if (orphanPatches.length > 0) {
      console.log('\n⚠️  发现孤立补丁（app_id 关联错误）:');
      console.log('='.repeat(120));
      orphanPatches.forEach(p => {
        console.log(`  补丁数据库ID: ${p.patch_db_id} | patch_id: ${p.patch_id}`);
        console.log(`  版本: ${p.base_version} → ${p.version}`);
        console.log(`  错误的 app_id: ${p.patch_app_id} (在 apps 表中找不到这个 ID)`);
        console.log(`  创建时间: ${p.created_at}`);
        console.log('');
      });
      
      console.log('\n💡 修复建议:');
      console.log('  1. 确认这些补丁应该属于哪个应用');
      console.log('  2. 使用以下 SQL 更新补丁的 app_id:');
      console.log('     UPDATE patches SET app_id = <正确的应用ID> WHERE id = <补丁ID>;');
    }
    
    db.close();
  });
});
