/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 闹钟初始化广播接收器
 *
 * <p>该类继承自 {@link BroadcastReceiver}，负责在系统启动完成后
 * （接收 {@code android.intent.action.BOOT_COMPLETED} 广播）
 * 重新注册所有未到期的便签提醒闹钟。</p>
 *
 * <p>设计背景：
 * Android 系统的 {@link AlarmManager} 中注册的闹钟在设备重启后会全部失效。
 * 因此需要在每次系统启动时，通过该接收器查询数据库中所有未来的提醒时间，
 * 并重新向 {@link AlarmManager} 注册对应的 {@link PendingIntent}，
 * 以确保便签提醒功能在重启后仍能正常工作。</p>
 *
 * <p>工作流程：
 * <ol>
 *   <li>接收系统启动完成广播</li>
 *   <li>查询数据库中所有提醒时间晚于当前时间的便签</li>
 *   <li>为每条有效提醒重新注册 {@link AlarmManager} 定时任务</li>
 *   <li>定时触发时由 {@link AlarmReceiver} 接收并处理提醒事件</li>
 * </ol>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see AlarmReceiver
 * @see AlarmAlertActivity
 */
public class AlarmInitReceiver extends BroadcastReceiver {

    /**
     * 数据库查询列投影
     *
     * <p>仅查询便签 ID 和提醒时间两列，
     * 减少不必要的数据传输，提高查询性能。</p>
     */
    private static final String[] PROJECTION = new String[]{
            NoteColumns.ID,           // 便签唯一标识符
            NoteColumns.ALERTED_DATE  // 便签设定的提醒时间戳（毫秒）
    };

    /** PROJECTION 中便签 ID 列的索引 */
    private static final int COLUMN_ID           = 0;

    /** PROJECTION 中提醒时间列的索引 */
    private static final int COLUMN_ALERTED_DATE = 1;

    /**
     * 接收系统广播并重新初始化所有未到期的便签提醒闹钟
     *
     * <p>该方法在接收到系统启动完成广播时被触发，执行以下操作：
     * <ol>
     *   <li>获取当前系统时间作为查询基准</li>
     *   <li>查询数据库中提醒时间晚于当前时间的所有普通便签</li>
     *   <li>为每条记录构建 {@link PendingIntent} 并注册到 {@link AlarmManager}</li>
     *   <li>关闭数据库游标，释放资源</li>
     * </ol>
     * </p>
     *
     * @param context 广播接收器的运行上下文，用于访问系统服务和内容提供者
     * @param intent  触发该接收器的广播 Intent
     *                （通常为 {@code android.intent.action.BOOT_COMPLETED}）
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 获取当前系统时间戳，用于过滤已过期的提醒记录
        long currentDate = System.currentTimeMillis();

        // 查询数据库：筛选提醒时间晚于当前时间且类型为普通便签的记录
        // 查询条件：ALERTED_DATE > 当前时间 AND TYPE = TYPE_NOTE
        Cursor c = context.getContentResolver().query(
                Notes.CONTENT_NOTE_URI,
                PROJECTION,
                NoteColumns.ALERTED_DATE + ">? AND "
                        + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE,
                new String[]{String.valueOf(currentDate)},
                null);

        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    // 读取当前记录的提醒时间戳
                    long alertDate = c.getLong(COLUMN_ALERTED_DATE);

                    // 构建触发提醒的广播 Intent，目标接收器为 AlarmReceiver
                    // 通过 URI 携带便签 ID，以便 AlarmReceiver 识别是哪条便签的提醒
                    Intent sender = new Intent(context, AlarmReceiver.class);
                    sender.setData(ContentUris.withAppendedId(
                            Notes.CONTENT_NOTE_URI, c.getLong(COLUMN_ID)));

                    // 创建 PendingIntent，用于在指定时间由系统触发广播
                    // requestCode 为 0，flags 为 0（使用默认行为）
                    PendingIntent pendingIntent = PendingIntent.getBroadcast(
                            context, 0, sender, 0);

                    // 获取系统闹钟管理服务
                    AlarmManager alermManager = (AlarmManager) context
                            .getSystemService(Context.ALARM_SERVICE);

                    // 注册精确闹钟：RTC_WAKEUP 模式确保在指定时间唤醒设备触发提醒
                    // 即使设备处于休眠状态也会被唤醒执行
                    alermManager.set(AlarmManager.RTC_WAKEUP, alertDate, pendingIntent);

                } while (c.moveToNext());
            }
            // 关闭游标，释放数据库连接资源，防止内存泄漏
            c.close();
        }
    }
}