package net.micode.notes;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

/**
 * AddNoteActivity —— 新增便签页面
 *
 * 功能：
 *   提供用户输入便签标题和内容的界面，
 *   点击保存按钮后将数据写入本地 SQLite 数据库的 notes 表，
 *   保存完成后自动返回上一页面。
 *
 * 继承自：AppCompatActivity
 */
public class AddNoteActivity extends AppCompatActivity {

    /** 便签标题输入框 */
    EditText et_title;

    /** 便签内容输入框 */
    EditText et_content;

    /** 保存按钮 */
    Button btn_save;

    /** 数据库帮助类，用于创建和获取 SQLite 数据库实例 */
    NoteDbHelper dbHelper;

    /**
     * Activity 创建时调用，负责初始化页面布局和各组件。
     *
     * 主要流程：
     *   1. 加载布局文件 activity_add_note
     *   2. 绑定界面控件
     *   3. 初始化数据库帮助类
     *   4. 为保存按钮注册点击监听，实现便签的保存逻辑
     *
     * @param savedInstanceState Activity 被系统重建时传入的历史状态数据，首次创建时为 null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 加载新增便签页面对应的布局文件
        setContentView(R.layout.activity_add_note);

        // 通过 ID 绑定布局中的各个控件
        et_title   = findViewById(R.id.et_title);
        et_content = findViewById(R.id.et_content);
        btn_save   = findViewById(R.id.btn_save);

        // 初始化数据库帮助类，用于后续获取数据库连接
        dbHelper = new NoteDbHelper(this);

        // 为保存按钮注册点击事件监听器
        btn_save.setOnClickListener(v -> {

            // 获取用户在标题输入框中输入的文字
            String title = et_title.getText().toString();

            // 获取用户在内容输入框中输入的文字
            String content = et_content.getText().toString();

            // 获取可写模式的 SQLite 数据库对象
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            // 创建 ContentValues 对象，用于封装待插入的字段和对应的值
            ContentValues values = new ContentValues();
            values.put("title", title);     // 设置 title 字段的值为用户输入的标题
            values.put("content", content); // 设置 content 字段的值为用户输入的内容

            // 将封装好的数据作为一条新记录插入到 notes 表中
            db.insert("notes", null, values);

            // 数据保存完成，关闭当前页面并返回上一个页面
            finish();
        });
    }
}