package net.micode.notes;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * NoteDbHelper —— 数据库辅助类
 * 
 * 功能：
 *   负责便签应用数据库的创建、打开以及版本管理（升级）。
 *   该类继承自 SQLiteOpenHelper，封装了 SQLite 数据库的底层操作。
 */
public class NoteDbHelper extends SQLiteOpenHelper {

    /** 数据库文件名 */
    private static final String DB_NAME = "notes.db";

    /** 数据库版本号，当表结构发生变化时需增加此版本号 */
    private static final int DB_VERSION = 1;

    /**
     * 构造方法
     * 
     * @param context 上下文对象，用于确定数据库文件在系统中的存储路径
     */
    public NoteDbHelper(Context context) {
        // 调用父类构造器，传入数据库名和版本号
        // factory 参数为 null，表示使用系统默认的游标工厂
        super(context, DB_NAME, null, DB_VERSION);
    }

    /**
     * 当数据库第一次被创建时调用。
     * 
     * 主要任务：
     *   执行 SQL 语句创建数据表。
     *   此处的表结构包含：
     *     - _id: 主键，自增整数（Android 列表适配器通常要求主键名为 _id）
     *     - title: 便签标题，文本类型
     *     - content: 便签正文内容，文本类型
     * 
     * @param db 数据库对象
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        // 执行创建表的 SQL 语句
        db.execSQL("CREATE TABLE notes (_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, content TEXT)");
    }

    /**
     * 当数据库版本号（DB_VERSION）增加时调用。
     * 
     * 主要任务：
     *   处理旧版本数据库到新版本数据库的迁移逻辑。
     *   当前的简单逻辑是：如果表已存在，则删除旧表并重新创建（会导致数据丢失，生产环境建议使用 ALTER TABLE）。
     * 
     * @param db 数据库对象
     * @param oldVersion 旧版本号
     * @param newVersion 新版本号
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 如果数据库表 notes 已存在，则将其删除
        db.execSQL("DROP TABLE IF EXISTS notes");
        // 调用 onCreate 重新创建新结构的表
        onCreate(db);
    }
}