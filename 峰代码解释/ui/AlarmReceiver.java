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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 便签闹钟触发广播接收器
 *
 * <p>该类继承自 {@link BroadcastReceiver}，作为便签提醒系统的中间调度层，
 * 负责接收由 {@link android.app.AlarmManager} 在指定时间触发的闹钟广播，
 * 并将其转发给 {@link AlarmAlertActivity} 以展示提醒界面。</p>
 *
 * <p>在整个便签提醒流程中，各组件的职责分工如下：
 * <ul>
 *   <li>{@link AlarmInitReceiver}：系统启动后重新注册所有未到期的闹钟</li>
 *   <li>{@link android.app.AlarmManager}：在指定时间触发广播</li>
 *   <li>{@link AlarmReceiver}（本类）：接收广播并启动提醒界面</li>
 *   <li>{@link AlarmAlertActivity}：向用户展示提醒对话框并播放铃声</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * 使用 {@link BroadcastReceiver} 而非直接由 AlarmManager 启动 Activity，
 * 符合 Android 组件化设计规范，将"接收系统事件"与"展示 UI"两个职责解耦，
 * 提高代码的可维护性与可测试性。</p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see AlarmInitReceiver
 * @see AlarmAlertActivity
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * 接收闹钟触发广播并启动便签提醒界面
     *
     * <p>当 {@link android.app.AlarmManager} 在预设的提醒时间到达时，
     * 该方法被系统回调，执行以下操作：
     * <ol>
     *   <li>将 Intent 的目标组件重定向至 {@link AlarmAlertActivity}</li>
     *   <li>添加 {@link Intent#FLAG_ACTIVITY_NEW_TASK} 标志，
     *       允许在非 Activity 上下文（BroadcastReceiver）中启动新 Activity</li>
     *   <li>启动 {@link AlarmAlertActivity} 向用户展示提醒界面</li>
     * </ol>
     * </p>
     *
     * <p><strong>注意：</strong>
     * {@link Intent#FLAG_ACTIVITY_NEW_TASK} 标志是必须的，
     * 因为 {@link BroadcastReceiver} 的上下文不属于任何 Activity 任务栈，
     * 在此上下文中启动 Activity 必须为其指定一个新的任务栈，
     * 否则将抛出 {@link android.util.AndroidRuntimeException}。</p>
     *
     * @param context 广播接收器的运行上下文，用于启动 Activity
     * @param intent  由 {@link android.app.AlarmManager} 触发的广播 Intent，
     *                其中携带了便签 ID（通过 URI 数据段传递），
     *                供 {@link AlarmAlertActivity} 加载对应便签内容
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 将 Intent 的目标组件切换为提醒界面 Activity
        // 原 Intent 中携带的便签 URI 数据将一并传递给 AlarmAlertActivity
        intent.setClass(context, AlarmAlertActivity.class);

        // 添加 FLAG_ACTIVITY_NEW_TASK 标志
        // 在 BroadcastReceiver 上下文中启动 Activity 时该标志为必要条件
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 启动便签提醒界面，展示提醒对话框并播放闹钟铃声
        context.startActivity(intent);
    }
}