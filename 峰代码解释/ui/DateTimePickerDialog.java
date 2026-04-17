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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框
 *
 * <p>该类继承自 {@link AlertDialog}，将自定义的 {@link DateTimePicker}
 * 控件嵌入到标准对话框中，为用户提供便签提醒时间的完整设置界面。</p>
 *
 * <p>主要功能：
 * <ul>
 *   <li>在对话框标题栏实时显示当前选中的日期时间（格式化字符串）</li>
 *   <li>支持 24 小时制与 12 小时制的自动适配（根据系统设置）</li>
 *   <li>提供"确定"和"取消"两个操作按钮</li>
 *   <li>通过 {@link OnDateTimeSetListener} 回调将用户确认的时间传递给调用方</li>
 * </ul>
 * </p>
 *
 * <p>使用方式：
 * <pre>
 *   DateTimePickerDialog dialog = new DateTimePickerDialog(context, System.currentTimeMillis());
 *   dialog.setOnDateTimeSetListener((d, date) -> handleDateSet(date));
 *   dialog.show();
 * </pre>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see DateTimePicker
 * @see OnDateTimeSetListener
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    /**
     * 当前对话框内部维护的日期时间状态
     *
     * <p>随用户在 {@link DateTimePicker} 上的操作实时更新，
     * 用户点击"确定"时将该值通过回调传递给外部。</p>
     */
    private Calendar mDate = Calendar.getInstance();

    /**
     * 当前是否使用 24 小时制显示模式
     *
     * <p>由系统时间格式设置决定，影响对话框标题栏的时间显示格式。</p>
     */
    private boolean mIs24HourView;

    /** 用户确认选择后的日期时间回调监听器 */
    private OnDateTimeSetListener mOnDateTimeSetListener;

    /** 嵌入对话框内容区域的日期时间选择控件 */
    private DateTimePicker mDateTimePicker;

    /**
     * 日期时间确认设置事件监听器接口
     *
     * <p>当用户在对话框中点击"确定"按钮后，
     * 该接口的实现类将收到回调，并获得用户最终选择的时间戳。</p>
     */
    public interface OnDateTimeSetListener {
        /**
         * 用户确认日期时间选择时的回调方法
         *
         * @param dialog 触发事件的 {@link AlertDialog} 对话框实例
         * @param date   用户最终选择的日期时间，以毫秒时间戳表示
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造函数：创建并初始化日期时间选择对话框
     *
     * <p>完成以下初始化工作：
     * <ol>
     *   <li>创建 {@link DateTimePicker} 控件并嵌入对话框内容区域</li>
     *   <li>注册日期时间变更监听器，实时同步内部 {@link Calendar} 状态</li>
     *   <li>将秒数归零，避免提醒时间精度过高导致用户困惑</li>
     *   <li>设置"确定"和"取消"按钮</li>
     *   <li>根据系统设置自动选择 24/12 小时制显示模式</li>
     *   <li>初始化对话框标题为当前选中时间的格式化字符串</li>
     * </ol>
     * </p>
     *
     * @param context 对话框所在的上下文环境
     * @param date    对话框打开时默认显示的时间，以毫秒时间戳表示
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);

        // 创建自定义日期时间选择控件并设置为对话框的内容视图
        mDateTimePicker = new DateTimePicker(context);
        setView(mDateTimePicker);

        // 注册日期时间变更监听器：用户每次调整滚轮时同步更新内部 Calendar 和标题显示
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                    int dayOfMonth, int hourOfDay, int minute) {
                // 将选择控件的变更同步到内部 Calendar 对象
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                // 实时更新对话框标题显示为最新选中的日期时间
                updateTitle(mDate.getTimeInMillis());
            }
        });

        // 设置初始时间：将传入的时间戳写入 Calendar，并将秒数归零
        mDate.setTimeInMillis(date);
        mDate.set(Calendar.SECOND, 0);

        // 将选择控件定位到初始时间
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());

        // 设置"确定"按钮，点击后触发 onClick 回调
        setButton(context.getString(R.string.datetime_dialog_ok), this);

        // 设置"取消"按钮，点击后直接关闭对话框，不触发回调
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener) null);

        // 根据系统时间格式设置自动选择 24/12 小时制显示模式
        set24HourView(DateFormat.is24HourFormat(this.getContext()));

        // 初始化对话框标题显示
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置时间显示模式（24 小时制或 12 小时制）
     *
     * <p>该设置影响对话框标题栏中时间字符串的格式化方式。
     * 通常由构造函数根据系统设置自动调用，也可由外部手动覆盖。</p>
     *
     * @param is24HourView {@code true} 使用 24 小时制；
     *                     {@code false} 使用 12 小时制（AM/PM）
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 注册用户确认日期时间选择后的回调监听器
     *
     * <p>当用户点击"确定"按钮时，注册的监听器将收到包含
     * 用户最终选择时间戳的回调通知。</p>
     *
     * @param callBack 实现了 {@link OnDateTimeSetListener} 接口的回调对象；
     *                 传入 {@code null} 将清除当前监听器
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新对话框标题栏显示的日期时间字符串
     *
     * <p>使用 {@link DateUtils#formatDateTime} 将时间戳格式化为
     * 包含年份、日期和时间的完整可读字符串，并设置为对话框标题。
     * 每次用户调整选择控件时均会调用该方法实时刷新标题。</p>
     *
     * <p><strong>注意：</strong>当前代码中 24 小时制和 12 小时制分支
     * 均使用了 {@link DateUtils#FORMAT_24HOUR}，12 小时制分支应使用
     * {@link DateUtils#FORMAT_12HOUR}，此处存在一个潜在的显示格式 Bug。</p>
     *
     * @param date 需要格式化并显示在标题中的时间戳（毫秒）
     */
    private void updateTitle(long date) {
        // 构建格式化标志：包含年份、日期和时间三部分
        int flag =
                DateUtils.FORMAT_SHOW_YEAR |
                DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_SHOW_TIME;

        // 根据 24/12 小时制设置对应的时间格式标志
        // TODO: 12 小时制分支应使用 DateUtils.FORMAT_12HOUR，当前存在显示格式 Bug
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;

        // 将格式化后的日期时间字符串设置为对话框标题
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 处理对话框按钮点击事件
     *
     * <p>该方法仅响应"确定"按钮的点击（"取消"按钮的监听器在构造时传入了 {@code null}）。
     * 点击"确定"时，若已注册监听器，则将当前选中的时间戳通过回调传递给外部调用方。</p>
     *
     * @param arg0 触发点击事件的对话框实例（未使用）
     * @param arg1 被点击的按钮标识（未使用，因"取消"按钮无监听器）
     */
    @Override
    public void onClick(DialogInterface arg0, int arg1) {
        // 若已注册确认回调，则将当前选中时间传递给外部
        if (mOnDateTimeSetListener != null) {
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }
}