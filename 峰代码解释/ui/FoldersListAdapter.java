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
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;

/**
 * 文件夹列表适配器
 *
 * <p>该类继承自 {@link CursorAdapter}，负责将数据库中的文件夹数据
 * 绑定到便签移动目标选择列表的每个列表项视图上。</p>
 *
 * <p>主要使用场景：
 * 当用户需要将便签移动到指定文件夹时，弹出的文件夹选择列表
 * 由该适配器驱动，展示所有可用的目标文件夹。</p>
 *
 * <p>特殊处理：
 * 根文件夹（{@link Notes#ID_ROOT_FOLDER}）不直接显示数据库中存储的名称，
 * 而是使用本地化字符串资源（{@code R.string.menu_move_parent_folder}）
 * 显示为"父文件夹"或"根目录"，以提升用户体验。</p>
 *
 * <p>视图层次结构：
 * <ul>
 *   <li>列表容器：{@link android.widget.ListView}</li>
 *   <li>列表项视图：{@link FolderListItem}（内部类）</li>
 *   <li>文件夹名称：{@link TextView}（{@code R.id.tv_folder_name}）</li>
 * </ul>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see CursorAdapter
 * @see FolderListItem
 */
public class FoldersListAdapter extends CursorAdapter {

    /**
     * 数据库查询列投影
     *
     * <p>仅查询文件夹 ID 和名称两列，
     * 减少不必要的数据传输以提高列表加载性能。</p>
     */
    public static final String[] PROJECTION = {
            NoteColumns.ID,      // 文件夹唯一标识符
            NoteColumns.SNIPPET  // 文件夹名称（摘要字段存储文件夹名）
    };

    /** PROJECTION 中文件夹 ID 列的索引 */
    public static final int ID_COLUMN   = 0;

    /** PROJECTION 中文件夹名称列的索引 */
    public static final int NAME_COLUMN = 1;

    /**
     * 构造函数：初始化文件夹列表适配器
     *
     * @param context 适配器所在的上下文环境，用于访问资源和布局
     * @param c       包含文件夹数据的数据库游标，
     *                应包含 {@link #PROJECTION} 中定义的列
     */
    public FoldersListAdapter(Context context, Cursor c) {
        super(context, c);
    }

    /**
     * 创建新的列表项视图
     *
     * <p>当列表需要显示新的列表项且没有可复用的视图时，
     * 该方法被 {@link CursorAdapter} 调用以创建新的视图实例。
     * 每个列表项均使用 {@link FolderListItem} 作为视图容器。</p>
     *
     * @param context 视图创建时的上下文环境
     * @param cursor  指向当前数据行的游标（此处未直接使用，数据绑定在 bindView 中完成）
     * @param parent  新视图将被添加到的父容器
     * @return 新创建的 {@link FolderListItem} 视图实例
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return new FolderListItem(context);
    }

    /**
     * 将数据库游标中的数据绑定到指定的列表项视图
     *
     * <p>该方法由 {@link CursorAdapter} 在需要显示或刷新列表项时调用。
     * 对根文件夹（{@link Notes#ID_ROOT_FOLDER}）进行特殊处理：
     * 使用本地化字符串替代数据库中存储的名称进行显示。</p>
     *
     * @param view    需要绑定数据的目标列表项视图，应为 {@link FolderListItem} 实例
     * @param context 绑定操作时的上下文环境，用于获取字符串资源
     * @param cursor  指向当前数据行的游标，包含文件夹 ID 和名称数据
     */
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof FolderListItem) {
            // 判断当前文件夹是否为根文件夹
            // 根文件夹使用本地化字符串显示，普通文件夹直接使用数据库中存储的名称
            String folderName = (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER)
                    ? context.getString(R.string.menu_move_parent_folder)
                    : cursor.getString(NAME_COLUMN);

            // 将文件夹名称绑定到列表项视图
            ((FolderListItem) view).bind(folderName);
        }
    }

    /**
     * 根据列表位置索引获取对应文件夹的显示名称
     *
     * <p>对根文件夹（{@link Notes#ID_ROOT_FOLDER}）进行特殊处理，
     * 返回本地化的根文件夹名称字符串，与 {@link #bindView} 中的处理逻辑保持一致。</p>
     *
     * <p>该方法通常用于在用户选中某个文件夹后，
     * 获取其名称以更新 UI 显示（如更新下拉按钮的标题）。</p>
     *
     * @param context  上下文环境，用于获取根文件夹的本地化名称字符串
     * @param position 目标文件夹在列表中的位置索引（从 0 开始）
     * @return 对应位置文件夹的显示名称字符串；
     *         根文件夹返回本地化字符串，普通文件夹返回数据库中存储的名称
     */
    public String getFolderName(Context context, int position) {
        // 通过位置索引从适配器中获取对应的游标对象
        Cursor cursor = (Cursor) getItem(position);

        // 判断是否为根文件夹，并返回对应的名称字符串
        return (cursor.getLong(ID_COLUMN) == Notes.ID_ROOT_FOLDER)
                ? context.getString(R.string.menu_move_parent_folder)
                : cursor.getString(NAME_COLUMN);
    }

    /**
     * 文件夹列表项视图内部类
     *
     * <p>该类继承自 {@link LinearLayout}，是文件夹列表中每个条目的视图容器。
     * 通过加载 {@code R.layout.folder_list_item} 布局文件构建视图结构，
     * 内部仅包含一个用于显示文件夹名称的 {@link TextView}。</p>
     *
     * <p>设计说明：
     * 使用内部类的方式将视图逻辑与适配器紧密耦合，
     * 简化了视图的创建与数据绑定流程，符合 Android 列表适配器的最佳实践。</p>
     */
    private class FolderListItem extends LinearLayout {

        /** 显示文件夹名称的文本控件 */
        private TextView mName;

        /**
         * 构造函数：加载布局并初始化文件夹名称文本控件
         *
         * @param context 视图创建时的上下文环境
         */
        public FolderListItem(Context context) {
            super(context);
            // 加载文件夹列表项布局文件，将其附加到当前 LinearLayout 容器中
            inflate(context, R.layout.folder_list_item, this);
            // 绑定文件夹名称文本控件
            mName = (TextView) findViewById(R.id.tv_folder_name);
        }

        /**
         * 将文件夹名称绑定到列表项视图的文本控件
         *
         * <p>由外部适配器的 {@link FoldersListAdapter#bindView} 方法调用，
         * 将数据库中查询到的文件夹名称（或本地化的根文件夹名称）显示到界面上。</p>
         *
         * @param name 需要显示的文件夹名称字符串
         */
        public void bind(String name) {
            mName.setText(name);
        }
    }
}