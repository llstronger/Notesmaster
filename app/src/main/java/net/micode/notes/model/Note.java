/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.model;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;

// 内部数据模型类：用于在内存中暂存便签主表(Note)的属性变化和数据表(Data)的内容变化，
// 并在合适的时机统一将这些修改(Diff)同步写入到底层 SQLite 数据库中。
public class Note {
    private ContentValues mNoteDiffValues; // 核心集合：用于缓存便签主表（如背景色、提醒时间）被修改的键值对
    private NoteData mNoteData; // 核心对象：用于缓存便签具体内容数据（如文本正文、通话记录）的变化
    private static final String TAG = "Note";
    /**
     * Create a new note id for adding a new note to databases
     */
    // 静态核心方法：当用户新建一个便签时，先在数据库的 Note 主表中“占个位”，预先插入一条基础记录，并获取新生成的自增 ID
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // Create a new note in the database
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1); // 标记此新建便签为“本地已修改”，以备云同步
        values.put(NoteColumns.PARENT_ID, folderId); // 记录新建便签所属的文件夹
        Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values); // 实际插入数据库

        long noteId = 0;
        try {
            noteId = Long.valueOf(uri.getPathSegments().get(1)); // 从返回的 Uri 中解析出新插入的便签 ID
        } catch (NumberFormatException e) {
            Log.e(TAG, "Get note id error :" + e.toString());
            noteId = 0;
        }
        if (noteId == -1) {
            throw new IllegalStateException("Wrong note id:" + noteId);
        }
        return noteId;
    }

    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    // 设置便签主表的属性值（如背景色等），并自动打上“已在本地修改”的标记，更新修改时间
    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    // 设置便签具体的文本内容，委托给内部类 mNoteData 处理
    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    // 设置便签相关的通话记录内容，委托给内部类 mNoteData 处理
    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    // 判断当前便签在内存中（无论主表还是数据表）是否发生过修改（是否有暂存的 Diff）
    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    // 核心持久化方法：将内存中暂存的所有修改(Diff)真正通过 ContentResolver update 写入到底层数据库中
    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) { // 如果根本没发生过修改，则直接返回，避免无意义的数据库读写消耗
            return true;
        }

        /**
         * In theory, once data changed, the note should be updated on {@link NoteColumns#LOCAL_MODIFIED} and
         * {@link NoteColumns#MODIFIED_DATE}. For data safety, though update note fails, we also update the
         * note data info
         */
        // 1. 同步便签主表 (Note) 的修改
        if (context.getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues, null,
                null) == 0) {
            Log.e(TAG, "Update note error, should not happen");
            // Do not return, fall through
        }
        mNoteDiffValues.clear(); // 主表修改写库完成后，清空内存暂存区

        // 2. 同步便签数据表 (Data) 的修改
        if (mNoteData.isLocalModified()
                && (mNoteData.pushIntoContentResolver(context, noteId) == null)) {
            return false;
        }

        return true;
    }

    // 内部私有数据包装类：专门用于管理便签在 Data 表（如长文本正文、通话记录关联）中的具体数据变化
    private class NoteData {
        private long mTextDataId;

        private ContentValues mTextDataValues; // 暂存文本内容的修改键值对

        private long mCallDataId;

        private ContentValues mCallDataValues; // 暂存通话记录信息的修改键值对

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        // 修改通话记录数据，联动触发主表的 LOCAL_MODIFIED 标记和修改时间更新
        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        // 修改文本数据，联动触发主表的 LOCAL_MODIFIED 标记和修改时间更新
        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        // 核心同步逻辑：将内部数据表的变化（文字变化、通话记录变化）推送到 ContentProvider 中
        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * Check for safety
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            // 使用 ContentProviderOperation 列表来构建“批量数据库事务”
            ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
            ContentProviderOperation.Builder builder = null;

            // 处理文本内容的插入或更新
            if(mTextDataValues.size() > 0) {
                mTextDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mTextDataId == 0) {
                    // 如果尚未有 Data ID (说明是全新的文本)，直接通过 insert 写入数据库
                    mTextDataValues.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mTextDataValues);
                    try {
                        setTextDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new text data fail with noteId" + noteId);
                        mTextDataValues.clear();
                        return null;
                    }
                } else {
                    // 如果已有 Data ID (说明是修改已有文本)，构建一个 update 操作，并加入事务操作列表中
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId));
                    builder.withValues(mTextDataValues);
                    operationList.add(builder.build());
                }
                mTextDataValues.clear(); // 处理完毕清空内存缓存
            }

            // 处理通话记录数据的插入或更新（逻辑同上）
            if(mCallDataValues.size() > 0) {
                mCallDataValues.put(DataColumns.NOTE_ID, noteId);
                if (mCallDataId == 0) {
                    mCallDataValues.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    Uri uri = context.getContentResolver().insert(Notes.CONTENT_DATA_URI,
                            mCallDataValues);
                    try {
                        setCallDataId(Long.valueOf(uri.getPathSegments().get(1)));
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Insert new call data fail with noteId" + noteId);
                        mCallDataValues.clear();
                        return null;
                    }
                } else {
                    builder = ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId));
                    builder.withValues(mCallDataValues);
                    operationList.add(builder.build());
                }
                mCallDataValues.clear();
            }

            // 核心执行：如果有待执行的 update 事务，使用 applyBatch 一次性批量提交，不仅提高性能，且保证了事务完整性
            if (operationList.size() > 0) {
                try {
                    ContentProviderResult[] results = context.getContentResolver().applyBatch(
                            Notes.AUTHORITY, operationList);
                    return (results == null || results.length == 0 || results[0] == null) ? null
                            : ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
                } catch (RemoteException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                } catch (OperationApplicationException e) {
                    Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
                    return null;
                }
            }
            return null;
        }
    }
}