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
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import net.micode.notes.data.Notes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * NotesListAdapter —— 便签列表适配器
 *
 * 功能：
 *   继承自 CursorAdapter，负责将数据库查询结果（Cursor）绑定到便签列表的每一个列表项上。
 *   同时支持多选模式（ChoiceMode），用于批量删除等操作，
 *   记录每个列表项的选中状态，并提供获取选中便签 ID 和桌面小部件信息的方法。
 *
 * 继承自：CursorAdapter
 */
public class NotesListAdapter extends CursorAdapter {

    /** 日志标签，用于调试时标识日志来源 */
    private static final String TAG = "NotesListAdapter";

    /** 上下文对象，用于创建列表项视图 */
    private Context mContext;

    /**
     * 记录每个列表项的选中状态。
     * key：列表项的位置（position）
     * value：是否被选中（true = 已选中，false = 未选中）
     */
    private HashMap<Integer, Boolean> mSelectedIndex;

    /** 当前列表中类型为普通便签（TYPE_NOTE）的数量，用于判断是否全选 */
    private int mNotesCount;

    /** 是否处于多选模式。true = 多选模式，false = 普通模式 */
    private boolean mChoiceMode;

    /**
     * AppWidgetAttribute —— 桌面小部件属性内部类
     *
     * 功能：
     *   用于封装一个便签所关联的桌面小部件的 ID 和类型，
     *   在批量操作时用于收集选中便签对应的小部件信息。
     */
    public static class AppWidgetAttribute {
        /** 桌面小部件的 ID */
        public int widgetId;
        /** 桌面小部件的类型 */
        public int widgetType;
    }

    /**
     * 构造方法，初始化便签列表适配器。
     *
     * @param context 上下文对象，用于后续创建列表项视图
     */
    public NotesListAdapter(Context context) {
        super(context, null);
        // 初始化选中状态记录表
        mSelectedIndex = new HashMap<Integer, Boolean>();
        mContext = context;
        mNotesCount = 0;
    }

    /**
     * 创建一个新的列表项视图。
     * 当 ListView 需要显示新的列表项且没有可复用的视图时由系统调用。
     *
     * @param context 上下文对象
     * @param cursor  指向当前数据行的游标
     * @param parent  列表项所属的父容器
     * @return 新创建的 NotesListItem 视图对象
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        // 创建并返回一个新的便签列表项视图
        return new NotesListItem(context);
    }

    /**
     * 将数据绑定到已有的列表项视图上。
     * 每次列表项需要显示或刷新时由系统调用。
     *
     * 主要流程：
     *   1. 确认视图类型为 NotesListItem
     *   2. 根据当前 Cursor 构造便签数据对象 NoteItemData
     *   3. 将数据、多选模式状态和选中状态一并绑定到列表项视图
     *
     * @param view    需要绑定数据的列表项视图
     * @param context 上下文对象
     * @param cursor  指向当前数据行的游标
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof NotesListItem) {
            // 根据当前游标位置的数据构造便签数据对象
            NoteItemData itemData = new NoteItemData(context, cursor);
            // 将数据绑定到列表项视图，同时传入多选模式状态和当前项的选中状态
            ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                    isSelectedItem(cursor.getPosition()));
        }
    }

    /**
     * 设置指定位置列表项的选中状态。
     * 更新选中状态后通知适配器刷新列表。
     *
     * @param position 列表项的位置
     * @param checked  true = 选中，false = 取消选中
     */
    public void setCheckedItem(final int position, final boolean checked) {
        mSelectedIndex.put(position, checked);
        // 通知 ListView 数据已变化，触发界面刷新
        notifyDataSetChanged();
    }

    /**
     * 判断当前是否处于多选模式。
     *
     * @return true = 当前为多选模式，false = 当前为普通模式
     */
    public boolean isInChoiceMode() {
        return mChoiceMode;
    }

    /**
     * 设置多选模式状态。
     * 切换模式时清空所有已记录的选中状态。
     *
     * @param mode true = 进入多选模式，false = 退出多选模式
     */
    public void setChoiceMode(boolean mode) {
        // 切换模式时清空原有选中记录
        mSelectedIndex.clear();
        mChoiceMode = mode;
    }

    /**
     * 全选或取消全选所有类型为普通便签（TYPE_NOTE）的列表项。
     * 文件夹类型的列表项不参与全选操作。
     *
     * @param checked true = 全选，false = 取消全选
     */
    public void selectAll(boolean checked) {
        Cursor cursor = getCursor();
        for (int i = 0; i < getCount(); i++) {
            if (cursor.moveToPosition(i)) {
                // 只对普通便签类型执行全选，文件夹类型跳过
                if (NoteItemData.getNoteType(cursor) == Notes.TYPE_NOTE) {
                    setCheckedItem(i, checked);
                }
            }
        }
    }

    /**
     * 获取所有已选中列表项的便签 ID 集合。
     * 会过滤掉根文件夹 ID（ID_ROOT_FOLDER），并记录异常日志。
     *
     * @return 包含所有已选中便签 ID 的 HashSet
     */
    public HashSet<Long> getSelectedItemIds() {
        HashSet<Long> itemSet = new HashSet<Long>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Long id = getItemId(position);
                if (id == Notes.ID_ROOT_FOLDER) {
                    // 根文件夹 ID 不应出现在选中集合中，记录异常日志
                    Log.d(TAG, "Wrong item id, should not happen");
                } else {
                    itemSet.add(id);
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取所有已选中便签所关联的桌面小部件属性集合。
     * 用于在删除便签时同步移除对应的桌面小部件。
     *
     * 注意：
     *   方法内部不关闭 Cursor，Cursor 的生命周期由适配器统一管理。
     *
     * @return 包含所有已选中便签小部件属性的 HashSet；若存在无效 Cursor 则返回 null
     */
    public HashSet<AppWidgetAttribute> getSelectedWidget() {
        HashSet<AppWidgetAttribute> itemSet = new HashSet<AppWidgetAttribute>();
        for (Integer position : mSelectedIndex.keySet()) {
            if (mSelectedIndex.get(position) == true) {
                Cursor c = (Cursor) getItem(position);
                if (c != null) {
                    // 构造小部件属性对象，读取当前便签关联的小部件 ID 和类型
                    AppWidgetAttribute widget = new AppWidgetAttribute();
                    NoteItemData item = new NoteItemData(mContext, c);
                    widget.widgetId   = item.getWidgetId();
                    widget.widgetType = item.getWidgetType();
                    itemSet.add(widget);
                    /**
                     * 不在此处关闭 Cursor，Cursor 只能由适配器负责关闭
                     */
                } else {
                    Log.e(TAG, "Invalid cursor");
                    return null;
                }
            }
        }
        return itemSet;
    }

    /**
     * 获取当前已选中的列表项数量。
     * 遍历选中状态记录表，统计值为 true 的条目数。
     *
     * @return 已选中的列表项数量
     */
    public int getSelectedCount() {
        Collection<Boolean> values = mSelectedIndex.values();
        if (null == values) {
            return 0;
        }
        Iterator<Boolean> iter = values.iterator();
        int count = 0;
        while (iter.hasNext()) {
            if (true == iter.next()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断当前是否已全选所有普通便签。
     * 已选数量不为 0 且等于普通便签总数时视为全选。
     *
     * @return true = 已全选，false = 未全选
     */
    public boolean isAllSelected() {
        int checkedCount = getSelectedCount();
        return (checkedCount != 0 && checkedCount == mNotesCount);
    }

    /**
     * 判断指定位置的列表项是否处于选中状态。
     *
     * @param position 列表项的位置
     * @return true = 已选中，false = 未选中
     */
    public boolean isSelectedItem(final int position) {
        if (null == mSelectedIndex.get(position)) {
            return false;
        }
        return mSelectedIndex.get(position);
    }

    /**
     * 当底层数据内容发生变化时由系统回调。
     * 重新统计普通便签数量，保证全选判断的准确性。
     */
    @Override
    protected void onContentChanged() {
        super.onContentChanged();
        // 数据变化后重新计算普通便签数量
        calcNotesCount();
    }

    /**
     * 更换适配器所使用的 Cursor 数据源。
     * 切换数据源后重新统计普通便签数量。
     *
     * @param cursor 新的数据库查询游标
     */
    @Override
    public void changeCursor(Cursor cursor) {
        super.changeCursor(cursor);
        // 更换数据源后重新计算普通便签数量
        calcNotesCount();
    }

    /**
     * 统计当前列表中类型为普通便签（TYPE_NOTE）的数量。
     * 结果保存在 mNotesCount 中，供 isAllSelected() 使用。
     * 遇到无效 Cursor 时记录错误日志并提前返回。
     */
    private void calcNotesCount() {
        mNotesCount = 0;
        for (int i = 0; i < getCount(); i++) {
            Cursor c = (Cursor) getItem(i);
            if (c != null) {
                // 只统计类型为普通便签的条目，文件夹类型不计入
                if (NoteItemData.getNoteType(c) == Notes.TYPE_NOTE) {
                    mNotesCount++;
                }
            } else {
                Log.e(TAG, "Invalid cursor");
                return;
            }
        }
    }
}