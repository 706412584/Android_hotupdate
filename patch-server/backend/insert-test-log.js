const db = require('./src/models/database');

async function insertTestLog() {
  try {
    await db.run(`
      INSERT INTO logs (
        user_id, username, action, resource_type, resource_id,
        details, ip_address, user_agent
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `, [
      1,
      'admin',
      'delete_patch',
      'patch',
      123,
      JSON.stringify({ method: 'DELETE', path: '/api/patches/123', message: '删除成功' }),
      '127.0.0.1',
      'Mozilla/5.0 Test'
    ]);
    
    console.log('✅ 测试日志插入成功');
    
    const logs = await db.query('SELECT * FROM logs ORDER BY created_at DESC LIMIT 5');
    console.log('\n最新日志:');
    console.table(logs);
    
  } catch (error) {
    console.error('❌ 插入失败:', error);
  }
  
  process.exit(0);
}

insertTestLog();
