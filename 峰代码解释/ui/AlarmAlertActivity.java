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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;

import java.io.IOException;

/**
 * 便签闹钟提醒界面
 *
 * <p>当便签设定的提醒时间到达时，该 Activity 负责：
 * <ul>
 *   <li>在锁屏状态下唤醒屏幕并展示提醒对话框</li>
 *   <li>播放系统默认闹钟铃声提醒用户</li>
 *   <li>提供"确认"与"进入编辑"两种响应操作</li>
 * </ul>
 * </p>
 *
 * <p>生命周期说明：
 * <ul>
 *   <li>实现 {@link OnClickListener} 处理对话框按钮点击事件</li>
 *   <li>实现 {@link OnDismissListener} 处理对话框关闭事件，确保铃声停止</li>
 *   <li>Activity 关闭时自动释放 {@link MediaPlayer} 资源，防止内存泄漏</li>
 * </ul>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see NoteEditActivity
 */
public class AlarmAlertActivity extends Activity
        implements OnClickListener, OnDismissListener {

    /** 当前提醒所关联的便签 ID */
    private long mNoteId;

    /** 便签摘要内容，用于在提醒对话框中向用户展示便签预览 */
    private String mSnippet;

    /**
     * 对话框中显示的便签摘要最大字符长度
     *
     * <p>若摘要超过该长度，则截断并在末尾追加省略提示字符串，
     * 避免对话框内容过长影响用户体验。</p>
     */
    private static final int SNIPPET_PREW_MAX_LEN = 60;

    /** 闹钟铃声播放器，负责播放系统默认闹钟音效 */
    MediaPlayer mPlayer;

    /**
     * Activity 创建时的初始化入口
     *
     * <p>主要完成以下初始化工作：
     * <ol>
     *   <li>隐藏标题栏，设置锁屏显示和屏幕唤醒标志</li>
     *   <li>从 Intent 中解析便签 ID，并查询对应的摘要内容</li>
     *   <li>初始化 MediaPlayer 并判断便签是否仍然有效</li>
     *   <li>若便签有效则显示提醒对话框并播放铃声，否则直接关闭页面</li>
     * </ol>
     * </p>
     *
     * @param savedInstanceState Activity 重建时的状态保存数据，首次创建时为 null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 隐藏窗口标题栏，使提醒界面更简洁
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final Window win = getWindow();
        // 设置锁屏状态下也可显示该 Activity
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // 若屏幕当前处于关闭状态，则强制点亮屏幕以展示提醒
        if (!isScreenOn()) {
            win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR);
        }

        Intent intent = getIntent();

        try {
            // 从 Intent 携带的 URI 路径段中提取便签 ID（路径格式：.../notes/{id}）
            mNoteId = Long.valueOf(intent.getData().getPathSegments().get(1));

            // 根据便签 ID 查询数据库获取摘要内容
            mSnippet = DataUtils.getSnippetById(this.getContentResolver(), mNoteId);

            // 若摘要超过最大显示长度，则截断并追加省略提示
            mSnippet = mSnippet.length() > SNIPPET_PREW_MAX_LEN
                    ? mSnippet.substring(0, SNIPPET_PREW_MAX_LEN)
                        + getResources().getString(R.string.notelist_string_info)
                    : mSnippet;
        } catch (IllegalArgumentException e) {
            // 便签 ID 不合法或便签不存在，终止 Activity 初始化
            e.printStackTrace();
            return;
        }

        // 初始化媒体播放器
        mPlayer = new MediaPlayer();

        // 验证便签是否仍在数据库中可见（未被删除或移入回收站）
        if (DataUtils.visibleInNoteDatabase(getContentResolver(), mNoteId, Notes.TYPE_NOTE)) {
            // 便签有效：显示提醒对话框并播放闹钟铃声
            showActionDialog();
            playAlarmSound();
        } else {
            // 便签已不可见（被删除），直接关闭提醒界面
            finish();
        }
    }

    /**
     * 检测屏幕当前是否处于点亮状态
     *
     * <p>通过 {@link PowerManager} 系统服务获取屏幕状态，
     * 用于决定是否需要在 Activity 启动时强制唤醒屏幕。</p>
     *
     * @return 屏幕已点亮返回 {@code true}；屏幕处于关闭/息屏状态返回 {@code false}
     */
    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isScreenOn();
    }

    /**
     * 播放系统默认闹钟铃声
     *
     * <p>获取系统当前设置的默认闹钟铃声 URI，并通过 {@link MediaPlayer} 循环播放。
     * 播放前会检查系统静音模式设置，以决定使用的音频流类型：
     * <ul>
     *   <li>若闹钟音频流受静音模式影响，则遵循当前铃声模式设置</li>
     *   <li>否则强制使用 {@link AudioManager#STREAM_ALARM} 音频流，
     *       确保闹钟铃声在静音模式下仍可播放</li>
     * </ul>
     * </p>
     */
    private void playAlarmSound() {
        // 获取系统默认闹钟铃声的 URI
        Uri url = RingtoneManager.getActualDefaultRingtoneUri(
                this, RingtoneManager.TYPE_ALARM);

        // 读取系统静音模式下受影响的音频流掩码
        int silentModeStreams = Settings.System.getInt(getContentResolver(),
                Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

        // 根据静音模式掩码判断闹钟音频流是否受影响，设置对应的音频流类型
        if ((silentModeStreams & (1 << AudioManager.STREAM_ALARM)) != 0) {
            // 闹钟音频流受静音模式影响，使用当前铃声模式的音频流
            mPlayer.setAudioStreamType(silentModeStreams);
        } else {
            // 闹钟音频流不受静音模式影响，强制使用闹钟专用音频流
            mPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        }

        try {
            // 设置铃声数据源并准备播放
            mPlayer.setDataSource(this, url);
            mPlayer.prepare();
            // 设置循环播放，确保在用户响应前铃声持续响起
            mPlayer.setLooping(true);
            mPlayer.start();
        } catch (IllegalArgumentException e) {
            // 非法参数异常：URI 参数无效
            e.printStackTrace();
        } catch (SecurityException e) {
            // 安全异常：应用缺少访问铃声资源的权限
            e.printStackTrace();
        } catch (IllegalStateException e) {
            // 非法状态异常：MediaPlayer 状态机异常，调用顺序不正确
            e.printStackTrace();
        } catch (IOException e) {
            // IO 异常：铃声资源文件读取失败
            e.printStackTrace();
        }
    }

    /**
     * 显示便签提醒操作对话框
     *
     * <p>对话框展示便签摘要内容，并根据屏幕状态提供不同的操作选项：
     * <ul>
     *   <li>屏幕点亮状态：显示"确认"和"进入编辑"两个按钮</li>
     *   <li>锁屏/息屏状态：仅显示"确认"按钮，避免误触进入编辑页面</li>
     * </ul>
     * </p>
     */
    private void showActionDialog() {
        AlertDialog.Builder dialog = new AlertDialog.Builder(this);
        // 设置对话框标题为应用名称
        dialog.setTitle(R.string.app_name);
        // 设置对话框消息内容为便签摘要
        dialog.setMessage(mSnippet);
        // 设置确认按钮（正向操作）：点击后关闭对话框
        dialog.setPositiveButton(R.string.notealert_ok, this);

        // 仅在屏幕点亮时显示"进入编辑"按钮，锁屏下不提供该选项
        if (isScreenOn()) {
            dialog.setNegativeButton(R.string.notealert_enter, this);
        }

        // 显示对话框并注册关闭监听器，确保对话框关闭时停止铃声
        dialog.show().setOnDismissListener(this);
    }

    /**
     * 处理对话框按钮点击事件
     *
     * <p>点击"进入编辑"按钮（负向按钮）时，跳转到便签编辑页面。
     * 点击"确认"按钮（正向按钮）时，对话框自动关闭并触发
     * {@link #onDismiss(DialogInterface)} 回调。</p>
     *
     * @param dialog 触发点击事件的对话框实例
     * @param which  被点击的按钮标识，对应 {@link DialogInterface} 中的按钮常量
     */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                // 点击"进入编辑"按钮：跳转到便签编辑界面
                Intent intent = new Intent(this, NoteEditActivity.class);
                intent.setAction(Intent.ACTION_VIEW);
                // 传递便签 ID 以便编辑页面加载对应便签内容
                intent.putExtra(Intent.EXTRA_UID, mNoteId);
                startActivity(intent);
                break;
            default:
                // 其他按钮（如确认按钮）：无需额外处理，对话框关闭后由 onDismiss 接管
                break;
        }
    }

    /**
     * 处理对话框关闭事件
     *
     * <p>无论用户通过何种方式关闭对话框（点击按钮、返回键等），
     * 该方法均会被调用，负责停止闹钟铃声并关闭当前 Activity。</p>
     *
     * @param dialog 被关闭的对话框实例
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        // 停止并释放铃声播放器资源
        stopAlarmSound();
        // 关闭提醒 Activity
        finish();
    }

    /**
     * 停止闹钟铃声并释放 MediaPlayer 资源
     *
     * <p>为防止内存泄漏，在停止播放后依次调用
     * {@link MediaPlayer#stop()}、{@link MediaPlayer#release()} 并将引用置空。</p>
     */
    private void stopAlarmSound() {
        if (mPlayer != null) {
            // 停止铃声播放
            mPlayer.stop();
            // 释放 MediaPlayer 占用的系统资源（音频焦点、解码器等）
            mPlayer.release();
            // 将引用置空，帮助 GC 回收内存
            mPlayer = null;
        }
    }
}