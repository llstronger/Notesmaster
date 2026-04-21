# 笔记置顶功能实现方案

这是一个为 "小米便签" (Notesmaster) 项目添加笔记置顶功能的详细实施方案。

## 1. 目标

允许用户将重要的笔记置顶，使其始终显示在笔记列表的顶部，方便快速访问。

## 2. 方案概述

我们将通过以下三个步骤来实现此功能：

1.  **修改数据库**：在笔记表 (`notes`) 中增加一个字段，用于标记笔记是否被置顶。
2.  **更新数据层**：修改 `ContentProvider` 的查询逻辑，使置顶的笔记优先显示。
3.  **更新 UI 层**：在笔记列表的菜单中添加“置顶”/“取消置顶”操作，并更新列表的排序。

## 3. 具体实施步骤

### 第 1 步：修改数据库结构

我们需要在 `notes` 表中添加一个 `is_pinned` 字段。

#### 3.1. 定义新的数据库列

**文件**: `app/src/main/java/net/micode/notes/data/Notes.java`

在 `NoteColumns` 接口中，添加一个新的列名常量。

```java
// ... existing code ...
public static final String COLUMN_WIDGET_TYPE = "widget_type";

/**
 * The flag indicating whether the note is pinned.
 * <P>Type: INTEGER (0 for false, 1 for true)</P>
 */
public static final String COLUMN_IS_PINNED = "is_pinned";

// ... existing code ...
```

#### 3.2. 升级数据库

**文件**: `app/src/main/java/net/micode/notes/data/NotesDatabaseHelper.java`

我们需要增加数据库版本号，并在 `onCreate()` 和 `onUpgrade()` 方法中处理新字段的添加。

1.  **提升数据库版本**:
    将 `DATABASE_VERSION` 从 `5` 改为 `6`。

    ```java
    private static final int DATABASE_VERSION = 6;
    ```

2.  **修改 `onCreate()`**:
    在创建 `TABLE_NOTE` 的 SQL 语句中加入 `is_pinned` 字段。

    ```sql
    db.execSQL("CREATE TABLE " + Notes.TABLE_NOTE + " ("
            + NoteColumns.ID + " INTEGER PRIMARY KEY,"
            // ... other columns ...
            + NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT 0,"
            + NoteColumns.IS_PINNED + " INTEGER NOT NULL DEFAULT 0" // 新增此行
            + ");");
    ```

3.  **修改 `onUpgrade()`**:
    添加从版本 5 到版本 6 的升级逻辑。

    ```java
    // ... in onUpgrade() method ...
    if (oldVersion < 6) {
        try {
            db.execSQL("ALTER TABLE " + Notes.TABLE_NOTE + " ADD COLUMN "
                    + NoteColumns.IS_PINNED + " INTEGER NOT NULL DEFAULT 0");
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }
    ```

### 第 2 步：更新 Content Provider 以支持排序

**文件**: `app/src/main/java/net/micode/notes/data/NotesProvider.java`

修改 `query()` 方法，使其默认将置顶笔记排在前面。

```java
// ... in query() method ...
String sortOrder = projectionMap.containsKey(Notes.NoteColumns.IS_PINNED)
        ? Notes.NoteColumns.IS_PINNED + " DESC, " + orderBy
        : orderBy;

// Get the database and run the query
SQLiteDatabase db = mHelper.getReadableDatabase();
Cursor c = qb.query(db, projection, selection, selectionArgs, groupBy, having,
        sortOrder, limit);
// ... rest of the method ...
```

**解释**:
我们构造一个新的 `sortOrder` 字符串。它会优先根据 `is_pinned` 字段进行降序 (`DESC`) 排序（1 在前，0 在后），然后再按照原来的排序规则（如修改时间）进行排序。

### 第 3 步：更新 UI 界面和操作逻辑

#### 3.1. 在菜单中添加“置顶”选项

**文件**: `app/src/main/res/menu/note_list.xml`

在笔记列表的长按上下文菜单中，添加“置顶”/“取消置顶”的菜单项。

```xml
<!-- ... existing items ... -->
<item
    android:id="@+id/menu_pin"
    android:title="@string/menu_pin" />
```

**文件**: `app/src/main/res/values/strings.xml` (以及其他语言版本)

添加对应的字符串资源。

```xml
<string name="menu_pin">置顶</string>
<string name="menu_unpin">取消置顶</string>
```

#### 3.2. 实现置顶操作逻辑

**文件**: `app/src/main/java/net/micode/notes/ui/NotesListActivity.java`

1.  **在 `onContextItemSelected()` 中处理菜单点击事件**:

    ```java
    // ... in onContextItemSelected() ...
    switch (item.getItemId()) {
        // ... other cases ...
        case R.id.menu_pin:
            pinNote(info.id, !isNotePinned(info.id)); // 切换置顶状态
            break;
        default:
            return super.onContextItemSelected(item);
    }
    return true;
    ```

2.  **动态修改菜单标题 (“置顶”/“取消置顶”)**:
    在 `onCreateContextMenu()` 方法中，根据当前笔记的状态动态设置菜单项的标题。

    ```java
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        // ... existing code ...
        if (isNotePinned(((AdapterContextMenuInfo) menuInfo).id)) {
            menu.findItem(R.id.menu_pin).setTitle(R.string.menu_unpin);
        } else {
            menu.findItem(R.id.menu_pin).setTitle(R.string.menu_pin);
        }
    }
    ```

3.  **添加 `isNotePinned` 和 `pinNote` 辅助方法**:

    ```java
    private boolean isNotePinned(long noteId) {
        Cursor c = getContentResolver().query(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                new String[]{Notes.NoteColumns.IS_PINNED},
                null, null, null);
        if (c != null && c.moveToFirst()) {
            int isPinned = c.getInt(0);
            c.close();
            return isPinned == 1;
        }
        return false;
    }

    private void pinNote(long noteId, boolean pin) {
        ContentValues values = new ContentValues();
        values.put(Notes.NoteColumns.IS_PINNED, pin ? 1 : 0);
        getContentResolver().update(
                ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId),
                values, null, null);
        // 列表会自动刷新，因为我们有 ContentObserver
    }
    ```

#### 3.3. 在 UI 上通过背景色区分置顶笔记

为了让用户能直观地看出哪些笔记是置顶的，我们为置顶的笔记列表项设置一个不同的背景色。

1.  **定义颜色**:
    在 `app/src/main/res/values/colors.xml` 中添加一个新的颜色资源。
    ```xml
    <color name="pinned_note_background">#F3E5F5</color> <!-- 一个淡淡的紫色 -->
    ```

2.  **修改 Adapter 逻辑**:
    **文件**: `app/src/main/java/net/micode/notes/ui/NotesListAdapter.java`

    在 `bindView()` 方法中，根据 `is_pinned` 字段的值来设置不同的背景。

    ```java
    // ... in bindView() ...
    if (view instanceof NotesListItem) {
        NoteItemData itemData = new NoteItemData(context, cursor);
        ((NotesListItem) view).bind(context, itemData, mChoiceMode,
                isSelectedItem(cursor.getPosition()));

        if (itemData.isPinned()) {
            view.setBackgroundColor(context.getResources().getColor(R.color.pinned_note_background));
        } else {
            // 对于非置顶笔记，恢复其原有的颜色
            view.setBackgroundResource(itemData.getBgColorId());
        }
    }
    ```
