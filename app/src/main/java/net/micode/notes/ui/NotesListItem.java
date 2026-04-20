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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser.NoteItemBgResources;

/**
 * 便签列表项自定义视图
 *
 * <p>该类继承自 {@link LinearLayout}，是便签列表中每个列表项的视图容器，
 * 通过加载 {@code R.layout.note_item} 布局文件构建视图结构。</p>
 *
 * <p>支持三种列表项显示类型：
 * <ol>
 *   <li><strong>通话记录文件夹</strong>（{@link Notes#ID_CALL_RECORD_FOLDER}）：
 *       显示文件夹图标、文件夹名称及包含的便签数量</li>
 *   <li><strong>通话记录便签</strong>（父 ID 为通话记录文件夹）：
 *       显示联系人姓名、通话内容摘要及提醒图标（若有）</li>
 *   <li><strong>普通便签或文件夹</strong>：
 *       显示便签摘要或文件夹名称，便签若有提醒则显示时钟图标</li>
 * </ol>
 * </p>
 *
 * <p>多选模式（choiceMode）下，普通便签列表项左侧会显示复选框，
 * 供用户进行批量操作（删除、移动等）。</p>
 *
 * <p>背景资源根据便签的颜色索引及其在列表中的相对位置
 * （首项、末项、单项、普通项）动态选择，实现圆角分组的视觉效果。</p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see NoteItemData
 * @see NoteItemBgResources
 */
public class NotesListItem extends LinearLayout {

    /** 提醒图标控件，用于显示时钟图标（有提醒时）或通话记录图标 */
    private ImageView mAlert;

    /** 便签标题/摘要文本控件，或文件夹名称文本控件 */
    private TextView mTitle;

    /** 便签最后修改时间文本控件，显示相对时间字符串（如"3分钟前"） */
    private TextView mTime;

    /** 通话记录便签的联系人姓名文本控件，非通话记录便签时隐藏 */
    private TextView mCallName;

    /**
     * 当前列表项绑定的便签数据模型
     *
     * <p>通过 {@link #getItemData()} 对外提供访问，
     * 供点击事件处理等场景使用。</p>
     */
    private NoteItemData mItemData;

    /** 多选模式下显示的复选框控件，普通模式下隐藏 */
    private CheckBox mCheckBox;

    /**
     * 构造函数：加载布局并初始化各子控件引用
     *
     * <p>通过 {@link #inflate} 加载 {@code R.layout.note_item} 布局，
     * 并通过 {@code findViewById} 绑定各子控件。</p>
     *
     * @param context 控件所在的上下文环境，用于加载布局资源
     */
    public NotesListItem(Context context) {
        super(context);
        // 加载列表项布局文件，将其附加到当前 LinearLayout 容器
        inflate(context, R.layout.note_item, this);
        // 绑定各子控件
        mAlert    = (ImageView) findViewById(R.id.iv_alert_icon);
        mTitle    = (TextView)  findViewById(R.id.tv_title);
        mTime     = (TextView)  findViewById(R.id.tv_time);
        mCallName = (TextView)  findViewById(R.id.tv_name);
        mCheckBox = (CheckBox)  findViewById(android.R.id.checkbox);
    }

    /**
     * 将便签数据绑定到列表项视图并刷新显示内容
     *
     * <p>根据数据类型和父文件夹 ID 分三种情况处理视图的显示逻辑，
     * 同时处理多选模式下复选框的显示状态，最后更新背景资源。</p>
     *
     * <p>三种显示类型的处理逻辑：
     * <ol>
     *   <li><strong>通话记录文件夹</strong>（ID == {@link Notes#ID_CALL_RECORD_FOLDER}）：
     *       隐藏联系人姓名，显示通话记录图标，标题显示文件夹名称和便签数量</li>
     *   <li><strong>通话记录便签</strong>（parentId == {@link Notes#ID_CALL_RECORD_FOLDER}）：
     *       显示联系人姓名，标题显示通话摘要，有提醒时显示时钟图标</li>
     *   <li><strong>普通便签或文件夹</strong>：
     *       隐藏联系人姓名，文件夹显示名称和数量，便签显示摘要及提醒图标</li>
     * </ol>
     * </p>
     *
     * @param context    上下文环境，用于获取字符串资源和设置文本样式
     * @param data       当前列表项对应的便签数据模型，包含所有显示所需的数据
     * @param choiceMode 是否处于多选模式：{@code true} 时普通便签显示复选框
     * @param checked    多选模式下当前列表项的选中状态
     */
    public void bind(Context context, NoteItemData data,
                     boolean choiceMode, boolean checked) {

        // 多选模式下仅普通便签显示复选框，文件夹不支持多选
        if (choiceMode && data.getType() == Notes.TYPE_NOTE) {
            mCheckBox.setVisibility(View.VISIBLE);
            mCheckBox.setChecked(checked);
        } else {
            mCheckBox.setVisibility(View.GONE);
        }

        // 保存当前绑定的数据模型，供外部通过 getItemData() 访问
        mItemData = data;

        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // ---- 类型一：通话记录文件夹 ----
            mCallName.setVisibility(View.GONE);
            mAlert.setVisibility(View.VISIBLE);
            // 使用主标题样式显示文件夹名称和包含的便签数量
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);
            mTitle.setText(context.getString(R.string.call_record_folder_name)
                    + context.getString(
                            R.string.format_folder_files_count, data.getNotesCount()));
            // 显示通话记录专属图标
            mAlert.setImageResource(R.drawable.call_record);

        } else if (data.getParentId() == Notes.ID_CALL_RECORD_FOLDER) {
            // ---- 类型二：通话记录文件夹中的便签 ----
            // 显示联系人姓名（优先显示通讯录姓名，否则显示电话号码）
            mCallName.setVisibility(View.VISIBLE);
            mCallName.setText(data.getCallName());
            // 使用次级标题样式显示通话内容摘要
            mTitle.setTextAppearance(context, R.style.TextAppearanceSecondaryItem);
            mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
            // 根据是否有提醒决定是否显示时钟图标
            if (data.hasAlert()) {
                mAlert.setImageResource(R.drawable.clock);
                mAlert.setVisibility(View.VISIBLE);
            } else {
                mAlert.setVisibility(View.GONE);
            }

        } else {
            // ---- 类型三：普通便签或普通文件夹 ----
            mCallName.setVisibility(View.GONE);
            mTitle.setTextAppearance(context, R.style.TextAppearancePrimaryItem);

            if (data.getType() == Notes.TYPE_FOLDER) {
                // 文件夹：显示文件夹名称和包含的便签数量，不显示提醒图标
                mTitle.setText(data.getSnippet()
                        + context.getString(
                                R.string.format_folder_files_count, data.getNotesCount()));
                mAlert.setVisibility(View.GONE);
            } else {
                // 普通便签：显示格式化后的摘要内容，有提醒时显示时钟图标
                mTitle.setText(DataUtils.getFormattedSnippet(data.getSnippet()));
                if (data.hasAlert()) {
                    mAlert.setImageResource(R.drawable.clock);
                    mAlert.setVisibility(View.VISIBLE);
                } else {
                    mAlert.setVisibility(View.GONE);
                }
            }
        }

        // 显示便签最后修改时间的相对时间字符串（如"3分钟前"、"昨天"）
        mTime.setText(DateUtils.getRelativeTimeSpanString(data.getModifiedDate()));

        // 根据便签颜色和列表位置设置列表项背景资源
        setBackground(data);
    }

    /**
     * 根据便签颜色索引和在列表中的位置选择并设置背景资源
     *
     * <p>普通便签根据位置状态选择对应的圆角背景样式：
     * <ul>
     *   <li>单项或文件夹后唯一便签（{@link NoteItemData#isSingle()} 或
     *       {@link NoteItemData#isOneFollowingFolder()}）：四角圆角（Single 样式）</li>
     *   <li>末项（{@link NoteItemData#isLast()}）：底部圆角（Last 样式）</li>
     *   <li>首项或文件夹后多条便签首项（{@link NoteItemData#isFirst()} 或
     *       {@link NoteItemData#isMultiFollowingFolder()}）：顶部圆角（First 样式）</li>
     *   <li>其他中间项：无圆角（Normal 样式）</li>
     * </ul>
     * 文件夹类型使用统一的文件夹背景资源，不受颜色索引影响。</p>
     *
     * @param data 当前列表项的便签数据模型，提供颜色索引和位置状态信息
     */
    private void setBackground(NoteItemData data) {
        // 获取便签的背景颜色索引
        int id = data.getBgColorId();

        if (data.getType() == Notes.TYPE_NOTE) {
            // 普通便签：根据在列表中的位置选择对应的圆角背景样式
            if (data.isSingle() || data.isOneFollowingFolder()) {
                // 单项或文件夹后唯一便签：四角均圆角
                setBackgroundResource(NoteItemBgResources.getNoteBgSingleRes(id));
            } else if (data.isLast()) {
                // 列表末项：底部圆角
                setBackgroundResource(NoteItemBgResources.getNoteBgLastRes(id));
            } else if (data.isFirst() || data.isMultiFollowingFolder()) {
                // 列表首项或文件夹后多条便签的首条：顶部圆角
                setBackgroundResource(NoteItemBgResources.getNoteBgFirstRes(id));
            } else {
                // 中间普通项：无圆角
                setBackgroundResource(NoteItemBgResources.getNoteBgNormalRes(id));
            }
        } else {
            // 文件夹类型：使用统一的文件夹背景资源（不受颜色索引影响）
            setBackgroundResource(NoteItemBgResources.getFolderBgRes());
        }
    }

    /**
     * 获取当前列表项绑定的便签数据模型
     *
     * <p>通常在列表项点击事件处理中调用，
     * 用于获取用户点击的具体便签或文件夹数据。</p>
     *
     * @return 当前列表项绑定的 {@link NoteItemData} 数据模型对象
     */
    public NoteItemData getItemData() {
        return mItemData;
    }
}