// 测试日志记录功能
const db = require('./src/models/database');

async function testLogger() {
  console.log('开始测试日志功能...\n');
  
  try {
    // 查询日志表结构
    const tableInfo = await db.query("PRAGMA table_info(logs)");
    console.log('日志表结构:');
    console.table(tableInfo);
    
    // 查询现有日志数量
    const count = await db.get('SELECT COUNT(*) as total FROM logs');
    console.log(`\n当前日志总数: ${count.total}`);
    
    // 查询最近的 5 条日志
    const recentLogs = await db.query(`
      SELECT id, username, action, resource_type, resource_id, created_at
      FROM logs
      ORDER BY created_at DESC
      LIMIT 5
    `);
    
    if (recentLogs.length > 0) {
      console.log('\n最近的 5 条日志:');
      console.table(recentLogs);
    } else {
      console.log('\n暂无日志记录');
    }
    
    // 按操作类型统计
    const stats = await db.query(`
      SELECT action, COUNT(*) as count
      FROM logs
      GROUP BY action
      ORDER BY count DESC
    `);
    
    if (stats.length > 0) {
      console.log('\n操作类型统计:');
      console.table(stats);
    }
    
  } catch (error) {
    console.error('测试失败:', error);
  }
  
  process.exit(0);
}

testLogger();
