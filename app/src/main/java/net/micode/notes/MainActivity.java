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

public class MainActivity extends AppCompatActivity {

    ListView listView;
    NoteDbHelper dbHelper;
    SQLiteDatabase db;
    Cursor cursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.list_view);
        dbHelper = new NoteDbHelper(this);
        db = dbHelper.getWritableDatabase();

        showNotes();

        // 新建便签
        findViewById(R.id.btn_add).setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddNoteActivity.class);
            startActivity(intent);
        });

        // ======================
        // 短点击 = 进入【修改】
        // ======================
        listView.setOnItemClickListener((parent, view, position, id) -> {
            // 获取当前便签数据
            Cursor c = (Cursor) parent.getItemAtPosition(position);
            String title = c.getString(c.getColumnIndex("title"));
            String content = c.getString(c.getColumnIndex("content"));

            // 跳转到编辑页面
            Intent intent = new Intent(MainActivity.this, EditNoteActivity.class);
            intent.putExtra("id", id);
            intent.putExtra("title", title);
            intent.putExtra("content", content);
            startActivity(intent);
        });

        // ======================
        // 长按 = 弹出【确认删除】
        // ======================
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("删除便签")
                    .setMessage("确定要删除吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        db.delete("notes", "_id=?", new String[]{String.valueOf(id)});
                        showNotes();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        showNotes();
    }

    private void showNotes() {
        cursor = db.query("notes", null, null, null, null, null, null);
        SimpleCursorAdapter adapter = new SimpleCursorAdapter(
                this,
                android.R.layout.simple_list_item_2,
                cursor,
                new String[]{"title", "content"},
                new int[]{android.R.id.text1, android.R.id.text2}
        );
        listView.setAdapter(adapter);
    }
}

