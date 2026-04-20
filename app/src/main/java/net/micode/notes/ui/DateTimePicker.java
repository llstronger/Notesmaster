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

import java.text.DateFormatSymbols;
import java.util.Calendar;

import net.micode.notes.R;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;

/**
 * 自定义日期时间选择控件
 *
 * <p>该类继承自 {@link FrameLayout}，是一个组合式自定义 View，
 * 通过四个 {@link NumberPicker} 滚轮控件分别控制日期、小时、分钟和上下午，
 * 为用户提供直观的日期时间选择交互界面。</p>
 *
 * <p>主要功能：
 * <ul>
 *   <li>支持 24 小时制与 12 小时制（AM/PM）两种时间显示模式的切换</li>
 *   <li>日期选择器显示以当前日期为中心的前后共 7 天（一周范围）</li>
 *   <li>小时与分钟滚轮支持循环滚动，跨越边界时自动进位/退位</li>
 *   <li>通过 {@link OnDateTimeChangedListener} 回调通知外部日期时间的变更</li>
 * </ul>
 * </p>
 *
 * <p>控件组成：
 * <ul>
 *   <li>{@link #mDateSpinner}：日期选择滚轮，显示"月.日 星期"格式</li>
 *   <li>{@link #mHourSpinner}：小时选择滚轮</li>
 *   <li>{@link #mMinuteSpinner}：分钟选择滚轮</li>
 *   <li>{@link #mAmPmSpinner}：上下午选择滚轮（仅 12 小时制下可见）</li>
 * </ul>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see OnDateTimeChangedListener
 */
public class DateTimePicker extends FrameLayout {

    /** 控件默认启用状态：默认为启用 */
    private static final boolean DEFAULT_ENABLE_STATE = true;

    /** 半天的小时数（12 小时制中使用） */
    private static final int HOURS_IN_HALF_DAY = 12;

    /** 全天的小时数（24 小时制中使用） */
    private static final int HOURS_IN_ALL_DAY = 24;

    /** 日期选择器显示的天数范围（一周 7 天） */
    private static final int DAYS_IN_ALL_WEEK = 7;

    /** 日期选择滚轮的最小值索引 */
    private static final int DATE_SPINNER_MIN_VAL = 0;

    /** 日期选择滚轮的最大值索引（共 7 个选项，索引 0~6） */
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1;

    /** 24 小时制下小时滚轮的最小值 */
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;

    /** 24 小时制下小时滚轮的最大值 */
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;

    /** 12 小时制下小时滚轮的最小值 */
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;

    /** 12 小时制下小时滚轮的最大值 */
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;

    /** 分钟选择滚轮的最小值 */
    private static final int MINUT_SPINNER_MIN_VAL = 0;

    /** 分钟选择滚轮的最大值 */
    private static final int MINUT_SPINNER_MAX_VAL = 59;

    /** AM/PM 选择滚轮的最小值索引（对应 AM） */
    private static final int AMPM_SPINNER_MIN_VAL = 0;

    /** AM/PM 选择滚轮的最大值索引（对应 PM） */
    private static final int AMPM_SPINNER_MAX_VAL = 1;

    /** 日期选择滚轮控件 */
    private final NumberPicker mDateSpinner;

    /** 小时选择滚轮控件 */
    private final NumberPicker mHourSpinner;

    /** 分钟选择滚轮控件 */
    private final NumberPicker mMinuteSpinner;

    /** 上下午选择滚轮控件（仅 12 小时制下可见） */
    private final NumberPicker mAmPmSpinner;

    /** 当前选中的日期时间，使用 {@link Calendar} 维护完整的时间状态 */
    private Calendar mDate;

    /**
     * 日期选择滚轮显示的文本数组
     * 存储以当前日期为中心、前后各若干天的日期字符串（格式：MM.dd EEEE）
     */
    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];

    /** 当前是否为上午（AM）状态，仅在 12 小时制下有效 */
    private boolean mIsAm;

    /** 当前是否为 24 小时制显示模式 */
    private boolean mIs24HourView;

    /** 当前控件的启用状态 */
    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;

    /**
     * 初始化标志位
     *
     * <p>在构造函数执行期间置为 {@code true}，
     * 防止初始化过程中重复触发日期时间变更回调，
     * 初始化完成后置为 {@code false}。</p>
     */
    private boolean mInitialising;

    /** 日期时间变更事件的外部监听器 */
    private OnDateTimeChangedListener mOnDateTimeChangedListener;

    /**
     * 日期选择滚轮值变化监听器
     *
     * <p>当用户滚动日期选择器时触发，根据新旧值的差值
     * 更新内部 {@link Calendar} 的日期，并刷新日期控件显示。</p>
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener =
            new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 根据滚轮值的变化量调整内部日期（正数为向后，负数为向前）
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            // 刷新日期选择器显示的文本内容
            updateDateControl();
            // 通知外部日期时间已变更
            onDateTimeChanged();
        }
    };

    /**
     * 小时选择滚轮值变化监听器
     *
     * <p>处理小时滚动的边界情况：
     * <ul>
     *   <li>12 小时制：在 11→12 或 12→11 的边界处切换 AM/PM 状态，
     *       并在 PM 的 11→12 或 AM 的 12→11 时自动进位/退位一天</li>
     *   <li>24 小时制：在 23→0 或 0→23 的边界处自动进位/退位一天</li>
     * </ul>
     * </p>
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener =
            new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false;
            Calendar cal = Calendar.getInstance();

            if (!mIs24HourView) {
                // ---- 12 小时制边界处理 ----
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1
                        && newVal == HOURS_IN_HALF_DAY) {
                    // PM 状态下从 11 滚动到 12：跨越午夜，日期加一天
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (mIsAm && oldVal == HOURS_IN_HALF_DAY
                        && newVal == HOURS_IN_HALF_DAY - 1) {
                    // AM 状态下从 12 滚动到 11：跨越午夜，日期减一天
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }

                // 在 11↔12 边界处切换 AM/PM 状态并更新上下午控件
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY
                        || oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    mIsAm = !mIsAm;
                    updateAmPmControl();
                }
            } else {
                // ---- 24 小时制边界处理 ----
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    // 从 23 滚动到 0：跨越午夜，日期加一天
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                } else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    // 从 0 滚动到 23：跨越午夜，日期减一天
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }

            // 将 12 小时制的小时值转换为 24 小时制并更新内部日期
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY
                    + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            onDateTimeChanged();

            // 若发生了日期跨越，同步更新年月日
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟选择滚轮值变化监听器
     *
     * <p>处理分钟滚动的边界情况：
     * <ul>
     *   <li>从 59 滚动到 0：小时加一，并检查是否需要更新日期和 AM/PM 状态</li>
     *   <li>从 0 滚动到 59：小时减一，并检查是否需要更新日期和 AM/PM 状态</li>
     * </ul>
     * </p>
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener =
            new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0;

            if (oldVal == maxValue && newVal == minValue) {
                // 分钟从 59 进位到 0：小时需要加一
                offset += 1;
            } else if (oldVal == minValue && newVal == maxValue) {
                // 分钟从 0 退位到 59：小时需要减一
                offset -= 1;
            }

            if (offset != 0) {
                // 更新小时并同步刷新日期控件与小时滚轮显示
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour());
                updateDateControl();

                // 根据新的小时值更新 AM/PM 状态
                int newHour = getCurrentHourOfDay();
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false;
                    updateAmPmControl();
                } else {
                    mIsAm = true;
                    updateAmPmControl();
                }
            }

            // 更新内部日期的分钟字段并通知变更
            mDate.set(Calendar.MINUTE, newVal);
            onDateTimeChanged();
        }
    };

    /**
     * AM/PM 选择滚轮值变化监听器
     *
     * <p>当用户切换上下午时，内部时间相应地增加或减少 12 小时，
     * 并刷新 AM/PM 控件显示状态。</p>
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener =
            new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 切换 AM/PM 状态
            mIsAm = !mIsAm;
            if (mIsAm) {
                // 切换为 AM：时间减少 12 小时
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                // 切换为 PM：时间增加 12 小时
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            updateAmPmControl();
            onDateTimeChanged();
        }
    };

    /**
     * 日期时间变更事件监听器接口
     *
     * <p>当用户通过控件修改日期或时间时，该接口的实现类将收到回调通知，
     * 回调参数包含完整的年、月、日、时、分信息。</p>
     */
    public interface OnDateTimeChangedListener {
        /**
         * 日期时间发生变更时的回调方法
         *
         * @param view       触发事件的 {@link DateTimePicker} 控件实例
         * @param year       当前选中的年份
         * @param month      当前选中的月份（0 表示一月，11 表示十二月）
         * @param dayOfMonth 当前选中的日（1~31）
         * @param hourOfDay  当前选中的小时（24 小时制，0~23）
         * @param minute     当前选中的分钟（0~59）
         */
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                int dayOfMonth, int hourOfDay, int minute);
    }

    /**
     * 构造函数：使用当前系统时间和系统默认时间格式初始化控件
     *
     * @param context 控件所在的上下文环境
     */
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    /**
     * 构造函数：使用指定时间戳和系统默认时间格式初始化控件
     *
     * @param context 控件所在的上下文环境
     * @param date    初始显示的时间，以毫秒时间戳表示
     */
    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /**
     * 构造函数：使用指定时间戳和指定时间格式初始化控件（完整初始化入口）
     *
     * <p>完成以下初始化工作：
     * <ol>
     *   <li>初始化内部 {@link Calendar} 和 AM/PM 状态</li>
     *   <li>加载布局文件并绑定各 {@link NumberPicker} 控件</li>
     *   <li>设置各滚轮的取值范围和值变化监听器</li>
     *   <li>初始化各控件的显示状态</li>
     *   <li>设置初始日期时间值</li>
     * </ol>
     * </p>
     *
     * @param context      控件所在的上下文环境
     * @param date         初始显示的时间，以毫秒时间戳表示
     * @param is24HourView {@code true} 表示使用 24 小时制；
     *                     {@code false} 表示使用 12 小时制（AM/PM）
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);
        mDate = Calendar.getInstance();
        mInitialising = true;
        // 根据当前小时判断初始 AM/PM 状态
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;

        // 加载日期时间选择器布局文件
        inflate(context, R.layout.datetime_picker, this);

        // 初始化日期选择滚轮
        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        // 初始化小时选择滚轮
        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);

        // 初始化分钟选择滚轮，设置长按快速滚动间隔为 100ms
        mMinuteSpinner = (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // 初始化 AM/PM 选择滚轮，显示值从系统本地化资源中获取
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 将各控件更新到初始显示状态
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        // 设置 24/12 小时制模式
        set24HourView(is24HourView);

        // 将控件显示设置为指定的初始时间
        setCurrentDate(date);

        // 同步控件启用状态
        setEnabled(isEnabled());

        // 初始化完成，关闭初始化标志，后续值变更将触发回调
        mInitialising = false;
    }

    /**
     * 设置控件及其所有子滚轮的启用/禁用状态
     *
     * <p>当新状态与当前状态相同时直接返回，避免重复设置。
     * 启用状态变化时会同步更新四个子 {@link NumberPicker} 的状态。</p>
     *
     * @param enabled {@code true} 启用控件；{@code false} 禁用控件
     */
    @Override
    public void setEnabled(boolean enabled) {
        // 若状态未发生变化，直接返回，避免不必要的重绘
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        // 同步更新所有子滚轮的启用状态
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    /**
     * 获取控件当前的启用状态
     *
     * @return {@code true} 表示控件已启用；{@code false} 表示控件已禁用
     */
    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * 获取当前选中日期时间对应的毫秒时间戳
     *
     * @return 当前选中日期时间的毫秒时间戳
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /**
     * 通过毫秒时间戳设置当前日期时间
     *
     * <p>将时间戳转换为 {@link Calendar} 后，拆分为年、月、日、时、分
     * 分别传入 {@link #setCurrentDate(int, int, int, int, int)} 进行设置。</p>
     *
     * @param date 目标日期时间的毫秒时间戳
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * 通过年、月、日、时、分分别设置当前日期时间
     *
     * @param year       目标年份
     * @param month      目标月份（0 表示一月，11 表示十二月）
     * @param dayOfMonth 目标日（1~31）
     * @param hourOfDay  目标小时（24 小时制，0~23）
     * @param minute     目标分钟（0~59）
     */
    public void setCurrentDate(int year, int month,
            int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    /**
     * 获取当前选中的年份
     *
     * @return 当前选中的年份
     */
    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    /**
     * 设置当前年份
     *
     * <p>若新值与当前值相同（且非初始化阶段），直接返回避免重复刷新。</p>
     *
     * @param year 目标年份
     */
    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的月份
     *
     * @return 当前选中的月份（0 表示一月，11 表示十二月）
     */
    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    /**
     * 设置当前月份
     *
     * <p>若新值与当前值相同（且非初始化阶段），直接返回避免重复刷新。</p>
     *
     * @param month 目标月份（0 表示一月，11 表示十二月）
     */
    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的日（月中的第几天）
     *
     * @return 当前选中的日（1~31）
     */
    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 设置当前日（月中的第几天）
     *
     * <p>若新值与当前值相同（且非初始化阶段），直接返回避免重复刷新。</p>
     *
     * @param dayOfMonth 目标日（1~31）
     */
    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的小时（24 小时制）
     *
     * @return 当前小时，范围 0~23
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取当前小时的滚轮显示值
     *
     * <p>根据当前时间格式返回对应的小时值：
     * <ul>
     *   <li>24 小时制：直接返回 0~23 的值</li>
     *   <li>12 小时制：将 24 小时值转换为 1~12 的值，
     *       其中 0 点显示为 12，13~23 点减去 12</li>
     * </ul>
     * </p>
     *
     * @return 适合当前时间格式的小时滚轮显示值
     */
    private int getCurrentHour() {
        if (mIs24HourView) {
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            if (hour > HOURS_IN_HALF_DAY) {
                // 下午时段（13~23）转换为 12 小时制（1~11）
                return hour - HOURS_IN_HALF_DAY;
            } else {
                // 0 点在 12 小时制中显示为 12，其余正常显示
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;
            }
        }
    }

    /**
     * 设置当前小时（24 小时制）
     *
     * <p>若新值与当前值相同（且非初始化阶段），直接返回避免重复刷新。
     * 在 12 小时制模式下，会同时更新 AM/PM 状态和对应的显示值。</p>
     *
     * @param hourOfDay 目标小时（24 小时制，0~23）
     */
    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);

        if (!mIs24HourView) {
            // 12 小时制：根据小时值更新 AM/PM 状态并转换为 12 小时制显示值
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    // 下午时段（13~23）转换为 1~11 显示
                    hourOfDay -= HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                if (hourOfDay == 0) {
                    // 0 点在 12 小时制中显示为 12
                    hourOfDay = HOURS_IN_HALF_DAY;
                }
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    /**
     * 获取当前选中的分钟
     *
     * @return 当前分钟，范围 0~59
     */
    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    /**
     * 设置当前分钟
     *
     * <p>若新值与当前值相同（且非初始化阶段），直接返回避免重复刷新。</p>
     *
     * @param minute 目标分钟（0~59）
     */
    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    /**
     * 获取当前是否为 24 小时制显示模式
     *
     * @return {@code true} 表示当前为 24 小时制；{@code false} 表示为 12 小时制
     */
    public boolean is24HourView() {
        return mIs24HourView;
    }

    /**
     * 设置时间显示模式（24 小时制或 12 小时制）
     *
     * <p>切换模式时：
     * <ul>
     *   <li>24 小时制：隐藏 AM/PM 滚轮，小时范围设为 0~23</li>
     *   <li>12 小时制：显示 AM/PM 滚轮，小时范围设为 1~12</li>
     * </ul>
     * 模式切换后保持当前实际小时不变（以 24 小时制为基准转换）。</p>
     *
     * @param is24HourView {@code true} 切换为 24 小时制；
     *                     {@code false} 切换为 12 小时制（AM/PM）
     */
    public void set24HourView(boolean is24HourView) {
        // 若模式未发生变化，直接返回
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;
        // 根据模式切换 AM/PM 滚轮的可见性
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);
        // 保存当前小时值（24 小时制），用于模式切换后恢复
        int hour = getCurrentHourOfDay();
        // 更新小时滚轮的取值范围
        updateHourControl();
        // 恢复小时显示（会根据新模式自动转换显示值）
        setCurrentHour(hour);
        // 更新 AM/PM 控件状态
        updateAmPmControl();
    }

    /**
     * 更新日期选择滚轮的显示内容
     *
     * <p>以当前内部日期为中心，生成前后各若干天（共 7 天）的日期字符串数组，
     * 格式为 "MM.dd EEEE"（如 "01.15 Monday"），并将滚轮定位到中间位置。</p>
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        // 从当前日期前推（DAYS_IN_ALL_WEEK/2 + 1）天作为起始点
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);

        // 清空旧的显示值，避免滚轮显示异常
        mDateSpinner.setDisplayedValues(null);

        // 依次生成 7 天的日期字符串
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }

        // 设置新的显示值并将滚轮定位到中间（当前日期位置）
        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);
        mDateSpinner.invalidate();
    }

    /**
     * 更新 AM/PM 选择滚轮的显示状态
     *
     * <p>24 小时制下隐藏该控件；12 小时制下根据当前 AM/PM 状态设置滚轮值。</p>
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            // 24 小时制下不需要 AM/PM 控件
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            // 12 小时制下根据 mIsAm 标志更新滚轮显示值
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新小时选择滚轮的取值范围
     *
     * <p>根据当前时间格式设置小时滚轮的最小值和最大值：
     * <ul>
     *   <li>24 小时制：范围 0~23</li>
     *   <li>12 小时制：范围 1~12</li>
     * </ul>
     * </p>
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    /**
     * 注册日期时间变更事件的监听器
     *
     * <p>当用户通过控件修改任意日期或时间字段时，
     * 注册的监听器将收到包含完整日期时间信息的回调通知。</p>
     *
     * @param callback 实现了 {@link OnDateTimeChangedListener} 接口的回调对象；
     *                 传入 {@code null} 将清除当前监听器
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /**
     * 触发日期时间变更回调通知
     *
     * <p>在任意日期或时间字段发生变化后调用，
     * 若已注册监听器则将当前完整的日期时间信息传递给监听器。
     * 初始化阶段（{@link #mInitialising} 为 {@code true}）不触发回调。</p>
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this,
                    getCurrentYear(), getCurrentMonth(), getCurrentDay(),
                    getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}