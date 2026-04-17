package net.micode.notes;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

/**
 * EditNoteActivity —— 编辑便签页面
 *
 * 功能：
 *   接收上一页面传递过来的便签 ID、标题和内容，
 *   在输入框中显示原有内容供用户修改，
 *   点击保存按钮后将修改后的数据更新到本地 SQLite 数据库的 notes 表中，
 *   保存完成后自动返回上一页面。
 *
 * 继承自：AppCompatActivity
 */
public class EditNoteActivity extends AppCompatActivity {

    /** 便签标题输入框 */
    EditText et_title;

    /** 便签内容输入框 */
    EditText et_content;

    /** 保存按钮 */
    Button btn_save;

    /** 数据库帮助类，用于创建和获取 SQLite 数据库实例 */
    NoteDbHelper dbHelper;

    /** 当前正在编辑的便签 ID，用于定位数据库中对应的记录 */
    long noteId;

    /**
     * Activity 创建时调用，负责初始化页面布局和各组件。
     *
     * 主要流程：
     *   1. 加载布局文件 activity_edit_note
     *   2. 绑定界面控件
     *   3. 初始化数据库帮助类
     *   4. 从 Intent 中获取上一页面传递的便签 ID、标题和内容
     *   5. 将原有标题和内容显示在输入框中
     *   6. 为保存按钮注册点击监听，实现便签的更新逻辑
     *
     * @param savedInstanceState Activity 被系统重建时传入的历史状态数据，首次创建时为 null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 加载编辑便签页面对应的布局文件
        setContentView(R.layout.activity_edit_note);

        // 通过 ID 绑定布局中的各个控件
        et_title   = findViewById(R.id.et_title);
        et_content = findViewById(R.id.et_content);
        btn_save   = findViewById(R.id.btn_save);

        // 初始化数据库帮助类，用于后续获取数据库连接
        dbHelper = new NoteDbHelper(this);

        // 从 Intent 中获取上一页面传递过来的便签 ID
        // 若未传入则默认为 -1，表示无效 ID
        noteId = getIntent().getLongExtra("id", -1);

        // 从 Intent 中获取上一页面传递过来的便签原有标题和内容
        String title   = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");

        // 将原有标题和内容填入输入框，供用户查看和修改
        et_title.setText(title);
        et_content.setText(content);

        // 为保存按钮注册点击事件监听器
        btn_save.setOnClickListener(v -> {

            // 获取用户修改后的标题
            String newTitle   = et_title.getText().toString();

            // 获取用户修改后的内容
            String newContent = et_content.getText().toString();

            // 获取可写模式的 SQLite 数据库对象
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            // 创建 ContentValues 对象，封装需要更新的字段和对应的新值
            ContentValues values = new ContentValues();
            values.put("title", newTitle);     // 设置 title 字段更新为用户修改后的标题
            values.put("content", newContent); // 设置 content 字段更新为用户修改后的内容

            // 根据便签 ID 定位到 notes 表中对应的记录，并执行更新操作
            // "_id=?" 为条件，String.valueOf(noteId) 为对应的参数值
            db.update("notes", values, "_id=?", new String[]{String.valueOf(noteId)});

            // 数据更新完成，关闭当前页面并返回上一个页面
            finish();
        });
    }
}