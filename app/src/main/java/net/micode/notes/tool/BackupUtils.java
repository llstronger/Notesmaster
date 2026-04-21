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

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 备份工具类
 *
 * <p>该类提供将便签数据导出为可读文本文件的备份功能。
 * 采用单例模式（Singleton Pattern）确保全局仅存在一个备份实例，
 * 避免多次实例化导致的资源浪费与数据冲突。</p>
 *
 * <p>主要功能：
 * <ul>
 *   <li>将便签内容（包括普通便签与通话记录便签）导出为文本文件</li>
 *   <li>将文本文件存储至外部存储（SD卡）指定目录</li>
 *   <li>通过状态码反映备份/恢复过程中的各种状态</li>
 * </ul>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 */
public class BackupUtils {

    /** 日志标签，用于 Logcat 中过滤本类日志输出 */
    private static final String TAG = "BackupUtils";

    /** 单例实例，使用 volatile 保证多线程环境下的可见性 */
    private static BackupUtils sInstance;

    /**
     * 获取 BackupUtils 的单例实例
     *
     * <p>使用 synchronized 关键字保证线程安全，防止多线程环境下
     * 重复创建实例。</p>
     *
     * @param context 应用上下文，用于访问资源与内容提供者
     * @return BackupUtils 的唯一实例
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    /**
     * 备份与恢复操作的状态码定义
     *
     * <p>以下常量用于标识备份或恢复流程中各阶段的运行状态，
     * 调用方可根据返回的状态码做出相应的 UI 提示或错误处理。</p>
     */

    /** 状态码：外部存储（SD卡）未挂载，无法进行备份操作 */
    public static final int STATE_SD_CARD_UNMOUONTED        = 0;

    /** 状态码：备份文件不存在，无法执行恢复操作 */
    public static final int STATE_BACKUP_FILE_NOT_EXIST     = 1;

    /** 状态码：备份数据格式损坏，可能被第三方程序修改 */
    public static final int STATE_DATA_DESTROIED            = 2;

    /** 状态码：系统运行时异常，导致备份或恢复操作失败 */
    public static final int STATE_SYSTEM_ERROR              = 3;

    /** 状态码：备份或恢复操作成功完成 */
    public static final int STATE_SUCCESS                   = 4;

    /** 文本导出处理器，负责具体的导出逻辑 */
    private TextExport mTextExport;

    /**
     * 私有构造函数
     *
     * <p>外部类无法直接实例化，须通过 {@link #getInstance(Context)} 获取实例。
     * 在构造时初始化文本导出处理器。</p>
     *
     * @param context 应用上下文
     */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 检查外部存储（SD卡）是否已挂载且可用
     *
     * @return 若外部存储已挂载返回 {@code true}，否则返回 {@code false}
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * 执行便签数据导出为文本文件的操作
     *
     * @return 操作结果状态码，参见本类中定义的 STATE_* 常量
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取已导出文本文件的文件名
     *
     * @return 导出文件名字符串，若未导出则返回空字符串
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取已导出文本文件所在的目录路径
     *
     * @return 导出文件的目录路径字符串，若未导出则返回空字符串
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    /**
     * 文本导出内部类
     *
     * <p>该静态内部类封装了将便签数据库内容转换并写入文本文件的全部逻辑，
     * 包括文件夹导出、单条便签导出以及通话记录便签的特殊处理。</p>
     */
    private static class TextExport {

        /**
         * 查询便签列表时使用的数据库列投影
         * 仅查询需要的列以提高查询性能
         */
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,             // 便签 ID
                NoteColumns.MODIFIED_DATE,  // 最后修改时间
                NoteColumns.SNIPPET,        // 便签摘要（用于显示文件夹名）
                NoteColumns.TYPE            // 便签类型（普通便签/文件夹）
        };

        /** NOTE_PROJECTION 中 ID 列的索引 */
        private static final int NOTE_COLUMN_ID = 0;

        /** NOTE_PROJECTION 中最后修改时间列的索引 */
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;

        /** NOTE_PROJECTION 中摘要列的索引（用于获取文件夹名称） */
        private static final int NOTE_COLUMN_SNIPPET = 2;

        /**
         * 查询便签详细数据时使用的数据库列投影
         * 涵盖内容、MIME类型及通话记录相关字段
         */
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,    // 便签内容或通话地点
                DataColumns.MIME_TYPE,  // 数据类型（普通便签/通话记录）
                DataColumns.DATA1,      // 通话日期
                DataColumns.DATA2,      // 预留字段
                DataColumns.DATA3,      // 预留字段
                DataColumns.DATA4,      // 电话号码
        };

        /** DATA_PROJECTION 中内容列的索引 */
        private static final int DATA_COLUMN_CONTENT = 0;

        /** DATA_PROJECTION 中 MIME 类型列的索引 */
        private static final int DATA_COLUMN_MIME_TYPE = 1;

        /** DATA_PROJECTION 中通话日期列的索引（对应 DATA1） */
        private static final int DATA_COLUMN_CALL_DATE = 2;

        /** DATA_PROJECTION 中电话号码列的索引（对应 DATA4） */
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;

        /** 文本导出格式数组，从资源文件中加载 */
        private final String[] TEXT_FORMAT;

        /** TEXT_FORMAT 中文件夹名称格式的索引 */
        private static final int FORMAT_FOLDER_NAME    = 0;

        /** TEXT_FORMAT 中便签日期格式的索引 */
        private static final int FORMAT_NOTE_DATE      = 1;

        /** TEXT_FORMAT 中便签内容格式的索引 */
        private static final int FORMAT_NOTE_CONTENT   = 2;

        /** 应用上下文，用于访问资源与内容提供者 */
        private Context mContext;

        /** 导出文件的文件名 */
        private String mFileName;

        /** 导出文件所在的目录路径 */
        private String mFileDirectory;

        /**
         * TextExport 构造函数
         *
         * <p>从资源文件中加载文本导出格式模板，并初始化上下文及文件信息。</p>
         *
         * @param context 应用上下文
         */
        public TextExport(Context context) {
            // 从 strings.xml 资源中加载导出格式模板数组
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 根据格式 ID 获取对应的格式化字符串模板
         *
         * @param id 格式 ID，对应 FORMAT_FOLDER_NAME、FORMAT_NOTE_DATE、FORMAT_NOTE_CONTENT
         * @return 对应的格式化字符串模板
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 将指定文件夹内的所有便签导出为文本
         *
         * <p>查询属于该文件夹的所有便签，依次输出每条便签的
         * 最后修改时间与具体内容。</p>
         *
         * @param folderId 文件夹的数据库 ID
         * @param ps       输出目标打印流
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询属于该文件夹的所有便签记录
            Cursor notesCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.PARENT_ID + "=?",
                    new String[]{folderId},
                    null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 输出便签的最后修改时间，格式化为可读的日期字符串
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE),
                                DateFormat.format(
                                        mContext.getString(R.string.format_datetime_mdhm),
                                        notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));

                        // 获取当前便签 ID，并递归导出该便签的详细内容
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                // 关闭游标，释放数据库资源
                notesCursor.close();
            }
        }

        /**
         * 将单条便签的详细数据导出到打印流
         *
         * <p>根据便签的 MIME 类型分两种情况处理：
         * <ul>
         *   <li>{@link DataConstants#CALL_NOTE}：通话记录便签，输出电话号码、通话时间和位置</li>
         *   <li>{@link DataConstants#NOTE}：普通文本便签，直接输出文本内容</li>
         * </ul>
         * </p>
         *
         * @param noteId 便签的数据库 ID
         * @param ps     输出目标打印流
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            // 查询该便签关联的所有数据记录
            Cursor dataCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION,
                    DataColumns.NOTE_ID + "=?",
                    new String[]{noteId},
                    null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        // 读取当前数据记录的 MIME 类型，以判断处理方式
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);

                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // ---- 处理通话记录便签 ----

                            // 读取电话号码、通话时间与通话地点
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            // 若电话号码不为空，则输出
                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }

                            // 输出通话时间，格式化为可读日期字符串
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                    DateFormat.format(
                                            mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));

                            // 若通话地点不为空，则输出
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }

                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            // ---- 处理普通文本便签 ----

                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            // 若内容不为空，则输出便签文本
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                // 关闭游标，释放数据库资源
                dataCursor.close();
            }

            // 在每条便签末尾写入分隔符，提高导出文件的可读性
            try {
                ps.write(new byte[]{
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 执行全量便签导出操作，将所有便签以用户可读的文本格式写入文件
         *
         * <p>导出顺序如下：
         * <ol>
         *   <li>优先导出所有文件夹及其包含的便签（含通话记录文件夹）</li>
         *   <li>再导出根目录下未归类的独立便签</li>
         * </ol>
         * </p>
         *
         * @return 操作结果状态码，参见本类中定义的 STATE_* 常量
         */
        public int exportToText() {
            // 检查外部存储是否可用
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            // 获取指向导出文件的打印流
            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            // ---- 第一阶段：导出所有文件夹及其便签 ----
            // 查询条件：类型为文件夹（且不在回收站）或为通话记录文件夹
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER,
                    null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // 获取文件夹名称：通话记录文件夹使用固定资源字符串，其余使用摘要字段
                        String folderName = "";
                        if (folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }

                        // 若文件夹名称不为空，则输出文件夹名称
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }

                        // 递归导出该文件夹下的所有便签
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                // 关闭游标，释放数据库资源
                folderCursor.close();
            }

            // ---- 第二阶段：导出根目录下的独立便签（未归入任何文件夹）----
            // 查询条件：类型为普通便签且父 ID 为 0（即根目录）
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND "
                            + NoteColumns.PARENT_ID + "=0",
                    null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        // 输出便签的最后修改时间
                        ps.println(String.format(getFormat(FORMAT_NOTE_DATE),
                                DateFormat.format(
                                        mContext.getString(R.string.format_datetime_mdhm),
                                        noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));

                        // 导出该便签的具体内容
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                // 关闭游标，释放数据库资源
                noteCursor.close();
            }

            // 关闭打印流，完成文件写入
            ps.close();

            return STATE_SUCCESS;
        }

        /**
         * 创建并获取指向导出文本文件的打印流
         *
         * <p>调用 {@link BackupUtils#generateFileMountedOnSDcard} 在 SD 卡上
         * 创建目标文件，并基于该文件构建 {@link PrintStream} 对象。</p>
         *
         * @return 成功时返回 {@link PrintStream} 实例；创建失败时返回 {@code null}
         */
        private PrintStream getExportToTextPrintStream() {
            // 在 SD 卡上生成目标导出文件
            File file = generateFileMountedOnSDcard(mContext,
                    R.string.file_path, R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }

            // 记录导出文件名与目录，供外部查询
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);

            // 创建基于文件输出流的打印流
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * 在 SD 卡指定路径下生成用于存储导出数据的文本文件
     *
     * <p>若目标目录不存在则自动创建，若目标文件不存在则自动创建新文件。
     * 文件名包含当前日期，格式由资源文件 {@code fileNameFormatResId} 定义。</p>
     *
     * @param context             应用上下文，用于获取字符串资源
     * @param filePathResId       文件目录路径的资源 ID
     * @param fileNameFormatResId 文件名格式字符串的资源 ID（包含日期占位符）
     * @return 创建成功的 {@link File} 对象；若因权限或 IO 异常导致失败则返回 {@code null}
     */
    private static File generateFileMountedOnSDcard(Context context,
                                                     int filePathResId,
                                                     int fileNameFormatResId) {
        StringBuilder sb = new StringBuilder();

        // 拼接 SD 卡根目录与应用指定子目录，构建完整目录路径
        sb.append(Environment.getExternalStorageDirectory());
        sb.append(context.getString(filePathResId));
        File filedir = new File(sb.toString());

        // 在目录路径基础上追加带日期的文件名，构建完整文件路径
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(
                        context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            // 若目录不存在，则创建目录（仅创建最后一级目录）
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            // 若文件不存在，则创建新的空文件
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException e) {
            // 安全权限异常：应用可能缺少外部存储写入权限
            e.printStackTrace();
        } catch (IOException e) {
            // IO 异常：磁盘空间不足或文件系统错误
            e.printStackTrace();
        }

        return null;
    }
}