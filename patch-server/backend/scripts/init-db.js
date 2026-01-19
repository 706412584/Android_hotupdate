const bcrypt = require('bcryptjs');
require('dotenv').config();

async function initDatabase() {
  try {
    console.log('🔧 初始化数据库...');

    // 等待更长时间让 database.js 中的表创建完成
    await new Promise(resolve => setTimeout(resolve, 2000));

    const db = require('../src/models/database');

    // 检查是否已有管理员用户
    const admin = await db.get(
      'SELECT id FROM users WHERE role = "admin"'
    );

    if (admin) {
      console.log('✅ 管理员用户已存在');
      await db.close();
      process.exit(0);
      return;
    }

    // 创建默认管理员
    const username = process.env.ADMIN_USERNAME || 'admin';
    const password = process.env.ADMIN_PASSWORD || 'admin123';
    const email = process.env.ADMIN_EMAIL || 'admin@example.com';

    const hashedPassword = await bcrypt.hash(password, 10);

    await db.run(
      'INSERT INTO users (username, password, email, role) VALUES (?, ?, ?, ?)',
      [username, hashedPassword, email, 'admin']
    );

    console.log('✅ 默认管理员创建成功');
    console.log(`   用户名: ${username}`);
    console.log(`   密码: ${password}`);
    console.log('   ⚠️  请尽快修改默认密码！');

    await db.close();
    process.exit(0);

  } catch (error) {
    console.error('❌ 初始化失败:', error);
    process.exit(1);
  }
}

initDatabase();
