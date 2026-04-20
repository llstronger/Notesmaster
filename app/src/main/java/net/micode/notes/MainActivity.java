package net.micode.notes;

import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity —— 应用主页面
 *
 * 功能：
 *   以列表形式展示所有已保存的便签，
 *   支持新建便签、点击便签进入编辑页面、长按便签弹出删除确认框三种操作。
 *   每次页面恢复显示时自动刷新便签列表，保证数据与数据库保持一致。
 *
 * 继承自：AppCompatActivity
 */
public class MainActivity extends AppCompatActivity {

    /** 用于展示便签列表的 ListView 控件 */
    ListView listView;

    /** 数据库帮助类，用于创建和获取 SQLite 数据库实例 */
    NoteDbHelper dbHelper;

    /** SQLite 数据库对象，用于执行增删改查操作 */
    SQLiteDatabase db;

    /** 数据库查询结果游标，指向 notes 表中的查询结果集 */
    Cursor cursor;

    /**
     * Activity 创建时调用，负责初始化页面布局和各组件。
     *
     * 主要流程：
     *   1. 加载布局文件 activity_main
     *   2. 绑定 ListView 控件
     *   3. 初始化数据库帮助类并获取数据库连接
     *   4. 调用 showNotes() 加载并展示便签列表
     *   5. 为新建按钮注册点击监听，跳转到 AddNoteActivity
     *   6. 为列表项注册短按监听，跳转到 EditNoteActivity 进行编辑
     *   7. 为列表项注册长按监听，弹出删除确认对话框
     *
     * @param savedInstanceState Activity 被系统重建时传入的历史状态数据，首次创建时为 null
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 加载主页面对应的布局文件
        setContentView(R.layout.activity_main);

        // 通过 ID 绑定布局中的 ListView 控件
        listView = findViewById(R.id.list_view);

        // 初始化数据库帮助类
        dbHelper = new NoteDbHelper(this);

        // 获取可写模式的 SQLite 数据库对象
        db = dbHelper.getWritableDatabase();

        // 初次加载页面时查询并展示所有便签
        showNotes();

        // 为"新建便签"按钮注册点击事件监听器
        findViewById(R.id.btn_add).setOnClickListener(v -> {
            // 跳转到新增便签页面
            Intent intent = new Intent(MainActivity.this, AddNoteActivity.class);
            startActivity(intent);
        });

        // =============================================
        // 短按列表项 = 进入编辑页面
        // =============================================
        listView.setOnItemClickListener((parent, view, position, id) -> {

            // 获取当前被点击列表项对应的 Cursor 对象
            Cursor c = (Cursor) parent.getItemAtPosition(position);

            // 从 Cursor 中读取当前便签的标题和内容
            String title   = c.getString(c.getColumnIndex("title"));
            String content = c.getString(c.getColumnIndex("content"));

            // 跳转到编辑便签页面，并将便签 ID、标题、内容一并传递过去
            Intent intent = new Intent(MainActivity.this, EditNoteActivity.class);
            intent.putExtra("id", id);           // 传递便签 ID，用于定位数据库记录
            intent.putExtra("title", title);     // 传递原有标题，用于回显
            intent.putExtra("content", content); // 传递原有内容，用于回显
            startActivity(intent);
        });

        // =============================================
        // 长按列表项 = 弹出删除确认对话框
        // =============================================
        listView.setOnItemLongClickListener((parent, view, position, id) -> {

            // 构建并显示删除确认对话框
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("删除便签")       // 设置对话框标题
                    .setMessage("确定要删除吗？") // 设置对话框提示内容
                    .setPositiveButton("确定", (dialog, which) -> {
                        // 用户点击"确定"后，根据便签 ID 从 notes 表中删除对应记录
                        db.delete("notes", "_id=?", new String[]{String.valueOf(id)});
                        // 删除完成后刷新列表，使界面与数据库保持一致
                        showNotes();
                    })
                    .setNegativeButton("取消", null) // 用户点击"取消"则关闭对话框，不执行任何操作
                    .show();

            // 返回 true 表示该长按事件已被消费，不再继续触发短按事件
            return true;
        });
    }

    /**
     * Activity 从后台恢复到前台时调用。
     *
     * 功能：
     *   每次页面重新显示时刷新便签列表，
     *   确保从编辑页面或新增页面返回后，列表内容与数据库保持一致。
     */
    @Override
    protected void onResume() {
        super.onResume();
        // 页面恢复时重新查询数据库并刷新列表
        showNotes();
    }

    /**
     * 从数据库中查询所有便签，并将结果绑定到 ListView 上进行展示。
     *
     * 主要流程：
     *   1. 查询 notes 表中的所有记录，结果存入 Cursor
     *   2. 使用 SimpleCursorAdapter 将 Cursor 数据映射到列表项布局
     *   3. 将适配器设置到 ListView 上完成展示
     */
    private void showNotes() {
        // 查询 notes 表中的所有记录，返回包含全部字段的 Cursor
        cursor = db.query("notes", null, null, null, null, null, null);

        // 创建 SimpleCursorAdapter，将查询结果映射到系统自带的两行列表项布局
        // simple_list_item_2：系统内置布局，包含 text1（主文字）和 text2（副文字）两个控件
        // 将 title 字段映射到 text1（显示为主标题），content 字段映射到 text2（显示为副标题）
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                android.R.layout.simple_list_item_2,          // 列表项布局
                cursor,                                        // 数据来源
                new String[]{"title", "content"},             // 数据库字段名
                new int[]{android.R.id.text1, android.R.id.text2} // 对应的控件 ID
        );

        // 将适配器绑定到 ListView，完成数据展示
        listView.setAdapter(adapter);
    }
}