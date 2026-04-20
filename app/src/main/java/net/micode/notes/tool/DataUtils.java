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

package net.micode.notes.tool;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * 数据操作工具类
 *
 * <p>该类提供一系列静态工具方法，封装了对便签数据库的常用操作，
 * 包括批量删除、移动便签、查询文件夹信息、获取通话记录等功能。</p>
 *
 * <p>所有方法均通过 {@link ContentResolver} 与数据库进行交互，
 * 遵循 Android ContentProvider 的访问规范，保证数据操作的安全性与一致性。</p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>所有方法均为静态方法，无需实例化即可使用</li>
 *   <li>数据库游标（Cursor）在使用后立即关闭，避免内存泄漏</li>
 *   <li>批量操作使用 {@link ContentProviderOperation} 保证原子性</li>
 * </ul>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 */
public class DataUtils {

    /** 日志标签，用于 Logcat 中过滤本类日志输出 */
    public static final String TAG = "DataUtils";

    /**
     * 批量删除便签
     *
     * <p>根据传入的便签 ID 集合，通过批量 ContentProvider 操作
     * 删除对应的便签记录。系统根文件夹（{@link Notes#ID_ROOT_FOLDER}）
     * 将被自动跳过，不允许删除。</p>
     *
     * <p>使用 {@link ContentProviderOperation} 批量操作以提高性能，
     * 减少多次单独删除带来的 IPC 开销。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param ids      待删除的便签 ID 集合，不可包含系统文件夹 ID
     * @return 删除成功返回 {@code true}；
     *         ids 为 null 或空集合时视为成功返回 {@code true}；
     *         删除失败返回 {@code false}
     */
    public static boolean batchDeleteNotes(ContentResolver resolver, HashSet<Long> ids) {
        // 若 ID 集合为 null，视为无需操作，直接返回成功
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }
        // 若 ID 集合为空，无删除目标，直接返回成功
        if (ids.size() == 0) {
            Log.d(TAG, "no id is in the hashset");
            return true;
        }

        // 构建批量删除操作列表
        ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();

        for (long id : ids) {
            // 系统根文件夹不允许删除，跳过并记录错误日志
            if (id == Notes.ID_ROOT_FOLDER) {
                Log.e(TAG, "Don't delete system folder root");
                continue;
            }
            // 为每个 ID 构建一条删除操作，URI 中追加具体 ID
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newDelete(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            operationList.add(builder.build());
        }

        try {
            // 批量提交删除操作，保证原子性
            ContentProviderResult[] results =
                    resolver.applyBatch(Notes.AUTHORITY, operationList);

            // 校验返回结果，判断是否删除成功
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            // 远程服务异常：ContentProvider 所在进程发生错误
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            // 操作应用异常：批量操作中某条操作执行失败
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 将单条便签移动到指定文件夹
     *
     * <p>更新便签的父文件夹 ID，同时记录原始父文件夹 ID（用于撤销操作），
     * 并标记该便签为本地已修改状态以触发同步。</p>
     *
     * @param resolver    Android 内容解析器，用于访问数据库
     * @param id          待移动的便签 ID
     * @param srcFolderId 便签当前所在的源文件夹 ID
     * @param desFolderId 便签目标文件夹 ID
     */
    public static void moveNoteToFoler(ContentResolver resolver, long id,
                                        long srcFolderId, long desFolderId) {
        ContentValues values = new ContentValues();
        // 设置新的父文件夹 ID（目标文件夹）
        values.put(NoteColumns.PARENT_ID, desFolderId);
        // 记录原始父文件夹 ID，便于撤销或同步时追溯来源
        values.put(NoteColumns.ORIGIN_PARENT_ID, srcFolderId);
        // 标记为本地已修改，触发后台同步机制
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        resolver.update(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id),
                values, null, null);
    }

    /**
     * 批量将便签移动到指定文件夹
     *
     * <p>根据传入的便签 ID 集合，通过批量 ContentProvider 操作
     * 将所有指定便签移动到目标文件夹，并标记为本地已修改。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param ids      待移动的便签 ID 集合
     * @param folderId 目标文件夹 ID
     * @return 移动成功返回 {@code true}；
     *         ids 为 null 时视为成功返回 {@code true}；
     *         移动失败返回 {@code false}
     */
    public static boolean batchMoveToFolder(ContentResolver resolver,
                                             HashSet<Long> ids, long folderId) {
        // 若 ID 集合为 null，视为无需操作，直接返回成功
        if (ids == null) {
            Log.d(TAG, "the ids is null");
            return true;
        }

        // 构建批量更新操作列表
        ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();

        for (long id : ids) {
            // 为每个便签构建更新操作：更新父文件夹 ID 并标记本地修改
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newUpdate(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, id));
            builder.withValue(NoteColumns.PARENT_ID, folderId);
            builder.withValue(NoteColumns.LOCAL_MODIFIED, 1);
            operationList.add(builder.build());
        }

        try {
            // 批量提交更新操作
            ContentProviderResult[] results =
                    resolver.applyBatch(Notes.AUTHORITY, operationList);

            // 校验返回结果，判断是否移动成功
            if (results == null || results.length == 0 || results[0] == null) {
                Log.d(TAG, "delete notes failed, ids:" + ids.toString());
                return false;
            }
            return true;
        } catch (RemoteException e) {
            // 远程服务异常：ContentProvider 所在进程发生错误
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } catch (OperationApplicationException e) {
            // 操作应用异常：批量操作中某条操作执行失败
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        }
        return false;
    }

    /**
     * 获取用户自定义文件夹的数量
     *
     * <p>统计数据库中所有用户创建的文件夹数量，
     * 排除系统文件夹（如回收站 {@link Notes#ID_TRASH_FOLER}）。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @return 用户自定义文件夹的数量；查询失败时返回 0
     */
    public static int getUserFolderCount(ContentResolver resolver) {
        // 使用 COUNT(*) 聚合函数统计符合条件的文件夹数量
        // 条件：类型为文件夹 且 父 ID 不为回收站（排除系统文件夹）
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{"COUNT(*)"},
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>?",
                new String[]{
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER)
                },
                null);

        int count = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    // 读取 COUNT(*) 的查询结果
                    count = cursor.getInt(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "get folder count failed:" + e.toString());
                } finally {
                    // 确保游标在任何情况下都被关闭，防止资源泄漏
                    cursor.close();
                }
            }
        }
        return count;
    }

    /**
     * 检查指定便签是否在数据库中可见（未被移入回收站）
     *
     * <p>根据便签 ID 和类型，判断该便签是否存在于非回收站的可见范围内。
     * 被移入回收站的便签不计入可见范围。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param noteId   待检查的便签 ID
     * @param type     便签类型（普通便签或文件夹）
     * @return 便签可见返回 {@code true}；不存在或在回收站中返回 {@code false}
     */
    public static boolean visibleInNoteDatabase(ContentResolver resolver,
                                                 long noteId, int type) {
        // 查询条件：ID 匹配 且 类型匹配 且 父 ID 不为回收站
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null,
                NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID
                        + "<>" + Notes.ID_TRASH_FOLER,
                new String[]{String.valueOf(type)},
                null);

        boolean exist = false;
        if (cursor != null) {
            // 若查询结果数量大于 0，则便签可见
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定便签是否存在于便签数据库中（不限状态）
     *
     * <p>与 {@link #visibleInNoteDatabase} 不同，该方法不过滤回收站中的便签，
     * 只要数据库中存在对应记录即返回 {@code true}。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param noteId   待检查的便签 ID
     * @return 便签存在返回 {@code true}；不存在返回 {@code false}
     */
    public static boolean existInNoteDatabase(ContentResolver resolver, long noteId) {
        // 直接通过 ID 查询，不附加任何过滤条件
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定数据记录是否存在于数据详情数据库中
     *
     * <p>用于验证便签的具体数据条目（如文本内容、通话记录）是否存在，
     * 与 {@link #existInNoteDatabase} 对应，但查询的是数据表而非便签表。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param dataId   待检查的数据记录 ID
     * @return 数据记录存在返回 {@code true}；不存在返回 {@code false}
     */
    public static boolean existInDataDatabase(ContentResolver resolver, long dataId) {
        // 通过数据 ID 查询数据详情表
        Cursor cursor = resolver.query(
                ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId),
                null, null, null, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 检查指定名称的可见文件夹是否已存在
     *
     * <p>用于创建文件夹前的重名校验，确保用户自定义文件夹名称的唯一性。
     * 仅在非回收站的可见文件夹范围内进行匹配。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param name     待检查的文件夹名称
     * @return 同名文件夹已存在返回 {@code true}；不存在返回 {@code false}
     */
    public static boolean checkVisibleFolderName(ContentResolver resolver, String name) {
        // 查询条件：类型为文件夹 且 不在回收站 且 摘要（名称）与目标名称匹配
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI, null,
                NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER
                        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
                        + " AND " + NoteColumns.SNIPPET + "=?",
                new String[]{name}, null);

        boolean exist = false;
        if (cursor != null) {
            if (cursor.getCount() > 0) {
                exist = true;
            }
            cursor.close();
        }
        return exist;
    }

    /**
     * 获取指定文件夹下所有便签关联的桌面小部件属性集合
     *
     * <p>查询某文件夹下所有便签绑定的 Widget ID 与 Widget 类型，
     * 用于在文件夹移动或删除时，同步更新对应桌面小部件的显示状态。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param folderId 目标文件夹 ID
     * @return 包含所有 Widget 属性的 HashSet；
     *         若文件夹下无便签或查询失败则返回 {@code null}
     */
    public static HashSet<AppWidgetAttribute> getFolderNoteWidget(ContentResolver resolver,
                                                                   long folderId) {
        // 只查询 Widget ID 和 Widget 类型两列，减少数据传输量
        Cursor c = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{NoteColumns.WIDGET_ID, NoteColumns.WIDGET_TYPE},
                NoteColumns.PARENT_ID + "=?",
                new String[]{String.valueOf(folderId)},
                null);

        HashSet<AppWidgetAttribute> set = null;
        if (c != null) {
            if (c.moveToFirst()) {
                set = new HashSet<AppWidgetAttribute>();
                do {
                    try {
                        // 构建 Widget 属性对象并填充 ID 与类型
                        AppWidgetAttribute widget = new AppWidgetAttribute();
                        widget.widgetId = c.getInt(0);
                        widget.widgetType = c.getInt(1);
                        set.add(widget);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e(TAG, e.toString());
                    }
                } while (c.moveToNext());
            }
            // 关闭游标，释放数据库资源
            c.close();
        }
        return set;
    }

    /**
     * 根据便签 ID 获取对应的通话电话号码
     *
     * <p>查询数据详情表，找到与指定便签关联的通话记录，
     * 并返回其中存储的电话号码。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param noteId   通话记录便签的 ID
     * @return 对应的电话号码字符串；若未找到或发生异常则返回空字符串 {@code ""}
     */
    public static String getCallNumberByNoteId(ContentResolver resolver, long noteId) {
        // 查询条件：便签 ID 匹配 且 MIME 类型为通话记录
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[]{CallNote.PHONE_NUMBER},
                CallNote.NOTE_ID + "=? AND " + CallNote.MIME_TYPE + "=?",
                new String[]{String.valueOf(noteId), CallNote.CONTENT_ITEM_TYPE},
                null);

        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 返回查询到的电话号码
                return cursor.getString(0);
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, "Get call number fails " + e.toString());
            } finally {
                // 确保游标在任何情况下都被关闭
                cursor.close();
            }
        }
        return "";
    }

    /**
     * 根据电话号码和通话时间获取对应的便签 ID
     *
     * <p>在通话记录数据表中，通过精确匹配通话时间和电话号码（使用数据库内置
     * 的 PHONE_NUMBERS_EQUAL 函数进行号码模糊匹配），查找对应的便签记录。</p>
     *
     * <p>PHONE_NUMBERS_EQUAL 函数支持不同格式电话号码的等价性比较
     * （如国际区号、短号等），避免因格式差异导致匹配失败。</p>
     *
     * @param resolver    Android 内容解析器，用于访问数据库
     * @param phoneNumber 通话记录的电话号码
     * @param callDate    通话发生的时间戳（毫秒）
     * @return 对应通话记录便签的 ID；若未找到则返回 {@code 0}
     */
    public static long getNoteIdByPhoneNumberAndCallDate(ContentResolver resolver,
                                                          String phoneNumber, long callDate) {
        // 查询条件：通话时间匹配 且 MIME 类型为通话记录 且 电话号码等价匹配
        Cursor cursor = resolver.query(Notes.CONTENT_DATA_URI,
                new String[]{CallNote.NOTE_ID},
                CallNote.CALL_DATE + "=? AND " + CallNote.MIME_TYPE + "=? AND PHONE_NUMBERS_EQUAL("
                        + CallNote.PHONE_NUMBER + ",?)",
                new String[]{
                        String.valueOf(callDate),
                        CallNote.CONTENT_ITEM_TYPE,
                        phoneNumber
                },
                null);

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                try {
                    // 返回查询到的便签 ID
                    return cursor.getLong(0);
                } catch (IndexOutOfBoundsException e) {
                    Log.e(TAG, "Get call note id fails " + e.toString());
                }
            }
            // 关闭游标，释放数据库资源
            cursor.close();
        }
        return 0;
    }

    /**
     * 根据便签 ID 获取便签的摘要内容
     *
     * <p>摘要通常为便签正文的第一行，用于列表界面的预览展示。</p>
     *
     * @param resolver Android 内容解析器，用于访问数据库
     * @param noteId   目标便签的 ID
     * @return 便签摘要字符串；若摘要为空则返回空字符串
     * @throws IllegalArgumentException 当数据库中不存在指定 ID 的便签时抛出
     */
    public static String getSnippetById(ContentResolver resolver, long noteId) {
        Cursor cursor = resolver.query(Notes.CONTENT_NOTE_URI,
                new String[]{NoteColumns.SNIPPET},
                NoteColumns.ID + "=?",
                new String[]{String.valueOf(noteId)},
                null);

        if (cursor != null) {
            String snippet = "";
            if (cursor.moveToFirst()) {
                // 读取摘要字段内容
                snippet = cursor.getString(0);
            }
            // 关闭游标，释放数据库资源
            cursor.close();
            return snippet;
        }
        // 若游标为 null，说明指定 ID 的便签不存在，抛出非法参数异常
        throw new IllegalArgumentException("Note is not found with id: " + noteId);
    }

    /**
     * 格式化便签摘要字符串
     *
     * <p>对原始摘要进行以下处理：
     * <ol>
     *   <li>去除首尾空白字符（trim）</li>
     *   <li>若摘要包含换行符，则截取第一行内容，保证单行显示</li>
     * </ol>
     * </p>
     *
     * @param snippet 原始便签摘要字符串，允许为 {@code null}
     * @return 格式化后的单行摘要字符串；若传入 {@code null} 则返回 {@code null}
     */
    public static String getFormattedSnippet(String snippet) {
        if (snippet != null) {
            // 去除首尾多余的空白字符
            snippet = snippet.trim();
            // 查找第一个换行符的位置
            int index = snippet.indexOf('\n');
            if (index != -1) {
                // 若存在换行，截取第一行内容，确保摘要为单行文本
                snippet = snippet.substring(0, index);
            }
        }
        return snippet;
    }
}