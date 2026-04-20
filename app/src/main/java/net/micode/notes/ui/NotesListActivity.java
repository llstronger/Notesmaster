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
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

/**
 * NotesListActivity —— 便签列表主页面
 *
 * 功能：
 *   应用的主入口页面，以列表形式展示所有便签和文件夹。
 *   支持以下核心操作：
 *     1. 新建便签
 *     2. 点击便签进入编辑页面
 *     3. 点击文件夹进入子文件夹
 *     4. 长按便签进入多选模式，支持批量删除和批量移动
 *     5. 长按文件夹弹出右键菜单，支持查看、删除、重命名
 *     6. 支持便签导出为文本文件
 *     7. 支持 Google Task 同步
 *     8. 首次启动时自动写入应用介绍便签
 *
 * 实现接口：
 *   OnClickListener          —— 处理按钮点击事件
 *   OnItemLongClickListener  —— 处理列表项长按事件
 *
 * 继承自：Activity
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {

    /** 便签列表查询请求标识符，用于区分不同的异步查询结果 */
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;

    /** 文件夹列表查询请求标识符，用于移动便签时查询目标文件夹列表 */
    private static final int FOLDER_LIST_QUERY_TOKEN = 1;

    /** 右键菜单：删除文件夹 */
    private static final int MENU_FOLDER_DELETE = 0;

    /** 右键菜单：查看文件夹 */
    private static final int MENU_FOLDER_VIEW = 1;

    /** 右键菜单：重命名文件夹 */
    private static final int MENU_FOLDER_CHANGE_NAME = 2;

    /** SharedPreferences 键名，记录是否已写入应用介绍便签 */
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";

    /**
     * 列表页面的状态枚举
     * NOTE_LIST          —— 主页面，显示根目录下的便签和文件夹
     * SUB_FOLDER         —— 子文件夹页面，显示某个文件夹内的便签
     * CALL_RECORD_FOLDER —— 通话记录文件夹页面
     */
    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    }

    /** 当前列表页面所处的状态 */
    private ListEditState mState;

    /** 异步数据库查询处理器，避免在主线程执行数据库操作 */
    private BackgroundQueryHandler mBackgroundQueryHandler;

    /** 便签列表适配器，负责将数据库数据绑定到列表视图 */
    private NotesListAdapter mNotesListAdapter;

    /** 显示便签列表的 ListView 控件 */
    private ListView mNotesListView;

    /** 新建便签按钮 */
    private Button mAddNewNote;

    /** 是否将触摸事件分发给列表视图（处理新建按钮透明区域的点击穿透） */
    private boolean mDispatch;

    /** 触摸事件起始 Y 坐标，用于计算滑动偏移 */
    private int mOriginY;

    /** 分发给列表视图的 Y 坐标 */
    private int mDispatchY;

    /** 子文件夹或通话记录文件夹页面顶部的标题栏 */
    private TextView mTitleBar;

    /** 当前正在浏览的文件夹 ID，默认为根文件夹 */
    private long mCurrentFolderId;

    /** 内容解析器，用于对数据库执行增删改查操作 */
    private ContentResolver mContentResolver;

    /** 多选模式回调，处理批量操作的菜单和选中状态 */
    private ModeCallback mModeCallBack;

    /** 日志标签 */
    private static final String TAG = "NotesListActivity";

    /** ListView 滚动速率 */
    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;

    /** 当前长按的便签数据项，用于右键菜单操作 */
    private NoteItemData mFocusNoteDataItem;

    /** 普通文件夹的查询条件：父文件夹 ID 等于当前文件夹 ID */
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";

    /**
     * 根目录的查询条件：
     * 显示所有非系统类型且父目录为根目录的便签，
     * 以及通话记录文件夹（仅当其中有记录时才显示）
     */
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";

    /** 打开已有便签的请求码 */
    private final static int REQUEST_CODE_OPEN_NODE = 102;

    /** 新建便签的请求码 */
    private final static int REQUEST_CODE_NEW_NODE = 103;

    /**
     * Activity 创建时调用，初始化页面布局和资源，并在首次启动时写入应用介绍便签。
     *
     * @param savedInstanceState Activity 被系统重建时传入的历史状态数据，首次创建时为 null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 加载便签列表页面布局
        setContentView(R.layout.note_list);
        // 初始化页面所有控件和资源
        initResources();
        // 首次使用时写入应用介绍便签
        setAppInfoFromRawRes();
    }

    /**
     * 从其他页面返回时的回调方法。
     * 若从新建或编辑页面返回且结果为成功，则清空适配器的 Cursor 以触发列表刷新。
     *
     * @param requestCode 发起请求时的请求码
     * @param resultCode  返回的结果码
     * @param data        返回携带的 Intent 数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE
                || requestCode == REQUEST_CODE_NEW_NODE)) {
            // 清空 Cursor，触发列表在 onResume 中重新查询
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * 首次启动应用时，从 raw 资源文件中读取应用介绍内容，并将其保存为一条便签。
     * 通过 SharedPreferences 记录是否已写入，避免重复创建。
     */
    private void setAppInfoFromRawRes() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        // 若已写入介绍便签则直接返回，不重复执行
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                // 打开 raw 目录下的 introduction 文本文件
                in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char[] buf = new char[1024];
                    int len = 0;
                    // 逐块读取文件内容并拼接
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                // 确保文件流在使用完毕后关闭，防止资源泄漏
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            // 在根目录创建一条空便签，并将介绍内容写入
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());

            if (note.saveNote()) {
                // 写入成功后记录到 SharedPreferences，防止下次重复写入
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }

    /**
     * Activity 从后台切换到前台时调用。
     * 每次页面可见时重新发起便签列表的异步查询，保证数据最新。
     */
    @Override
    protected void onStart() {
        super.onStart();
        // 发起异步查询，刷新便签列表
        startAsyncNotesListQuery();
    }

    /**
     * 初始化页面所需的所有控件和资源。
     *
     * 主要流程：
     *   1. 获取 ContentResolver 和异步查询处理器
     *   2. 设置当前文件夹为根目录
     *   3. 初始化 ListView 并添加底部视图、设置监听器
     *   4. 初始化便签列表适配器
     *   5. 初始化新建便签按钮
     *   6. 初始化标题栏和页面状态
     */
    private void initResources() {
        mContentResolver = this.getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());

        // 默认进入根目录
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;

        // 初始化便签列表控件
        mNotesListView = (ListView) findViewById(R.id.notes_list);

        // 添加列表底部视图（装饰用途，不可点击）
        mNotesListView.addFooterView(
                LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);

        // 设置列表项点击和长按监听器
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);

        // 初始化适配器并绑定到列表
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);

        // 初始化新建便签按钮
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());

        // 初始化触摸事件分发相关变量
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;

        // 初始化标题栏
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);

        // 默认状态为主便签列表
        mState = ListEditState.NOTE_LIST;

        // 初始化多选模式回调
        mModeCallBack = new ModeCallback();
    }

    /**
     * ModeCallback —— 多选操作模式回调内部类
     *
     * 功能：
     *   实现 ListView 的多选模式（ActionMode），
     *   提供批量删除和批量移动文件夹的菜单操作，
     *   以及全选/取消全选的下拉菜单。
     *
     * 实现接口：
     *   ListView.MultiChoiceModeListener —— 处理多选模式的生命周期和选中状态变化
     *   OnMenuItemClickListener          —— 处理菜单项点击事件
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {

        /** 下拉菜单，用于全选/取消全选操作 */
        private DropdownMenu mDropDownMenu;

        /** 当前的多选操作模式对象 */
        private ActionMode mActionMode;

        /** "移动"菜单项，根据当前文件夹和用户文件夹数量决定是否显示 */
        private MenuItem mMoveMenu;

        /**
         * 进入多选模式时调用，初始化操作栏菜单和下拉菜单。
         *
         * @param mode 当前的 ActionMode 对象
         * @param menu 操作栏菜单
         * @return true 表示成功创建操作模式
         */
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // 加载多选操作菜单布局
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);

            mMoveMenu = menu.findItem(R.id.move);
            // 若当前在通话记录文件夹中，或用户没有自定义文件夹，则隐藏"移动"菜单
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }

            mActionMode = mode;

            // 进入多选模式：开启适配器多选、禁用长按、隐藏新建按钮
            mNotesListAdapter.setChoiceMode(true);
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);

            // 加载并设置自定义操作栏视图（包含下拉菜单）
            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);

            // 初始化下拉菜单，点击时执行全选/取消全选
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    // 切换全选状态
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }
            });
            return true;
        }

        /**
         * 更新下拉菜单的标题和全选菜单项状态。
         * 根据当前已选数量更新显示文字，并同步全选/取消全选的勾选状态。
         */
        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // 更新下拉菜单标题，显示当前已选数量
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format);

            // 同步全选菜单项的勾选状态和文字
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }

        /**
         * 准备操作模式菜单时调用（当前未使用）。
         */
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        /**
         * 操作栏菜单项被点击时调用（当前未使用，由 onMenuItemClick 处理）。
         */
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        /**
         * 退出多选模式时调用。
         * 恢复适配器普通模式、重新开启长按、显示新建便签按钮。
         *
         * @param mode 当前的 ActionMode 对象
         */
        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            mAddNewNote.setVisibility(View.VISIBLE);
        }

        /**
         * 主动结束当前多选操作模式。
         */
        public void finishActionMode() {
            mActionMode.finish();
        }

        /**
         * 列表项选中状态发生变化时调用。
         * 同步更新适配器中对应位置的选中状态，并刷新菜单显示。
         *
         * @param mode     当前的 ActionMode 对象
         * @param position 发生变化的列表项位置
         * @param id       发生变化的列表项 ID
         * @param checked  变化后的选中状态
         */
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                                              boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }

        /**
         * 操作栏菜单项点击事件处理。
         * 处理批量删除和批量移动操作。
         * 若当前没有选中任何便签则提示用户。
         *
         * @param item 被点击的菜单项
         * @return true 表示事件已处理
         */
        public boolean onMenuItemClick(MenuItem item) {
            // 未选中任何便签时给出提示
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this,
                        getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }

            int id = item.getItemId();
            if (id == R.id.delete) {
                // 弹出删除确认对话框
                AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                builder.setTitle(getString(R.string.alert_title_delete));
                builder.setIcon(android.R.drawable.ic_dialog_alert);
                builder.setMessage(getString(R.string.alert_message_delete_notes,
                        mNotesListAdapter.getSelectedCount()));
                builder.setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // 确认后执行批量删除
                                batchDelete();
                            }
                        });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.show();
                return true;
            } else if (id == R.id.move) {
                // 查询可移动的目标文件夹列表
                startQueryDestinationFolders();
                return true;
            }
            return false;
        }
    }

    /**
     * NewNoteOnTouchListener —— 新建便签按钮触摸事件监听内部类
     *
     * 功能：
     *   处理新建便签按钮的透明区域点击穿透问题。
     *   当用户点击按钮透明区域时，将触摸事件转发给其下方的便签列表，
     *   使列表可以正常响应滑动操作。
     */
    private class NewNoteOnTouchListener implements OnTouchListener {

        /**
         * 处理新建便签按钮的触摸事件。
         *
         * 主要逻辑：
         *   按钮底部有一块透明区域，满足公式 y < -0.12x + 94 的点击位于透明区域内，
         *   此时将事件坐标转换后分发给列表视图处理，实现点击穿透效果。
         *
         * @param v     触发触摸事件的视图
         * @param event 触摸事件对象
         * @return true 表示事件已被消费并转发给列表
         */
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getAction();

            if (action == MotionEvent.ACTION_DOWN) {
                Display display = getWindowManager().getDefaultDisplay();
                int screenHeight = display.getHeight();
                int newNoteViewHeight = mAddNewNote.getHeight();
                int start = screenHeight - newNoteViewHeight;
                int eventY = start + (int) event.getY();

                // 在子文件夹模式下，需要减去标题栏高度进行坐标修正
                if (mState == ListEditState.SUB_FOLDER) {
                    eventY -= mTitleBar.getHeight();
                    start -= mTitleBar.getHeight();
                }

                /**
                 * 当点击位于按钮透明区域内（满足 y < -0.12x + 94 的点）时，
                 * 将触摸事件转发给列表视图，实现点击穿透。
                 * 透明区域由 UI 设计师根据按钮背景图形状确定，公式随背景图变化而变化。
                 */
                if (event.getY() < (event.getX() * (-0.12) + 94)) {
                    View view = mNotesListView.getChildAt(
                            mNotesListView.getChildCount() - 1
                                    - mNotesListView.getFooterViewsCount());
                    if (view != null && view.getBottom() > start
                            && (view.getTop() < (start + 94))) {
                        mOriginY = (int) event.getY();
                        mDispatchY = eventY;
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = true;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                // 滑动过程中持续转发事件，更新 Y 坐标偏移
                if (mDispatch) {
                    mDispatchY += (int) event.getY() - mOriginY;
                    event.setLocation(event.getX(), mDispatchY);
                    return mNotesListView.dispatchTouchEvent(event);
                }
            } else {
                // 手指抬起或取消时，结束事件转发
                if (mDispatch) {
                    event.setLocation(event.getX(), mDispatchY);
                    mDispatch = false;
                    return mNotesListView.dispatchTouchEvent(event);
                }
            }
            return false;
        }
    }

    /**
     * 发起异步查询，从数据库中加载当前文件夹下的便签列表。
     * 根据当前是否在根目录，使用不同的查询条件。
     * 查询结果按便签类型降序、修改时间降序排列。
     */
    private void startAsyncNotesListQuery() {
        // 根目录使用特殊查询条件（包含通话记录文件夹），子文件夹使用普通条件
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER)
                ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI,
                NoteItemData.PROJECTION,
                selection,
                new String[]{String.valueOf(mCurrentFolderId)},
                NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * BackgroundQueryHandler —— 异步数据库查询处理器内部类
     *
     * 功能：
     *   继承自 AsyncQueryHandler，在后台线程执行数据库查询，
     *   查询完成后在主线程回调 onQueryComplete 方法，更新 UI。
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {

        /**
         * 构造方法。
         *
         * @param contentResolver 内容解析器，用于执行数据库查询
         */
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        /**
         * 异步查询完成后的回调方法。
         * 根据查询令牌区分不同查询类型，分别更新便签列表或显示文件夹选择对话框。
         *
         * @param token  查询令牌，用于区分查询类型
         * @param cookie 附带的额外对象（此处未使用）
         * @param cursor 查询结果游标
         */
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (token == FOLDER_NOTE_LIST_QUERY_TOKEN) {
                // 便签列表查询完成，更新适配器数据源
                mNotesListAdapter.changeCursor(cursor);
            } else if (token == FOLDER_LIST_QUERY_TOKEN) {
                // 文件夹列表查询完成，显示移动目标文件夹选择对话框
                if (cursor != null && cursor.getCount() > 0) {
                    showFolderListMenu(cursor);
                } else {
                    Log.e(TAG, "Query folder failed");
                }
            }
        }
    }

    /**
     * 显示移动目标文件夹选择对话框。
     * 用户选择文件夹后，将所有已选中的便签批量移动到该文件夹。
     *
     * @param cursor 包含可选文件夹列表的游标
     */
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final NoteEditActivity adapter = new NoteEditActivity(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // 批量将选中便签移动到用户选择的文件夹
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(),
                        adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                // 移动完成后退出多选模式
                mModeCallBack.finishActionMode();
            }
        });
        builder.show();
    }

    /**
     * 跳转到便签编辑页面，新建一条便签。
     * 将当前文件夹 ID 传递给编辑页面，确保便签保存在正确的文件夹下。
     */
    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        // 传递当前文件夹 ID，新建便签将保存在此文件夹下
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    /**
     * 批量删除已选中的便签（异步执行）。
     *
     * 主要流程：
     *   1. 在后台线程收集选中便签关联的桌面小部件信息
     *   2. 根据是否开启同步模式，决定直接删除还是移入回收站
     *   3. 在主线程更新受影响的桌面小部件，并退出多选模式
     */
    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {

            /**
             * 后台线程执行删除操作。
             *
             * @return 受影响的桌面小部件属性集合
             */
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                // 收集选中便签关联的桌面小部件信息，用于删除后更新小部件
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // 非同步模式：直接从数据库删除
                    if (!DataUtils.batchDeleteNotes(mContentResolver,
                            mNotesListAdapter.getSelectedItemIds())) {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // 同步模式：将便签移入回收站，由同步服务负责远端删除
                    if (!DataUtils.batchMoveToFolder(mContentResolver,
                            mNotesListAdapter.getSelectedItemIds(),
                            Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }

            /**
             * 主线程中执行删除后的 UI 更新。
             * 更新受影响的桌面小部件，并退出多选模式。
             *
             * @param widgets 受影响的桌面小部件属性集合
             */
            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            // 通知桌面小部件刷新显示内容
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                // 退出多选操作模式
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }

    /**
     * 删除指定文件夹及其包含的所有便签。
     * 根据是否开启同步模式，决定直接删除还是移入回收站。
     * 删除后更新该文件夹内便签关联的桌面小部件。
     *
     * @param folderId 要删除的文件夹 ID，不能为根文件夹 ID
     */
    private void deleteFolder(long folderId) {
        // 根文件夹不允许删除
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }

        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);

        // 获取该文件夹内所有便签关联的桌面小部件信息
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);

        if (!isSyncMode()) {
            // 非同步模式：直接删除文件夹
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // 同步模式：移入回收站
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }

        // 更新受影响的桌面小部件
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }

    /**
     * 打开指定便签，跳转到便签编辑页面进行查看或编辑。
     *
     * @param data 要打开的便签数据项
     */
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        // 传递便签 ID，编辑页面根据 ID 加载对应便签内容
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }

    /**
     * 打开指定文件夹，进入文件夹内的便签列表页面。
     * 根据文件夹类型（普通文件夹或通话记录文件夹）更新页面状态和标题栏显示。
     *
     * @param data 要打开的文件夹数据项
     */
    private void openFolder(NoteItemData data) {
        // 更新当前文件夹 ID 并重新查询列表
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();

        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            // 进入通话记录文件夹：切换状态并隐藏新建按钮（通话记录不允许手动新建）
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else {
            // 进入普通子文件夹
            mState = ListEditState.SUB_FOLDER;
        }

        // 更新标题栏显示文字
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }

    /**
     * 处理按钮点击事件。
     * 目前仅处理新建便签按钮的点击。
     *
     * @param v 被点击的视图
     */
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_new_note) {
            createNewNote();
        }
    }

    /**
     * 显示软键盘。
     */
    private void showSoftInput() {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    /**
     * 隐藏软键盘。
     *
     * @param view 当前获得焦点的视图，用于获取窗口令牌
     */
    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /**
     * 显示新建或修改文件夹名称的对话框。
     *
     * 主要流程：
     *   1. 加载输入框布局并显示软键盘
     *   2. 根据 create 参数决定是新建还是修改文件夹名称
     *   3. 监听输入框文字变化，输入为空时禁用确认按钮
     *   4. 点击确认时校验文件夹名称是否已存在，通过后执行新建或更新操作
     *
     * @param create true = 新建文件夹，false = 修改文件夹名称
     */
    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();

        if (!create) {
            // 修改文件夹名称：预填当前文件夹名称
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            // 新建文件夹：输入框清空
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }

        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // 点击取消时隐藏软键盘
                hideSoftInput(etName);
            }
        });

        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button) dialog.findViewById(android.R.id.button1);

        // 自定义确认按钮点击逻辑
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();

                // 校验文件夹名称是否已存在
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this,
                            getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    // 选中已输入的文字，提示用户修改
                    etName.setSelection(0, etName.length());
                    return;
                }

                if (!create) {
                    // 修改文件夹名称：更新数据库中的记录
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values,
                                NoteColumns.ID + "=?",
                                new String[]{String.valueOf(mFocusNoteDataItem.getId())});
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    // 新建文件夹：插入新记录到数据库
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });

        // 初始状态下若输入框为空则禁用确认按钮
        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }

        // 监听输入框文字变化，动态启用/禁用确认按钮
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 文字变化前无需处理
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 输入框为空时禁用确认按钮，否则启用
                positive.setEnabled(!TextUtils.isEmpty(etName.getText()));
            }

            public void afterTextChanged(Editable s) {
                // 文字变化后无需处理
            }
        });
    }

    /**
     * 发送广播通知指定桌面小部件刷新显示内容。
     * 根据小部件类型选择对应的 Provider 类。
     *
     * @param appWidgetId   要更新的桌面小部件 ID
     * @param appWidgetType 桌面小部件类型（2x 或 4x）
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 文件夹长按右键菜单监听器。
     * 长按文件夹时弹出包含查看、删除、重命名三个选项的上下文菜单。
     */
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener =
            new OnCreateContextMenuListener() {
                public void onCreateContextMenu(ContextMenu menu, View v,
                                                ContextMenuInfo menuInfo) {
                    if (mFocusNoteDataItem != null) {
                        // 设置菜单标题为文件夹名称
                        menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                        menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                        menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                        menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
                    }
                }
            };

    /**
     * 右键菜单关闭时调用，移除文件夹右键菜单监听器，避免重复响应。
     *
     * @param menu 关闭的菜单对象
     */
    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }

    /**
     * 右键菜单项被选中时调用，处理文件夹的查看、删除和重命名操作。
     *
     * @param item 被选中的菜单项
     * @return true 表示事件已处理
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }

        int id = item.getItemId();
        if (id == MENU_FOLDER_VIEW) {
            // 进入文件夹
            openFolder(mFocusNoteDataItem);
        } else if (id == MENU_FOLDER_DELETE) {
            // 弹出删除确认对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_folder));
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // 确认后删除文件夹
                            deleteFolder(mFocusNoteDataItem.getId());
                        }
                    });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (id == MENU_FOLDER_CHANGE_NAME) {
            // 显示重命名对话框
            showCreateOrModifyFolderDialog(false);
        }
        return true;
    }

    /**
     * 准备顶部选项菜单时调用。
     * 根据当前页面状态（主列表、子文件夹、通话记录文件夹）加载不同的菜单布局。
     *
     * @param menu 顶部选项菜单对象
     * @return true 表示菜单已准备完成
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            // 主列表菜单：包含新建文件夹、导出、同步、设置、搜索等选项
            getMenuInflater().inflate(R.menu.note_list, menu);
            // 根据当前同步状态动态设置同步菜单文字
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing()
                            ? R.string.menu_sync_cancel
                            : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            // 子文件夹菜单
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            // 通话记录文件夹菜单
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }

    /**
     * 顶部选项菜单项被点击时调用，处理各菜单项的功能逻辑。
     *
     * @param item 被点击的菜单项
     * @return true 表示事件已处理
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_new_folder) {
            // 新建文件夹
            showCreateOrModifyFolderDialog(true);
            return true;
        } else if (id == R.id.menu_export_text) {
            // 导出便签为文本文件
            exportNoteToText();
            return true;
        } else if (id == R.id.menu_sync) {
            // 同步操作：已配置账号则开始/取消同步，否则跳转到设置页面
            if (isSyncMode()) {
                if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                    GTaskSyncService.startSync(this);
                } else {
                    GTaskSyncService.cancelSync(this);
                }
            } else {
                startPreferenceActivity();
            }
            return true;
        } else if (id == R.id.menu_setting) {
            // 进入设置页面
            startPreferenceActivity();
            return true;
        } else if (id == R.id.menu_new_note) {
            // 新建便签
            createNewNote();
            return true;
        } else if (id == R.id.menu_search) {
            // 触发搜索
            onSearchRequested();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 触发系统搜索框。
     *
     * @return true 表示搜索请求已处理
     */
    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, false);
        return true;
    }

    /**
     * 将便签导出为文本文件（异步执行）。
     * 根据导出结果显示对应的成功或失败提示对话框。
     *
     * 可能的结果状态：
     *   STATE_SD_CARD_UNMOUONTED —— SD 卡未挂载
     *   STATE_SUCCESS            —— 导出成功，显示文件路径
     *   STATE_SYSTEM_ERROR       —— 系统错误
     */
    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... unused) {
                // 在后台线程执行文本导出操作
                return backup.exportToText();
            }

            @Override
            protected void onPostExecute(Integer result) {
                // 根据导出结果在主线程显示对应的提示对话框
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    // SD 卡未挂载，无法导出
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(getString(R.string.failed_sdcard_export));
                    builder.setMessage(getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    // 导出成功，显示文件保存路径
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(getString(R.string.success_sdcard_export));
                    builder.setMessage(getString(R.string.format_exported_file_location,
                            backup.getExportedTextFileName(),
                            backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    // 系统错误，导出失败
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(getString(R.string.failed_sdcard_export));
                    builder.setMessage(getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }
        }.execute();
    }

    /**
     * 判断当前是否处于同步模式。
     * 通过检查设置中是否配置了同步账号名称来判断。
     *
     * @return true = 已配置同步账号，处于同步模式；false = 未配置同步账号
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 跳转到应用设置页面（NotesPreferenceActivity）。
     * 支持从嵌套 Activity 中正确跳转。
     */
    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }

    /**
     * OnListItemClickListener —— 列表项点击事件监听内部类
     *
     * 功能：
     *   处理便签列表中列表项的点击事件。
     *   在多选模式下，点击切换列表项的选中状态。
     *   在普通模式下，根据列表项类型（便签或文件夹）执行打开操作。
     */
    private class OnListItemClickListener implements OnItemClickListener {

        /**
         * 列表项被点击时调用。
         *
         * @param parent   列表视图
         * @param view     被点击的列表项视图
         * @param position 被点击项的位置
         * @param id       被点击项的数据库 ID
         */
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();

                if (mNotesListAdapter.isInChoiceMode()) {
                    // 多选模式下：点击切换该项的选中状态（仅普通便签可选中）
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }

                // 普通模式下：根据当前页面状态和列表项类型执行对应操作
                if (mState == ListEditState.NOTE_LIST) {
                    if (item.getType() == Notes.TYPE_FOLDER
                            || item.getType() == Notes.TYPE_SYSTEM) {
                        // 点击文件夹或系统文件夹：进入该文件夹
                        openFolder(item);
                    } else if (item.getType() == Notes.TYPE_NOTE) {
                        // 点击普通便签：打开编辑页面
                        openNode(item);
                    } else {
                        Log.e(TAG, "Wrong note type in NOTE_LIST");
                    }
                } else if (mState == ListEditState.SUB_FOLDER
                        || mState == ListEditState.CALL_RECORD_FOLDER) {
                    // 子文件夹或通话记录文件夹中：只能打开普通便签
                    if (item.getType() == Notes.TYPE_NOTE) {
                        openNode(item);
                    } else {
                        Log.e(TAG, "Wrong note type in SUB_FOLDER");
                    }
                }
            }
        }
    }

    /**
     * 发起异步查询，获取可作为移动目标的文件夹列表。
     * 查询条件会过滤掉回收站和当前所在文件夹，并按修改时间降序排列。
     * 若当前在子文件夹中，还会额外包含根文件夹作为可选目标。
     */
    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND "
                + NoteColumns.PARENT_ID + "<>? AND "
                + NoteColumns.ID + "<>?";

        // 若在子文件夹中，追加根文件夹作为可选目标
        selection = (mState == ListEditState.NOTE_LIST) ? selection
                : "(" + selection + ") OR ("
                + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";

        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                NoteEditActivity.PROJECTION,
                selection,
                new String[]{
                        String.valueOf(Notes.TYPE_FOLDER),    // 只查询文件夹类型
                        String.valueOf(Notes.ID_TRASH_FOLER), // 排除回收站
                        String.valueOf(mCurrentFolderId)       // 排除当前所在文件夹
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * 列表项长按事件处理。
     * 长按普通便签时进入多选模式；长按文件夹时弹出右键菜单。
     *
     * @param parent   列表视图
     * @param view     被长按的列表项视图
     * @param position 被长按项的位置
     * @param id       被长按项的数据库 ID
     * @return false 表示事件未完全消费，允许继续传递
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            // 记录当前长按的便签数据项
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();

            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE
                    && !mNotesListAdapter.isInChoiceMode()) {
                // 长按普通便签且当前不在多选模式：启动多选操作模式
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    // 将当前长按项设为已选中状态，并触发震动反馈
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                // 长按文件夹：注册右键菜单监听器，弹出文件夹操作菜单
                mNotesListView.setOnCreateContextMenuListener(
                        mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}