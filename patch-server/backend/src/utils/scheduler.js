const cron = require('node-cron');
const { exec } = require('child_process');
const path = require('path');
const fs = require('fs').promises;

class Scheduler {
  constructor() {
    this.tasks = new Map();
  }

  /**
   * 添加定时任务
   * @param {string} name - 任务名称
   * @param {string} schedule - cron 表达式
   * @param {Function} task - 任务函数
   */
  addTask(name, schedule, task) {
    if (this.tasks.has(name)) {
      console.warn(`任务 ${name} 已存在，将被覆盖`);
      this.removeTask(name);
    }

    const cronTask = cron.schedule(schedule, async () => {
      console.log(`[${new Date().toISOString()}] 开始执行任务: ${name}`);
      try {
        await task();
        console.log(`[${new Date().toISOString()}] 任务完成: ${name}`);
      } catch (error) {
        console.error(`[${new Date().toISOString()}] 任务失败: ${name}`, error);
      }
    }, {
      scheduled: false
    });

    this.tasks.set(name, {
      schedule,
      task: cronTask,
      enabled: false
    });

    console.log(`✅ 任务已添加: ${name} (${schedule})`);
  }

  /**
   * 启动任务
   * @param {string} name - 任务名称
   */
  startTask(name) {
    const taskInfo = this.tasks.get(name);
    if (!taskInfo) {
      throw new Error(`任务不存在: ${name}`);
    }

    if (taskInfo.enabled) {
      console.warn(`任务已在运行: ${name}`);
      return;
    }

    taskInfo.task.start();
    taskInfo.enabled = true;
    console.log(`▶️  任务已启动: ${name}`);
  }

  /**
   * 停止任务
   * @param {string} name - 任务名称
   */
  stopTask(name) {
    const taskInfo = this.tasks.get(name);
    if (!taskInfo) {
      throw new Error(`任务不存在: ${name}`);
    }

    if (!taskInfo.enabled) {
      console.warn(`任务未运行: ${name}`);
      return;
    }

    taskInfo.task.stop();
    taskInfo.enabled = false;
    console.log(`⏸️  任务已停止: ${name}`);
  }

  /**
   * 移除任务
   * @param {string} name - 任务名称
   */
  removeTask(name) {
    const taskInfo = this.tasks.get(name);
    if (!taskInfo) {
      return;
    }

    if (taskInfo.enabled) {
      taskInfo.task.stop();
    }

    taskInfo.task.destroy();
    this.tasks.delete(name);
    console.log(`🗑️  任务已移除: ${name}`);
  }

  /**
   * 获取所有任务
   */
  getTasks() {
    const tasks = [];
    for (const [name, info] of this.tasks.entries()) {
      tasks.push({
        name,
        schedule: info.schedule,
        enabled: info.enabled
      });
    }
    return tasks;
  }

  /**
   * 启动所有任务
   */
  startAll() {
    for (const name of this.tasks.keys()) {
      try {
        this.startTask(name);
      } catch (error) {
        console.error(`启动任务失败: ${name}`, error);
      }
    }
  }

  /**
   * 停止所有任务
   */
  stopAll() {
    for (const name of this.tasks.keys()) {
      try {
        this.stopTask(name);
      } catch (error) {
        console.error(`停止任务失败: ${name}`, error);
      }
    }
  }
}

// 创建全局调度器实例
const scheduler = new Scheduler();

// 备份任务
async function backupTask() {
  return new Promise((resolve, reject) => {
    const backupScript = path.join(__dirname, '../scripts/backup.js');
    
    exec(`node "${backupScript}"`, (error, stdout, stderr) => {
      if (error) {
        console.error('备份失败:', error);
        console.error('stderr:', stderr);
        reject(error);
        return;
      }
      
      console.log('备份输出:', stdout);
      resolve();
    });
  });
}

// 日志清理任务
async function cleanLogsTask() {
  const db = require('../models/database');
  const daysToKeep = parseInt(process.env.LOG_RETENTION_DAYS) || 30;
  
  try {
    const result = await db.run(
      `DELETE FROM logs WHERE created_at < datetime('now', '-${daysToKeep} days')`
    );
    
    console.log(`✅ 已清理 ${result.changes} 条旧日志（保留 ${daysToKeep} 天）`);
  } catch (error) {
    console.error('清理日志失败:', error);
    throw error;
  }
}

// 清理旧下载记录任务
async function cleanDownloadsTask() {
  const db = require('../models/database');
  const daysToKeep = parseInt(process.env.DOWNLOAD_RETENTION_DAYS) || 90;
  
  try {
    const result = await db.run(
      `DELETE FROM downloads WHERE created_at < datetime('now', '-${daysToKeep} days')`
    );
    
    console.log(`✅ 已清理 ${result.changes} 条旧下载记录（保留 ${daysToKeep} 天）`);
  } catch (error) {
    console.error('清理下载记录失败:', error);
    throw error;
  }
}

// 初始化定时任务
function initScheduler() {
  // 每天凌晨 2 点执行备份
  const backupSchedule = process.env.BACKUP_SCHEDULE || '0 2 * * *';
  scheduler.addTask('backup', backupSchedule, backupTask);
  
  // 每周日凌晨 3 点清理日志
  const cleanLogsSchedule = process.env.CLEAN_LOGS_SCHEDULE || '0 3 * * 0';
  scheduler.addTask('clean-logs', cleanLogsSchedule, cleanLogsTask);
  
  // 每月 1 号凌晨 4 点清理下载记录
  const cleanDownloadsSchedule = process.env.CLEAN_DOWNLOADS_SCHEDULE || '0 4 1 * *';
  scheduler.addTask('clean-downloads', cleanDownloadsSchedule, cleanDownloadsTask);
  
  // 如果启用了自动备份，则启动任务
  if (process.env.AUTO_BACKUP === 'true') {
    scheduler.startTask('backup');
    console.log('✅ 自动备份已启用');
  }
  
  // 如果启用了自动清理，则启动任务
  if (process.env.AUTO_CLEAN === 'true') {
    scheduler.startTask('clean-logs');
    scheduler.startTask('clean-downloads');
    console.log('✅ 自动清理已启用');
  }
}

module.exports = {
  scheduler,
  initScheduler,
  backupTask,
  cleanLogsTask,
  cleanDownloadsTask
};
