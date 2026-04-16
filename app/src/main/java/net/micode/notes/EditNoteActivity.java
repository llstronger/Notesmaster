package net.micode.notes;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class EditNoteActivity extends AppCompatActivity {

    EditText et_title, et_content;
    Button btn_save;
    NoteDbHelper dbHelper;
    long noteId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_note);

        et_title = findViewById(R.id.et_title);
        et_content = findViewById(R.id.et_content);
        btn_save = findViewById(R.id.btn_save);
        dbHelper = new NoteDbHelper(this);

        // 获取传过来的便签ID
        noteId = getIntent().getLongExtra("id", -1);
        String title = getIntent().getStringExtra("title");
        String content = getIntent().getStringExtra("content");

        // 显示原有内容
        et_title.setText(title);
        et_content.setText(content);

        // 保存修改
        btn_save.setOnClickListener(v -> {
            String newTitle = et_title.getText().toString();
            String newContent = et_content.getText().toString();

            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("title", newTitle);
            values.put("content", newContent);

            db.update("notes", values, "_id=?", new String[]{String.valueOf(noteId)});
            finish();
        });
    }
}