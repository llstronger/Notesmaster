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

package net.micode.notes.data; // 声明该文件所属的包名为 net.micode.notes.data

import android.net.Uri; // 导入 Android 的 Uri 类，用于处理统一资源标识符

public class Notes { // 定义 Notes 公开类，作为数据库的全局契约类（Contract Class），包含所有常量
    public static final String AUTHORITY = "micode_notes"; // 定义 ContentProvider 的全局唯一标识符（主机名/权限）
    public static final String TAG = "Notes"; // 定义在 Logcat 中打印日志时使用的标签名
    public static final int TYPE_NOTE     = 0; // 定义节点类型常量：0 代表该节点是普通便签
    public static final int TYPE_FOLDER   = 1; // 定义节点类型常量：1 代表该节点是普通的文件夹
    public static final int TYPE_SYSTEM   = 2; // 定义节点类型常量：2 代表该节点是系统级文件夹（不可随便删除）

    /**
     * 以下 ID 是系统保留文件夹的专属标识符
     * {@link Notes#ID_ROOT_FOLDER } 是默认的根文件夹
     * {@link Notes#ID_TEMPARAY_FOLDER } 是用于存放不属于任何文件夹的“游离便签”的临时文件夹
     * {@link Notes#ID_CALL_RECORD_FOLDER} 是专门用于存储通话记录便签的文件夹
     */
    public static final int ID_ROOT_FOLDER = 0; // 根文件夹的数据库 ID 固定设为 0
    public static final int ID_TEMPARAY_FOLDER = -1; // 临时/游离文件夹的数据库 ID 固定设为 -1
    public static final int ID_CALL_RECORD_FOLDER = -2; // 通话记录专用文件夹的数据库 ID 固定设为 -2
    public static final int ID_TRASH_FOLER = -3; // 垃圾箱文件夹的数据库 ID 固定设为 -3

    public static final String INTENT_EXTRA_ALERT_DATE = "net.micode.notes.alert_date"; // Intent 传递参数的键名：闹钟提醒时间
    public static final String INTENT_EXTRA_BACKGROUND_ID = "net.micode.notes.background_color_id"; // Intent 传递参数的键名：背景颜色 ID
    public static final String INTENT_EXTRA_WIDGET_ID = "net.micode.notes.widget_id"; // Intent 传递参数的键名：绑定的桌面小部件 ID
    public static final String INTENT_EXTRA_WIDGET_TYPE = "net.micode.notes.widget_type"; // Intent 传递参数的键名：绑定的桌面小部件类型(尺寸)
    public static final String INTENT_EXTRA_FOLDER_ID = "net.micode.notes.folder_id"; // Intent 传递参数的键名：所在文件夹的 ID
    public static final String INTENT_EXTRA_CALL_DATE = "net.micode.notes.call_date"; // Intent 传递参数的键名：通话发生的日期时间戳

    public static final int TYPE_WIDGET_INVALIDE      = -1; // 桌面小部件尺寸类型常量：无效的或未初始化的类型
    public static final int TYPE_WIDGET_2X            = 0;  // 桌面小部件尺寸类型常量：表示 2x2 尺寸的桌面挂件
    public static final int TYPE_WIDGET_4X            = 1;  // 桌面小部件尺寸类型常量：表示 4x4 尺寸的桌面挂件

    public static class DataConstants { // 定义内部静态类 DataConstants，存放数据 MIME 类型常量
        public static final String NOTE = TextNote.CONTENT_ITEM_TYPE; // 引用 TextNote 的 MIME 类型作为“普通文本便签”的类型常量
        public static final String CALL_NOTE = CallNote.CONTENT_ITEM_TYPE; // 引用 CallNote 的 MIME 类型作为“通话记录便签”的类型常量
    }

    /**
     * 用于查询所有便签和文件夹记录的主 Uri
     */
    public static final Uri CONTENT_NOTE_URI = Uri.parse("content://" + AUTHORITY + "/note"); // 拼接并解析便签主表(Note)的对外访问 Uri

    /**
     * 用于查询具体便签文本和附件数据的 Uri
     */
    public static final Uri CONTENT_DATA_URI = Uri.parse("content://" + AUTHORITY + "/data"); // 拼接并解析便签数据表(Data)的对外访问 Uri

    public interface NoteColumns { // 定义 NoteColumns 接口，集中管理便签主表的所有列名（字段名）
        /**
         * 表中每一行的唯一 ID（主键）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ID = "_id"; // 定义数据库字段名：当前行的主键 _id

        /**
         * 当前便签或文件夹所属的父节点（文件夹）的 ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String PARENT_ID = "parent_id"; // 定义数据库字段名：父节点所属的 ID

        /**
         * 此便签或文件夹被创建的日期（时间戳）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date"; // 定义数据库字段名：首次创建时间

        /**
         * 此便签或文件夹最后一次被修改的日期（时间戳）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date"; // 定义数据库字段名：最近一次修改的时间

        /**
         * 设置的闹钟提醒触发日期（时间戳）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ALERTED_DATE = "alert_date"; // 定义数据库字段名：闹钟唤醒的时间

        /**
         * 文件夹的名称，或者是便签的文本片段（摘要预览）
         * <P> 类型: TEXT </P>
         */
        public static final String SNIPPET = "snippet"; // 定义数据库字段名：用来在列表页显示的文本摘要

        /**
         * 该便签绑定的桌面小部件的 ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String WIDGET_ID = "widget_id"; // 定义数据库字段名：对应的 Android Widget ID

        /**
         * 该便签绑定的桌面小部件的尺寸类型
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String WIDGET_TYPE = "widget_type"; // 定义数据库字段名：对应的 Android Widget 类型(如 2x2, 4x4)

        /**
         * 此便签使用的背景颜色的索引 ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String BG_COLOR_ID = "bg_color_id"; // 定义数据库字段名：当前便签背景色(红黄蓝绿)的 ID

        /**
         * 对于纯文本便签它没有附件；对于多媒体便签，它至少包含一个附件（标记位）
         * <P> 类型: INTEGER </P>
         */
        public static final String HAS_ATTACHMENT = "has_attachment"; // 定义数据库字段名：指示是否包含图片等附件（布尔值映射 0/1）

        /**
         * 记录该文件夹内部包含了多少条便签的数量
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String NOTES_COUNT = "notes_count"; // 定义数据库字段名：本文件夹下的便签总数缓存

        /**
         * 文件类型标志：当前记录是文件夹还是普通的便签
         * <P> 类型: INTEGER </P>
         */
        public static final String TYPE = "type"; // 定义数据库字段名：节点类型（对应上方的 TYPE_NOTE 或 TYPE_FOLDER）

        /**
         * 上一次与云端成功同步的时间戳 ID
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String SYNC_ID = "sync_id"; // 定义数据库字段名：云同步标记用的 Sync ID

        /**
         * 用于指示该记录是否在本地机器上被修改过（且未同步）的标志位
         * <P> 类型: INTEGER </P>
         */
        public static final String LOCAL_MODIFIED = "local_modified"; // 定义数据库字段名：本地修改标志（0为未修改，1为已修改待同步）

        /**
         * 在被移动到临时文件夹之前，原本所属的父级文件夹 ID
         * <P> 类型 : INTEGER </P>
         */
        public static final String ORIGIN_PARENT_ID = "origin_parent_id"; // 定义数据库字段名：被移动前的原始目录 ID 记录

        /**
         * 对应 Google Tasks 上的云端全局唯一标识符 (GTask ID)
         * <P> 类型 : TEXT </P>
         */
        public static final String GTASK_ID = "gtask_id"; // 定义数据库字段名：云端 Google Task 服务器返回的对应字符串 ID

        /**
         * 记录修改的版本号（每次修改自增）
         * <P> 类型 : INTEGER (long) </P>
         */
        public static final String VERSION = "version"; // 定义数据库字段名：数据版本号，用于辅助解决云同步冲突
    }

    public interface DataColumns { // 定义 DataColumns 接口，集中管理存放具体内容的“数据表”列名
        /**
         * 表中每一行的唯一 ID（主键）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String ID = "_id"; // 定义数据库字段名：数据表当前行的主键 _id

        /**
         * 此行数据所代表的具体 MIME 类型（如文本、图片等）
         * <P> 类型: Text </P>
         */
        public static final String MIME_TYPE = "mime_type"; // 定义数据库字段名：数据的 MIME 格式分类文本

        /**
         * 指向当前数据隶属的那条便签的引用 ID（即 Note 主表的外键）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String NOTE_ID = "note_id"; // 定义数据库字段名：外键，指向归属便签在 NoteColumns 中的主键 _id

        /**
         * 该条数据记录被创建的日期（时间戳）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CREATED_DATE = "created_date"; // 定义数据库字段名：数据的创建时间

        /**
         * 该条数据记录最近一次被修改的日期（时间戳）
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String MODIFIED_DATE = "modified_date"; // 定义数据库字段名：数据的最后修改时间

        /**
         * 数据的实际核心内容（真正手打的便签长文本就在这里）
         * <P> 类型: TEXT </P>
         */
        public static final String CONTENT = "content"; // 定义数据库字段名：存储最核心的便签正文文字


        /**
         * 通用数据列1，其具体含义由 {@link #MIME_TYPE} 指定的类型决定，用于存储 INTEGER 型数据
         * <P> 类型: INTEGER </P>
         */
        public static final String DATA1 = "data1"; // 定义数据库字段名：通用扩展预留字段 1 (整数型)

        /**
         * 通用数据列2，其具体含义由 {@link #MIME_TYPE} 指定的类型决定，用于存储 INTEGER 型数据
         * <P> 类型: INTEGER </P>
         */
        public static final String DATA2 = "data2"; // 定义数据库字段名：通用扩展预留字段 2 (整数型)

        /**
         * 通用数据列3，其具体含义由 {@link #MIME_TYPE} 指定的类型决定，用于存储 TEXT 型数据
         * <P> 类型: TEXT </P>
         */
        public static final String DATA3 = "data3"; // 定义数据库字段名：通用扩展预留字段 3 (文本型)

        /**
         * 通用数据列4，其具体含义由 {@link #MIME_TYPE} 指定的类型决定，用于存储 TEXT 型数据
         * <P> 类型: TEXT </P>
         */
        public static final String DATA4 = "data4"; // 定义数据库字段名：通用扩展预留字段 4 (文本型)

        /**
         * 通用数据列5，其具体含义由 {@link #MIME_TYPE} 指定的类型决定，用于存储 TEXT 型数据
         * <P> 类型: TEXT </P>
         */
        public static final String DATA5 = "data5"; // 定义数据库字段名：通用扩展预留字段 5 (文本型)
    }

    public static final class TextNote implements DataColumns { // 专门针对“普通文本便签”定义的数据结构配置类，实现 DataColumns 接口
        /**
         * 模式指示标志，用来标记当前的文本是否处于“打钩待办清单”模式
         * <P> 类型: Integer 1:表示处于清单模式 0:表示处于普通纯文本模式 </P>
         */
        public static final String MODE = DATA1; // 复用 DATA1 列，作为清单模式开启与否的存储字段

        public static final int MODE_CHECK_LIST = 1; // 清单模式开启时的常量值为 1

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/text_note"; // 向系统声明这类数据集合的全局 MIME 目录类型

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/text_note"; // 向系统声明单条文本便签数据的具体 MIME 类型

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/text_note"); // 生成一个专属访问文本便签数据（非主表记录）的 Uri
    }

    public static final class CallNote implements DataColumns { // 专门针对“通话记录便签”定义的数据结构配置类，实现 DataColumns 接口
        /**
         * 记录此次通话发生的日期和时间
         * <P> 类型: INTEGER (long) </P>
         */
        public static final String CALL_DATE = DATA1; // 复用 DATA1 列，用来存储通话发生时的时间戳

        /**
         * 记录此次通话对应的对方电话号码
         * <P> 类型: TEXT </P>
         */
        public static final String PHONE_NUMBER = DATA3; // 复用 DATA3 列，用来存储通话记录中的对方电话号码字符串

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_note"; // 向系统声明这类数据集合的全局 MIME 目录类型（通话便签）

        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_note"; // 向系统声明单条通话便签数据的具体 MIME 类型

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/call_note"); // 生成一个专属访问通话便签数据（非主表记录）的 Uri
    }
}