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

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 便签清单模式下的自定义文本编辑控件
 *
 * <p>该类继承自 {@link EditText}，是便签清单勾选模式（Check List Mode）
 * 中每个列表项的文本输入控件，对标准 EditText 进行了以下扩展：</p>
 *
 * <ul>
 *   <li><strong>回车键处理</strong>：按下回车键时，将光标后的文本拆分到新的列表项中</li>
 *   <li><strong>删除键处理</strong>：在文本为空且位于行首时，删除当前列表项并将内容合并到上一项</li>
 *   <li><strong>触摸事件处理</strong>：精确计算触摸位置对应的文本偏移量，将光标定位到触摸处</li>
 *   <li><strong>焦点变化处理</strong>：焦点变化时通知外部控制复选框的显示/隐藏状态</li>
 *   <li><strong>URL 长按菜单</strong>：长按选中包含超链接的文本时，弹出对应类型的操作菜单
 *       （拨打电话、打开网页、发送邮件）</li>
 * </ul>
 *
 * <p>与外部的交互通过 {@link OnTextViewChangeListener} 接口实现，
 * 由 {@link NoteEditActivity} 实现并注册。</p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see NoteEditActivity
 * @see OnTextViewChangeListener
 */
public class NoteEditText extends EditText {

    /** 日志标签，用于 Logcat 中过滤本类日志输出 */
    private static final String TAG = "NoteEditText";

    /**
     * 当前编辑控件在清单列表中的位置索引
     *
     * <p>用于在回调 {@link OnTextViewChangeListener} 时传递位置信息，
     * 以便外部（{@link NoteEditActivity}）定位并操作对应的列表项。</p>
     */
    private int mIndex;

    /**
     * 按下删除键之前的光标起始位置
     *
     * <p>在 {@link #onKeyDown} 中记录，在 {@link #onKeyUp} 中使用。
     * 用于判断删除操作发生时光标是否处于文本行首（位置为 0），
     * 以决定是否触发删除当前列表项的回调。</p>
     */
    private int mSelectionStartBeforeDelete;

    // ========================================================================
    // URL Scheme 常量定义
    // 用于识别文本中超链接的协议类型，以提供对应的操作菜单项
    // ========================================================================

    /** URL Scheme：电话号码链接（如 tel:10086） */
    private static final String SCHEME_TEL   = "tel:";

    /** URL Scheme：网页链接（如 http://www.example.com） */
    private static final String SCHEME_HTTP  = "http:";

    /** URL Scheme：邮件链接（如 mailto:user@example.com） */
    private static final String SCHEME_EMAIL = "mailto:";

    /**
     * URL Scheme 与操作菜单文本资源 ID 的映射表
     *
     * <p>Key：URL Scheme 前缀字符串</p>
     * <p>Value：对应操作菜单项的字符串资源 ID
     * （如"拨打电话"、"打开网页"、"发送邮件"）</p>
     */
    private static final Map<String, Integer> sSchemaActionResMap =
            new HashMap<String, Integer>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL,   R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP,  R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * 文本内容变化事件监听器接口
     *
     * <p>由 {@link NoteEditActivity} 实现并通过
     * {@link #setOnTextViewChangeListener} 注册，
     * 用于在清单列表项的增删、内容变化时通知外部进行相应的 UI 更新。</p>
     *
     * <p>由 {@link NoteEditActivity} 调用，用于删除或新增编辑框。</p>
     */
    public interface OnTextViewChangeListener {

        /**
         * 当按下删除键（{@link KeyEvent#KEYCODE_DEL}）且当前编辑框文本为空时，
         * 通知外部删除当前列表项并将光标移至上一项末尾。
         *
         * @param index 当前被删除的编辑框在清单列表中的索引
         * @param text  被删除编辑框中的文本内容（通常为空字符串），
         *              用于追加到前一个编辑框末尾
         */
        void onEditTextDelete(int index, String text);

        /**
         * 当按下回车键（{@link KeyEvent#KEYCODE_ENTER}）时，
         * 通知外部在当前列表项之后插入一个新的列表项。
         *
         * @param index 新列表项应插入的位置索引（当前项索引 + 1）
         * @param text  从当前编辑框光标处截取的后半段文本，
         *              将作为新列表项的初始内容
         */
        void onEditTextEnter(int index, String text);

        /**
         * 当编辑框的文本内容发生变化或焦点状态改变时，
         * 通知外部根据是否有文本内容控制复选框的显示/隐藏。
         *
         * @param index   发生变化的编辑框在清单列表中的索引
         * @param hasText 当前编辑框是否有文本内容：
         *                {@code true} 表示有内容（显示复选框），
         *                {@code false} 表示为空（隐藏复选框）
         */
        void onTextChange(int index, boolean hasText);
    }

    /** 外部注册的文本内容变化监听器，由 {@link NoteEditActivity} 实现 */
    private OnTextViewChangeListener mOnTextViewChangeListener;

    /**
     * 构造函数：通过代码直接创建控件时使用
     *
     * <p>默认将列表项索引初始化为 0，
     * 并传入 {@code null} 作为 AttributeSet 以使用默认样式。</p>
     *
     * @param context 控件所在的上下文环境
     */
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    /**
     * 设置当前编辑控件在清单列表中的位置索引
     *
     * <p>在清单列表项被创建、插入或删除后，
     * 由 {@link NoteEditActivity} 调用以更新索引，保持索引的连续性。</p>
     *
     * @param index 新的列表位置索引（从 0 开始）
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 注册文本内容变化事件监听器
     *
     * <p>通常由 {@link NoteEditActivity} 在创建列表项时调用，
     * 将自身作为监听器传入，以接收删除、回车、文本变化等事件的回调。</p>
     *
     * @param listener 实现了 {@link OnTextViewChangeListener} 接口的监听器对象
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    /**
     * 构造函数：从 XML 布局文件中加载控件时使用
     *
     * <p>使用系统默认的 {@code editTextStyle} 样式，
     * 确保控件外观与系统标准 EditText 保持一致。</p>
     *
     * @param context 控件所在的上下文环境
     * @param attrs   XML 布局文件中定义的属性集合
     */
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    /**
     * 构造函数：从 XML 布局文件加载控件并应用自定义样式时使用
     *
     * @param context  控件所在的上下文环境
     * @param attrs    XML 布局文件中定义的属性集合
     * @param defStyle 默认样式资源 ID
     */
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 处理触摸事件，将光标精确定位到触摸位置
     *
     * <p>在 {@link MotionEvent#ACTION_DOWN} 事件中，
     * 将触摸点的屏幕坐标转换为文本中的字符偏移量，
     * 并通过 {@link Selection#setSelection} 将光标移至对应位置。</p>
     *
     * <p>坐标转换步骤：
     * <ol>
     *   <li>获取触摸点的原始坐标（相对于控件左上角）</li>
     *   <li>减去内边距偏移量，得到文本区域内的相对坐标</li>
     *   <li>加上滚动偏移量，得到文本内容的实际坐标</li>
     *   <li>通过 {@link Layout} 计算对应的文本行和字符偏移量</li>
     * </ol>
     * </p>
     *
     * @param event 触摸事件对象，包含触摸位置和动作类型
     * @return 事件处理结果，交由父类继续处理
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 获取触摸点相对于控件左上角的坐标
                int x = (int) event.getX();
                int y = (int) event.getY();

                // 减去左侧和顶部内边距，转换为文本区域内的坐标
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();

                // 加上当前滚动偏移量，得到文本内容的实际坐标
                x += getScrollX();
                y += getScrollY();

                // 根据 y 坐标确定触摸点所在的文本行
                Layout layout = getLayout();
                int line = layout.getLineForVertical(y);

                // 根据行号和 x 坐标确定触摸点对应的字符偏移量
                int off = layout.getOffsetForHorizontal(line, x);

                // 将光标定位到触摸点对应的字符位置
                Selection.setSelection(getText(), off);
                break;
        }
        return super.onTouchEvent(event);
    }

    /**
     * 处理按键按下事件
     *
     * <p>针对两个特殊按键进行预处理：
     * <ul>
     *   <li>{@link KeyEvent#KEYCODE_ENTER}：若已注册监听器，返回 {@code false}
     *       以阻止默认的换行行为，改由 {@link #onKeyUp} 中的自定义逻辑处理</li>
     *   <li>{@link KeyEvent#KEYCODE_DEL}：记录删除前的光标位置，
     *       用于在 {@link #onKeyUp} 中判断是否需要触发删除列表项的回调</li>
     * </ul>
     * </p>
     *
     * @param keyCode 按下的按键码
     * @param event   按键事件对象
     * @return 回车键且已注册监听器时返回 {@code false}（阻止默认换行）；
     *         其他情况交由父类处理
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    // 已注册监听器时阻止默认换行行为，由 onKeyUp 处理自定义逻辑
                    return false;
                }
                break;
            case KeyEvent.KEYCODE_DEL:
                // 记录删除操作前的光标起始位置，供 onKeyUp 中的边界判断使用
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 处理按键抬起事件
     *
     * <p>针对两个特殊按键执行自定义逻辑：
     * <ul>
     *   <li>{@link KeyEvent#KEYCODE_DEL}：若删除前光标位于行首（位置为 0）
     *       且当前不是第一个列表项，则触发删除当前列表项的回调，
     *       将本项内容合并到上一项末尾</li>
     *   <li>{@link KeyEvent#KEYCODE_ENTER}：将光标后的文本截取出来，
     *       清空当前编辑框中光标之后的内容，
     *       并触发在当前项之后插入新列表项的回调</li>
     * </ul>
     * </p>
     *
     * @param keyCode 抬起的按键码
     * @param event   按键事件对象
     * @return 删除键触发列表项删除时返回 {@code true}（消费事件）；
     *         其他情况交由父类处理
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (mOnTextViewChangeListener != null) {
                    // 判断条件：删除前光标位于行首（位置为 0）且不是第一个列表项
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        // 触发删除当前列表项的回调，将本项内容传递给上一项末尾追加
                        mOnTextViewChangeListener.onEditTextDelete(
                                mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                if (mOnTextViewChangeListener != null) {
                    // 获取当前光标位置，将文本分为两部分
                    int selectionStart = getSelectionStart();
                    // 截取光标之后的文本作为新列表项的内容
                    String text = getText().subSequence(
                            selectionStart, length()).toString();
                    // 清空当前编辑框中光标之后的内容（保留光标之前的部分）
                    setText(getText().subSequence(0, selectionStart));
                    // 触发在当前项之后插入新列表项的回调
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;

            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 处理编辑框焦点变化事件
     *
     * <p>当编辑框失去焦点时，根据当前文本是否为空，
     * 通知外部监听器更新对应列表项复选框的显示状态：
     * <ul>
     *   <li>失去焦点且文本为空：通知隐藏复选框（{@code hasText = false}）</li>
     *   <li>其他情况（获得焦点或有文本内容）：通知显示复选框（{@code hasText = true}）</li>
     * </ul>
     * </p>
     *
     * @param focused              当前是否获得焦点
     * @param direction            焦点移动的方向
     * @param previouslyFocusedRect 上一个获得焦点的 View 的边界矩形
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction,
            Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                // 失去焦点且内容为空：通知外部隐藏该项的复选框
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                // 获得焦点或内容不为空：通知外部显示该项的复选框
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 创建长按文本时的上下文菜单
     *
     * <p>当用户长按选中的文本区域中包含 {@link URLSpan} 超链接时，
     * 根据链接的 URL Scheme 类型，在上下文菜单中添加对应的操作菜单项：
     * <ul>
     *   <li>{@code tel:} → 拨打电话</li>
     *   <li>{@code http:} → 打开网页</li>
     *   <li>{@code mailto:} → 发送邮件</li>
     *   <li>其他未知 Scheme → 通用链接操作</li>
     * </ul>
     * 点击菜单项后，通过 {@link URLSpan#onClick} 触发对应的系统操作。</p>
     *
     * <p>仅当选中区域内恰好包含一个 {@link URLSpan} 时才添加菜单项，
     * 避免多链接情况下菜单歧义。</p>
     *
     * @param menu 需要填充菜单项的上下文菜单对象
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            // 获取当前选中区域的起始和结束位置
            int selStart = getSelectionStart();
            int selEnd   = getSelectionEnd();

            // 取选区的最小和最大位置，处理反向选择的情况
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            // 获取选中区域内的所有 URLSpan（超链接）
            final URLSpan[] urls = ((Spanned) getText())
                    .getSpans(min, max, URLSpan.class);

            // 仅当选中区域内恰好有一个超链接时才添加操作菜单项
            if (urls.length == 1) {
                int defaultResId = 0;

                // 根据 URL 的 Scheme 前缀匹配对应的操作菜单文本资源 ID
                for (String schema : sSchemaActionResMap.keySet()) {
                    if (urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                // 若未匹配到已知 Scheme，使用通用链接操作菜单文本
                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                // 添加菜单项，点击时通过 URLSpan 触发对应的系统操作（拨号/浏览器/邮件）
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // 通过 URLSpan 的 onClick 触发系统对应的 Intent
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}