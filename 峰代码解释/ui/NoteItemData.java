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
import android.database.Cursor;
import android.text.TextUtils;

import net.micode.notes.data.Contact;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.tool.DataUtils;

/**
 * 便签列表项数据模型类
 *
 * <p>该类封装了便签列表中单个列表项（便签或文件夹）的所有数据字段，
 * 通过从数据库游标（{@link Cursor}）中读取数据完成初始化。</p>
 *
 * <p>主要职责：
 * <ul>
 *   <li>从数据库游标中解析并存储便签或文件夹的基本属性
 *       （ID、摘要、背景色、时间戳、Widget 信息等）</li>
 *   <li>对通话记录类型的便签，额外查询关联的电话号码和联系人姓名</li>
 *   <li>通过 {@link #checkPostion(Cursor)} 分析列表项在当前列表中的相对位置，
 *       为列表项的圆角背景样式渲染提供依据</li>
 *   <li>提供丰富的 getter 方法供列表适配器和 ViewHolder 使用</li>
 * </ul>
 * </p>
 *
 * <p>位置状态说明（用于列表项背景样式的选择）：
 * <ul>
 *   <li>{@link #mIsFirstItem}：是否为列表中的第一项</li>
 *   <li>{@link #mIsLastItem}：是否为列表中的最后一项</li>
 *   <li>{@link #mIsOnlyOneItem}：是否为列表中唯一的一项</li>
 *   <li>{@link #mIsOneNoteFollowingFolder}：是否为紧跟文件夹之后的唯一便签</li>
 *   <li>{@link #mIsMultiNotesFollowingFolder}：是否为紧跟文件夹之后的多条便签之一</li>
 * </ul>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see NotesListAdapter
 */
public class NoteItemData {

    /**
     * 数据库查询列投影
     *
     * <p>定义了查询便签列表时所需的所有数据库列，
     * 列的顺序与下方列索引常量严格对应，不可随意调整。</p>
     */
    static final String[] PROJECTION = new String[]{
            NoteColumns.ID,             // 便签唯一标识符
            NoteColumns.ALERTED_DATE,   // 提醒时间戳
            NoteColumns.BG_COLOR_ID,    // 背景颜色索引
            NoteColumns.CREATED_DATE,   // 创建时间戳
            NoteColumns.HAS_ATTACHMENT, // 是否有附件
            NoteColumns.MODIFIED_DATE,  // 最后修改时间戳
            NoteColumns.NOTES_COUNT,    // 文件夹内便签数量（仅文件夹有效）
            NoteColumns.PARENT_ID,      // 父文件夹 ID
            NoteColumns.SNIPPET,        // 便签摘要内容
            NoteColumns.TYPE,           // 类型（便签/文件夹/系统）
            NoteColumns.WIDGET_ID,      // 关联桌面小部件 ID
            NoteColumns.WIDGET_TYPE,    // 关联桌面小部件类型
    };

    // ========================================================================
    // PROJECTION 列索引常量
    // 与上方 PROJECTION 数组的列顺序严格一一对应
    // ========================================================================

    /** ID 列的索引 */
    private static final int ID_COLUMN           = 0;
    /** 提醒时间列的索引 */
    private static final int ALERTED_DATE_COLUMN = 1;
    /** 背景颜色 ID 列的索引 */
    private static final int BG_COLOR_ID_COLUMN  = 2;
    /** 创建时间列的索引 */
    private static final int CREATED_DATE_COLUMN = 3;
    /** 是否有附件列的索引 */
    private static final int HAS_ATTACHMENT_COLUMN = 4;
    /** 最后修改时间列的索引 */
    private static final int MODIFIED_DATE_COLUMN = 5;
    /** 文件夹内便签数量列的索引 */
    private static final int NOTES_COUNT_COLUMN  = 6;
    /** 父文件夹 ID 列的索引 */
    private static final int PARENT_ID_COLUMN    = 7;
    /** 便签摘要列的索引 */
    private static final int SNIPPET_COLUMN      = 8;
    /** 类型列的索引 */
    private static final int TYPE_COLUMN         = 9;
    /** 桌面小部件 ID 列的索引 */
    private static final int WIDGET_ID_COLUMN    = 10;
    /** 桌面小部件类型列的索引 */
    private static final int WIDGET_TYPE_COLUMN  = 11;

    // ========================================================================
    // 便签/文件夹基本数据字段
    // ========================================================================

    /** 便签或文件夹的数据库唯一 ID */
    private long mId;

    /** 提醒闹钟时间戳（毫秒），值为 0 表示未设置提醒 */
    private long mAlertDate;

    /** 背景颜色索引，对应 {@link net.micode.notes.tool.ResourceParser} 中的颜色常量 */
    private int mBgColorId;

    /** 便签创建时间戳（毫秒） */
    private long mCreatedDate;

    /** 是否有附件（如通话记录附件） */
    private boolean mHasAttachment;

    /** 便签最后修改时间戳（毫秒） */
    private long mModifiedDate;

    /** 文件夹内包含的便签数量（仅对文件夹类型有效，便签类型为 0） */
    private int mNotesCount;

    /** 父文件夹的数据库 ID，根目录便签的父 ID 为 0 */
    private long mParentId;

    /**
     * 便签摘要内容
     *
     * <p>从数据库读取后，会自动去除清单模式下的
     * 已勾选（{@link NoteEditActivity#TAG_CHECKED}）和
     * 未勾选（{@link NoteEditActivity#TAG_UNCHECKED}）前缀标记，
     * 确保列表显示的摘要为纯文本内容。</p>
     */
    private String mSnippet;

    /** 类型标识：便签（{@link Notes#TYPE_NOTE}）或文件夹（{@link Notes#TYPE_FOLDER}）等 */
    private int mType;

    /** 关联的桌面小部件 ID */
    private int mWidgetId;

    /** 关联的桌面小部件类型（2x 或 4x） */
    private int mWidgetType;

    /**
     * 通话记录联系人姓名
     *
     * <p>仅对通话记录文件夹（{@link Notes#ID_CALL_RECORD_FOLDER}）中的便签有效。
     * 优先显示通讯录中的联系人姓名，若查询不到则显示电话号码本身。</p>
     */
    private String mName;

    /**
     * 通话记录关联的电话号码
     *
     * <p>仅对通话记录文件夹中的便签有效，其他便签为空字符串。</p>
     */
    private String mPhoneNumber;

    // ========================================================================
    // 列表位置状态字段
    // 用于列表适配器根据位置选择对应的圆角背景样式
    // ========================================================================

    /** 是否为当前列表中的最后一项 */
    private boolean mIsLastItem;

    /** 是否为当前列表中的第一项 */
    private boolean mIsFirstItem;

    /** 是否为当前列表中唯一的一项 */
    private boolean mIsOnlyOneItem;

    /**
     * 是否为紧跟在文件夹之后且该组只有一条便签
     *
     * <p>用于决定是否使用"单项（Single）"圆角背景样式。</p>
     */
    private boolean mIsOneNoteFollowingFolder;

    /**
     * 是否为紧跟在文件夹之后且该组有多条便签
     *
     * <p>用于决定是否使用"首项（First）"圆角背景样式。</p>
     */
    private boolean mIsMultiNotesFollowingFolder;

    /**
     * 构造函数：从数据库游标中读取数据并初始化便签列表项数据模型
     *
     * <p>完成以下初始化工作：
     * <ol>
     *   <li>从游标中读取所有基本字段数据</li>
     *   <li>清理摘要内容中的清单模式标记字符</li>
     *   <li>若为通话记录便签，查询关联的电话号码和联系人姓名</li>
     *   <li>调用 {@link #checkPostion(Cursor)} 分析当前项在列表中的相对位置</li>
     * </ol>
     * </p>
     *
     * @param context 上下文环境，用于查询通讯录联系人信息
     * @param cursor  指向当前数据行的数据库游标，
     *                应包含 {@link #PROJECTION} 中定义的所有列
     */
    public NoteItemData(Context context, Cursor cursor) {
        // 从游标中读取各字段数据
        mId             = cursor.getLong(ID_COLUMN);
        mAlertDate      = cursor.getLong(ALERTED_DATE_COLUMN);
        mBgColorId      = cursor.getInt(BG_COLOR_ID_COLUMN);
        mCreatedDate    = cursor.getLong(CREATED_DATE_COLUMN);
        // 将整型的附件标志（>0 为有附件）转换为布尔值
        mHasAttachment  = (cursor.getInt(HAS_ATTACHMENT_COLUMN) > 0);
        mModifiedDate   = cursor.getLong(MODIFIED_DATE_COLUMN);
        mNotesCount     = cursor.getInt(NOTES_COUNT_COLUMN);
        mParentId       = cursor.getLong(PARENT_ID_COLUMN);
        mSnippet        = cursor.getString(SNIPPET_COLUMN);

        // 去除摘要中清单模式的已勾选/未勾选前缀标记，确保列表显示纯文本
        mSnippet = mSnippet.replace(NoteEditActivity.TAG_CHECKED, "")
                           .replace(NoteEditActivity.TAG_UNCHECKED, "");

        mType       = cursor.getInt(TYPE_COLUMN);
        mWidgetId   = cursor.getInt(WIDGET_ID_COLUMN);
        mWidgetType = cursor.getInt(WIDGET_TYPE_COLUMN);

        // 初始化电话号码为空字符串
        mPhoneNumber = "";

        // 若便签位于通话记录文件夹，查询关联的电话号码和联系人姓名
        if (mParentId == Notes.ID_CALL_RECORD_FOLDER) {
            mPhoneNumber = DataUtils.getCallNumberByNoteId(
                    context.getContentResolver(), mId);
            if (!TextUtils.isEmpty(mPhoneNumber)) {
                // 优先从通讯录中查找联系人姓名；若未找到则以电话号码作为显示名称
                mName = Contact.getContact(context, mPhoneNumber);
                if (mName == null) {
                    mName = mPhoneNumber;
                }
            }
        }

        // 非通话记录便签的联系人姓名默认为空字符串
        if (mName == null) {
            mName = "";
        }

        // 分析当前列表项在列表中的相对位置，用于背景样式的选择
        checkPostion(cursor);
    }

    /**
     * 分析并记录当前列表项在列表中的相对位置状态
     *
     * <p>通过检查游标的位置信息及前一项的类型，判断以下位置状态：
     * <ul>
     *   <li>是否为列表首项、末项或唯一项</li>
     *   <li>若为普通便签，检查其前一项是否为文件夹或系统文件夹，
     *       并根据后续便签数量判断该便签是否为"文件夹后唯一便签"
     *       或"文件夹后多条便签之一"</li>
     * </ul>
     * </p>
     *
     * <p><strong>注意：</strong>该方法会临时移动游标到前一行进行检查，
     * 检查完成后会恢复游标位置。若无法恢复则抛出 {@link IllegalStateException}。</p>
     *
     * @param cursor 指向当前数据行的数据库游标
     * @throws IllegalStateException 若游标移动到前一行后无法恢复到当前行时抛出
     */
    private void checkPostion(Cursor cursor) {
        // 判断当前项是否为列表末项、首项或唯一项
        mIsLastItem     = cursor.isLast();
        mIsFirstItem    = cursor.isFirst();
        mIsOnlyOneItem  = (cursor.getCount() == 1);

        // 初始化文件夹跟随状态
        mIsMultiNotesFollowingFolder = false;
        mIsOneNoteFollowingFolder    = false;

        // 仅对非首项的普通便签进行文件夹跟随状态的检查
        if (mType == Notes.TYPE_NOTE && !mIsFirstItem) {
            int position = cursor.getPosition();

            // 临时将游标移至前一行，检查前一项的类型
            if (cursor.moveToPrevious()) {
                int prevType = cursor.getInt(TYPE_COLUMN);

                if (prevType == Notes.TYPE_FOLDER || prevType == Notes.TYPE_SYSTEM) {
                    // 前一项为文件夹或系统文件夹，判断当前组的便签数量
                    if (cursor.getCount() > (position + 1)) {
                        // 当前位置之后还有更多项：当前便签是文件夹后多条便签之一
                        mIsMultiNotesFollowingFolder = true;
                    } else {
                        // 当前位置之后无更多项：当前便签是文件夹后的唯一便签
                        mIsOneNoteFollowingFolder = true;
                    }
                }

                // 将游标恢复到当前行，若恢复失败则抛出异常
                if (!cursor.moveToNext()) {
                    throw new IllegalStateException(
                            "cursor move to previous but can't move back");
                }
            }
        }
    }

    /**
     * 判断当前便签是否为紧跟文件夹之后的唯一便签
     *
     * <p>用于列表适配器选择"单项（Single）"圆角背景样式。</p>
     *
     * @return {@code true} 表示是文件夹后的唯一便签；{@code false} 否则
     */
    public boolean isOneFollowingFolder() {
        return mIsOneNoteFollowingFolder;
    }

    /**
     * 判断当前便签是否为紧跟文件夹之后的多条便签之一
     *
     * <p>用于列表适配器选择"首项（First）"圆角背景样式。</p>
     *
     * @return {@code true} 表示是文件夹后多条便签中的第一条；{@code false} 否则
     */
    public boolean isMultiFollowingFolder() {
        return mIsMultiNotesFollowingFolder;
    }

    /**
     * 判断当前列表项是否为列表中的最后一项
     *
     * <p>用于列表适配器选择"末项（Last）"圆角背景样式。</p>
     *
     * @return {@code true} 表示是列表最后一项；{@code false} 否则
     */
    public boolean isLast() {
        return mIsLastItem;
    }

    /**
     * 获取通话记录便签关联的联系人姓名或电话号码
     *
     * <p>若能从通讯录中查到联系人，返回联系人姓名；
     * 否则返回电话号码本身；非通话记录便签返回空字符串。</p>
     *
     * @return 联系人姓名、电话号码或空字符串
     */
    public String getCallName() {
        return mName;
    }

    /**
     * 判断当前列表项是否为列表中的第一项
     *
     * @return {@code true} 表示是列表第一项；{@code false} 否则
     */
    public boolean isFirst() {
        return mIsFirstItem;
    }

    /**
     * 判断当前列表项是否为列表中唯一的一项
     *
     * <p>用于列表适配器选择"单项（Single）"圆角背景样式。</p>
     *
     * @return {@code true} 表示列表中只有这一项；{@code false} 否则
     */
    public boolean isSingle() {
        return mIsOnlyOneItem;
    }

    /**
     * 获取便签或文件夹的数据库唯一 ID
     *
     * @return 数据库 ID
     */
    public long getId() {
        return mId;
    }

    /**
     * 获取便签的提醒闹钟时间戳
     *
     * @return 提醒时间戳（毫秒）；值为 0 表示未设置提醒
     */
    public long getAlertDate() {
        return mAlertDate;
    }

    /**
     * 获取便签的创建时间戳
     *
     * @return 创建时间戳（毫秒）
     */
    public long getCreatedDate() {
        return mCreatedDate;
    }

    /**
     * 判断便签是否有附件
     *
     * @return {@code true} 表示有附件；{@code false} 表示无附件
     */
    public boolean hasAttachment() {
        return mHasAttachment;
    }

    /**
     * 获取便签的最后修改时间戳
     *
     * @return 最后修改时间戳（毫秒）
     */
    public long getModifiedDate() {
        return mModifiedDate;
    }

    /**
     * 获取便签的背景颜色索引
     *
     * @return 背景颜色索引，对应 {@link net.micode.notes.tool.ResourceParser} 中的颜色常量
     */
    public int getBgColorId() {
        return mBgColorId;
    }

    /**
     * 获取便签所在父文件夹的数据库 ID
     *
     * @return 父文件夹 ID；根目录便签的父 ID 为 0
     */
    public long getParentId() {
        return mParentId;
    }

    /**
     * 获取文件夹内包含的便签数量
     *
     * <p>仅对文件夹类型（{@link Notes#TYPE_FOLDER}）的列表项有效，
     * 普通便签此值为 0。</p>
     *
     * @return 文件夹内的便签数量
     */
    public int getNotesCount() {
        return mNotesCount;
    }

    /**
     * 获取便签所在文件夹的 ID（与 {@link #getParentId()} 等价）
     *
     * <p>提供语义更清晰的方法名，用于需要明确表达"文件夹 ID"概念的场景。</p>
     *
     * @return 所在文件夹的数据库 ID
     */
    public long getFolderId() {
        return mParentId;
    }

    /**
     * 获取列表项的类型
     *
     * @return 类型常量，如 {@link Notes#TYPE_NOTE}、{@link Notes#TYPE_FOLDER} 等
     */
    public int getType() {
        return mType;
    }

    /**
     * 获取关联桌面小部件的类型
     *
     * @return 桌面小部件类型，如 {@link Notes#TYPE_WIDGET_2X} 或 {@link Notes#TYPE_WIDGET_4X}
     */
    public int getWidgetType() {
        return mWidgetType;
    }

    /**
     * 获取关联桌面小部件的 ID
     *
     * @return 桌面小部件 ID；未关联小部件时为 {@link android.appwidget.AppWidgetManager#INVALID_APPWIDGET_ID}
     */
    public int getWidgetId() {
        return mWidgetId;
    }

    /**
     * 获取便签的摘要内容
     *
     * <p>已去除清单模式的勾选/未勾选前缀标记，为纯文本内容。</p>
     *
     * @return 便签摘要字符串
     */
    public String getSnippet() {
        return mSnippet;
    }

    /**
     * 判断便签是否设置了提醒闹钟
     *
     * @return {@code true} 表示已设置提醒（{@link #mAlertDate} > 0）；
     *         {@code false} 表示未设置提醒
     */
    public boolean hasAlert() {
        return (mAlertDate > 0);
    }

    /**
     * 判断当前便签是否为通话记录类型
     *
     * <p>判断条件：便签位于通话记录文件夹（{@link Notes#ID_CALL_RECORD_FOLDER}）
     * 且关联的电话号码不为空。</p>
     *
     * @return {@code true} 表示是通话记录便签；{@code false} 否则
     */
    public boolean isCallRecord() {
        return (mParentId == Notes.ID_CALL_RECORD_FOLDER
                && !TextUtils.isEmpty(mPhoneNumber));
    }

    /**
     * 从数据库游标中静态读取当前行的便签类型
     *
     * <p>该静态方法无需构造 {@link NoteItemData} 对象，
     * 可直接从游标中快速获取当前行的类型字段，
     * 通常用于列表适配器在 {@code getItemViewType} 中判断列表项类型。</p>
     *
     * @param cursor 指向目标数据行的数据库游标，
     *               应包含 {@link #PROJECTION} 中定义的列
     * @return 类型常量，如 {@link Notes#TYPE_NOTE} 或 {@link Notes#TYPE_FOLDER}
     */
    public static int getNoteType(Cursor cursor) {
        return cursor.getInt(TYPE_COLUMN);
    }
}