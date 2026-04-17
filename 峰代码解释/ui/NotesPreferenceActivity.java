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

package net.micode.notes.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;

/**
 * 便签应用设置页面
 *
 * <p>该类继承自 {@link PreferenceActivity}，是便签应用的设置（偏好）界面，
 * 提供 Google Task 账户同步配置及相关功能的管理入口。</p>
 *
 * <p>主要功能：
 * <ul>
 *   <li>Google 账户绑定：首次绑定或切换同步账户</li>
 *   <li>手动触发/取消 GTask 同步操作</li>
 *   <li>显示上次同步时间及当前同步进度</li>
 *   <li>移除已绑定的同步账户并清理本地同步状态</li>
 *   <li>随机背景色开关等其他偏好设置</li>
 * </ul>
 * </p>
 *
 * <p>同步状态通过注册 {@link GTaskReceiver} 监听
 * {@link GTaskSyncService#GTASK_SERVICE_BROADCAST_NAME} 广播实时更新 UI。</p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see GTaskSyncService
 */
public class NotesPreferenceActivity extends PreferenceActivity {

    /**
     * SharedPreferences 文件名
     *
     * <p>该文件存储所有便签应用的用户偏好设置，
     * 包括同步账户名、上次同步时间等。</p>
     */
    public static final String PREFERENCE_NAME = "notes_preferences";

    /** SharedPreferences 键：已绑定的 GTask 同步账户名称 */
    public static final String PREFERENCE_SYNC_ACCOUNT_NAME = "pref_key_account_name";

    /** SharedPreferences 键：上次成功同步的时间戳（毫秒） */
    public static final String PREFERENCE_LAST_SYNC_TIME = "pref_last_sync_time";

    /**
     * SharedPreferences 键：是否开启随机背景色
     *
     * <p>值为 {@code true} 时，新建便签将随机选择背景颜色；
     * 值为 {@code false} 时使用默认颜色（黄色）。</p>
     */
    public static final String PREFERENCE_SET_BG_COLOR_KEY = "pref_key_bg_random_appear";

    /**
     * Preference XML 中同步账户分类（PreferenceCategory）的 Key
     *
     * <p>用于通过 {@link #findPreference(CharSequence)} 查找对应的偏好分类控件。</p>
     */
    private static final String PREFERENCE_SYNC_ACCOUNT_KEY = "pref_sync_account_key";

    /**
     * 跳转到系统添加账户界面时，用于过滤账户类型的 Extra Key
     *
     * <p>值为 "authorities"，传入 "gmail-ls" 以仅显示 Google 账户添加入口。</p>
     */
    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    /**
     * 同步账户设置的偏好分类控件
     *
     * <p>动态添加账户绑定 Preference 项，并在账户变更时刷新显示内容。</p>
     */
    private PreferenceCategory mAccountCategory;

    /**
     * GTask 同步服务广播接收器
     *
     * <p>监听 {@link GTaskSyncService} 发送的同步状态广播，
     * 实时更新同步按钮和同步进度文本的显示状态。</p>
     */
    private GTaskReceiver mReceiver;

    /**
     * 进入"添加账户"界面前已存在的 Google 账户列表快照
     *
     * <p>用于在用户添加新账户后返回时，
     * 通过对比前后账户列表的差异，自动将新账户设置为同步账户。</p>
     */
    private Account[] mOriAccounts;

    /**
     * 标记用户是否已通过系统界面添加了新的 Google 账户
     *
     * <p>在 {@link #onResume()} 中根据此标志决定是否自动绑定新账户。</p>
     */
    private boolean mHasAddedAccount;

    /**
     * Activity 创建时的初始化入口
     *
     * <p>完成以下初始化工作：
     * <ol>
     *   <li>启用 ActionBar 的返回导航按钮</li>
     *   <li>从 XML 资源文件加载偏好设置项</li>
     *   <li>获取同步账户偏好分类控件引用</li>
     *   <li>注册 GTask 同步状态广播接收器</li>
     *   <li>为设置列表添加自定义头部视图</li>
     * </ol>
     * </p>
     *
     * @param icicle Activity 重建时的状态保存数据，首次创建时为 null
     */
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // 启用 ActionBar 左上角的返回导航按钮（应用图标导航）
        getActionBar().setDisplayHomeAsUpEnabled(true);

        // 从 XML 资源文件中加载偏好设置项列表
        addPreferencesFromResource(R.xml.preferences);

        // 获取同步账户偏好分类控件，用于动态添加账户绑定 Preference 项
        mAccountCategory = (PreferenceCategory) findPreference(PREFERENCE_SYNC_ACCOUNT_KEY);

        // 创建并注册 GTask 同步状态广播接收器
        mReceiver = new GTaskReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(GTaskSyncService.GTASK_SERVICE_BROADCAST_NAME);
        registerReceiver(mReceiver, filter);

        // 初始化账户快照为 null（尚未进入添加账户流程）
        mOriAccounts = null;

        // 为偏好设置列表添加自定义头部视图（如标题说明等）
        View header = LayoutInflater.from(this).inflate(R.layout.settings_header, null);
        getListView().addHeaderView(header, null, true);
    }

    /**
     * Activity 从后台恢复到前台时调用
     *
     * <p>处理用户从系统添加账户界面返回的情况：
     * 若用户添加了新的 Google 账户（{@link #mHasAddedAccount} 为 true），
     * 则自动将新增账户设置为同步账户，并刷新设置界面显示。</p>
     */
    @Override
    protected void onResume() {
        super.onResume();

        // 若用户刚添加了新账户，通过对比前后账户列表自动绑定新账户
        if (mHasAddedAccount) {
            Account[] accounts = getGoogleAccounts();
            if (mOriAccounts != null && accounts.length > mOriAccounts.length) {
                // 遍历当前账户列表，找出新增的账户（在旧列表中不存在的）
                for (Account accountNew : accounts) {
                    boolean found = false;
                    for (Account accountOld : mOriAccounts) {
                        if (TextUtils.equals(accountOld.name, accountNew.name)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        // 自动将新增账户设置为同步账户
                        setSyncAccount(accountNew.name);
                        break;
                    }
                }
            }
        }

        // 刷新账户偏好项和同步按钮的显示状态
        refreshUI();
    }

    /**
     * Activity 销毁时释放资源
     *
     * <p>注销已注册的 GTask 同步状态广播接收器，
     * 防止内存泄漏和无效的广播回调。</p>
     */
    @Override
    protected void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }

    /**
     * 加载并刷新同步账户偏好设置项
     *
     * <p>清空账户偏好分类中的所有旧项，重新创建账户绑定 Preference 项。
     * 点击该 Preference 项时：
     * <ul>
     *   <li>若正在同步：显示"同步中无法修改账户"的提示</li>
     *   <li>若未绑定账户：显示账户选择对话框（首次绑定）</li>
     *   <li>若已绑定账户：显示账户变更确认对话框（修改/移除）</li>
     * </ul>
     * </p>
     */
    private void loadAccountPreference() {
        mAccountCategory.removeAll();

        // 创建账户绑定 Preference 项
        Preference accountPref = new Preference(this);
        final String defaultAccount = getSyncAccountName(this);
        accountPref.setTitle(getString(R.string.preferences_account_title));
        accountPref.setSummary(getString(R.string.preferences_account_summary));

        // 注册 Preference 点击监听器
        accountPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                if (!GTaskSyncService.isSyncing()) {
                    if (TextUtils.isEmpty(defaultAccount)) {
                        // 首次绑定账户：显示账户选择对话框
                        showSelectAccountAlertDialog();
                    } else {
                        // 已有绑定账户：显示变更确认对话框，提示数据风险
                        showChangeAccountConfirmAlertDialog();
                    }
                } else {
                    // 同步进行中：提示用户无法修改账户
                    Toast.makeText(NotesPreferenceActivity.this,
                            R.string.preferences_toast_cannot_change_account,
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        mAccountCategory.addPreference(accountPref);
    }

    /**
     * 加载并刷新同步操作按钮和上次同步时间的显示状态
     *
     * <p>根据当前同步状态（{@link GTaskSyncService#isSyncing()}）动态更新：
     * <ul>
     *   <li>同步中：按钮显示"取消同步"，同步状态文本显示当前进度</li>
     *   <li>未同步：按钮显示"立即同步"，同步状态文本显示上次同步时间
     *       （若从未同步则隐藏）</li>
     * </ul>
     * 若未绑定同步账户，同步按钮将被禁用。</p>
     */
    private void loadSyncButton() {
        Button syncButton = (Button) findViewById(R.id.preference_sync_button);
        TextView lastSyncTimeView =
                (TextView) findViewById(R.id.prefenerece_sync_status_textview);

        // 根据同步状态设置按钮文本和点击行为
        if (GTaskSyncService.isSyncing()) {
            // 同步中：按钮功能为取消同步
            syncButton.setText(getString(R.string.preferences_button_sync_cancel));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.cancelSync(NotesPreferenceActivity.this);
                }
            });
        } else {
            // 未同步：按钮功能为立即开始同步
            syncButton.setText(getString(R.string.preferences_button_sync_immediately));
            syncButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    GTaskSyncService.startSync(NotesPreferenceActivity.this);
                }
            });
        }

        // 未绑定同步账户时禁用同步按钮
        syncButton.setEnabled(!TextUtils.isEmpty(getSyncAccountName(this)));

        // 根据同步状态设置上次同步时间/进度的显示内容
        if (GTaskSyncService.isSyncing()) {
            // 同步中：显示当前同步进度字符串
            lastSyncTimeView.setText(GTaskSyncService.getProgressString());
            lastSyncTimeView.setVisibility(View.VISIBLE);
        } else {
            long lastSyncTime = getLastSyncTime(this);
            if (lastSyncTime != 0) {
                // 有历史同步记录：格式化显示上次同步时间
                lastSyncTimeView.setText(getString(
                        R.string.preferences_last_sync_time,
                        DateFormat.format(
                                getString(R.string.preferences_last_sync_time_format),
                                lastSyncTime)));
                lastSyncTimeView.setVisibility(View.VISIBLE);
            } else {
                // 从未同步：隐藏上次同步时间文本
                lastSyncTimeView.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 刷新设置界面的账户偏好项和同步按钮显示状态
     *
     * <p>在以下场景下调用以保持 UI 与当前状态同步：
     * <ul>
     *   <li>Activity 恢复（{@link #onResume()}）</li>
     *   <li>接收到 GTask 同步广播（{@link GTaskReceiver}）</li>
     *   <li>账户绑定/移除操作完成后</li>
     * </ul>
     * </p>
     */
    private void refreshUI() {
        loadAccountPreference();
        loadSyncButton();
    }

    /**
     * 显示 Google 账户选择对话框（首次绑定账户时使用）
     *
     * <p>展示设备上所有已登录的 Google 账户列表供用户选择，
     * 同时提供"添加账户"入口跳转到系统账户管理界面。
     * 用户选择账户后自动完成绑定并刷新 UI。</p>
     *
     * <p>若设备上存在已绑定的账户，对应项将被默认选中。</p>
     */
    private void showSelectAccountAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 加载并配置自定义对话框标题视图
        View titleView = LayoutInflater.from(this).inflate(
                R.layout.account_dialog_title, null);
        TextView titleTextView =
                (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(
                R.string.preferences_dialog_select_account_title));
        TextView subtitleTextView =
                (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        subtitleTextView.setText(getString(
                R.string.preferences_dialog_select_account_tips));
        dialogBuilder.setCustomTitle(titleView);
        dialogBuilder.setPositiveButton(null, null);

        // 获取当前设备上所有 Google 账户
        Account[] accounts = getGoogleAccounts();
        String defAccount = getSyncAccountName(this);

        // 保存当前账户列表快照，用于返回时检测新添加的账户
        mOriAccounts = accounts;
        mHasAddedAccount = false;

        if (accounts.length > 0) {
            // 构建账户名称列表，并标记当前已绑定的账户为选中状态
            CharSequence[] items = new CharSequence[accounts.length];
            final CharSequence[] itemMapping = items;
            int checkedItem = -1;
            int index = 0;
            for (Account account : accounts) {
                if (TextUtils.equals(account.name, defAccount)) {
                    checkedItem = index;
                }
                items[index++] = account.name;
            }

            // 设置单选列表：用户选择后立即绑定账户并关闭对话框
            dialogBuilder.setSingleChoiceItems(items, checkedItem,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setSyncAccount(itemMapping[which].toString());
                            dialog.dismiss();
                            refreshUI();
                        }
                    });
        }

        // 在对话框底部添加"添加账户"入口视图
        View addAccountView = LayoutInflater.from(this).inflate(
                R.layout.add_account_text, null);
        dialogBuilder.setView(addAccountView);

        final AlertDialog dialog = dialogBuilder.show();

        // 点击"添加账户"入口：跳转到系统账户管理界面（仅显示 Google 账户）
        addAccountView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // 标记用户进入了添加账户流程，以便 onResume 时自动绑定新账户
                mHasAddedAccount = true;
                Intent intent = new Intent("android.settings.ADD_ACCOUNT_SETTINGS");
                // 过滤仅显示 Google 账户（gmail-ls）的添加入口
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[]{"gmail-ls"});
                startActivityForResult(intent, -1);
                dialog.dismiss();
            }
        });
    }

    /**
     * 显示切换同步账户的确认对话框
     *
     * <p>当用户已绑定同步账户后再次点击账户设置项时显示，
     * 提示切换账户可能带来的数据风险，并提供三个操作选项：
     * <ul>
     *   <li>切换账户：打开账户选择对话框重新选择</li>
     *   <li>移除账户：解除绑定并清理本地同步数据</li>
     *   <li>取消：关闭对话框不做任何操作</li>
     * </ul>
     * </p>
     */
    private void showChangeAccountConfirmAlertDialog() {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);

        // 加载并配置自定义对话框标题视图，显示当前已绑定的账户名称
        View titleView = LayoutInflater.from(this).inflate(
                R.layout.account_dialog_title, null);
        TextView titleTextView =
                (TextView) titleView.findViewById(R.id.account_dialog_title);
        titleTextView.setText(getString(
                R.string.preferences_dialog_change_account_title,
                getSyncAccountName(this)));
        TextView subtitleTextView =
                (TextView) titleView.findViewById(R.id.account_dialog_subtitle);
        // 显示切换账户的风险警告信息
        subtitleTextView.setText(getString(
                R.string.preferences_dialog_change_account_warn_msg));
        dialogBuilder.setCustomTitle(titleView);

        // 构建操作选项列表
        CharSequence[] menuItemArray = new CharSequence[]{
                getString(R.string.preferences_menu_change_account),  // 切换账户
                getString(R.string.preferences_menu_remove_account),  // 移除账户
                getString(R.string.preferences_menu_cancel)           // 取消
        };

        dialogBuilder.setItems(menuItemArray, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    // 切换账户：显示账户选择对话框
                    showSelectAccountAlertDialog();
                } else if (which == 1) {
                    // 移除账户：解除绑定并刷新 UI
                    removeSyncAccount();
                    refreshUI();
                }
                // which == 2（取消）：对话框自动关闭，无需额外处理
            }
        });
        dialogBuilder.show();
    }

    /**
     * 获取设备上所有已登录的 Google 账户列表
     *
     * @return 所有 Google 类型账户（{@code com.google}）的数组；
     *         若无 Google 账户则返回空数组
     */
    private Account[] getGoogleAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        return accountManager.getAccountsByType("com.google");
    }

    /**
     * 将指定账户设置为 GTask 同步账户
     *
     * <p>若新账户与当前绑定账户不同，则执行以下操作：
     * <ol>
     *   <li>将新账户名持久化到 SharedPreferences</li>
     *   <li>将上次同步时间重置为 0</li>
     *   <li>在后台线程中清理本地所有便签的 GTask 同步 ID</li>
     *   <li>显示账户设置成功的 Toast 提示</li>
     * </ol>
     * </p>
     *
     * @param account 需要设置为同步账户的 Google 账户名称；
     *                传入 {@code null} 时存储空字符串
     */
    private void setSyncAccount(String account) {
        if (!getSyncAccountName(this).equals(account)) {
            SharedPreferences settings = getSharedPreferences(
                    PREFERENCE_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = settings.edit();

            // 持久化新的同步账户名称
            if (account != null) {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, account);
            } else {
                editor.putString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
            }
            editor.commit();

            // 重置上次同步时间（切换账户后需重新同步）
            setLastSyncTime(this, 0);

            // 在后台线程中清理本地便签的 GTask ID 和同步 ID
            // 避免在主线程执行数据库操作，防止 ANR
            new Thread(new Runnable() {
                public void run() {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.GTASK_ID, "");  // 清空 GTask ID
                    values.put(NoteColumns.SYNC_ID, 0);    // 重置同步 ID
                    getContentResolver().update(
                            Notes.CONTENT_NOTE_URI, values, null, null);
                }
            }).start();

            // 显示账户设置成功的提示
            Toast.makeText(NotesPreferenceActivity.this,
                    getString(R.string.preferences_toast_success_set_accout, account),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 移除当前绑定的同步账户并清理相关数据
     *
     * <p>执行以下操作：
     * <ol>
     *   <li>从 SharedPreferences 中删除同步账户名和上次同步时间</li>
     *   <li>在后台线程中清理本地所有便签的 GTask 同步 ID</li>
     * </ol>
     * </p>
     */
    private void removeSyncAccount() {
        SharedPreferences settings = getSharedPreferences(
                PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();

        // 从 SharedPreferences 中移除同步账户名和上次同步时间记录
        if (settings.contains(PREFERENCE_SYNC_ACCOUNT_NAME)) {
            editor.remove(PREFERENCE_SYNC_ACCOUNT_NAME);
        }
        if (settings.contains(PREFERENCE_LAST_SYNC_TIME)) {
            editor.remove(PREFERENCE_LAST_SYNC_TIME);
        }
        editor.commit();

        // 在后台线程中清理本地便签的 GTask 关联信息
        new Thread(new Runnable() {
            public void run() {
                ContentValues values = new ContentValues();
                values.put(NoteColumns.GTASK_ID, "");  // 清空 GTask ID
                values.put(NoteColumns.SYNC_ID, 0);    // 重置同步 ID
                getContentResolver().update(
                        Notes.CONTENT_NOTE_URI, values, null, null);
            }
        }).start();
    }

    /**
     * 静态方法：获取当前绑定的 GTask 同步账户名称
     *
     * <p>从应用专属的 SharedPreferences 文件（{@link #PREFERENCE_NAME}）中
     * 读取已保存的同步账户名。该方法为静态方法，可在其他类中直接调用。</p>
     *
     * @param context 应用上下文，用于访问 SharedPreferences
     * @return 已绑定的同步账户名称；未绑定时返回空字符串 {@code ""}
     */
    public static String getSyncAccountName(Context context) {
        SharedPreferences settings = context.getSharedPreferences(
                PREFERENCE_NAME, Context.MODE_PRIVATE);
        return settings.getString(PREFERENCE_SYNC_ACCOUNT_NAME, "");
    }

    /**
     * 静态方法：保存上次成功同步的时间戳
     *
     * <p>在同步完成后由 {@link GTaskSyncService} 调用，
     * 将同步完成时间持久化到 SharedPreferences，
     * 用于在设置界面中显示"上次同步时间"。</p>
     *
     * @param context 应用上下文，用于访问 SharedPreferences
     * @param time    同步完成时间戳（毫秒）；传入 0 表示重置（账户切换时使用）
     */
    public static void setLastSyncTime(Context context, long time) {
        SharedPreferences settings = context.getSharedPreferences(
                PREFERENCE_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(PREFERENCE_LAST_SYNC_TIME, time);
        editor.commit();
    }

    /**
     * 静态方法：获取上次成功同步的时间戳
     *
     * <p>从 SharedPreferences 中读取上次同步时间，
     * 用于在设置界面中格式化显示"上次同步时间"。</p>
     *
     * @param context 应用上下文，用于访问 SharedPreferences
     * @return 上次成功同步的时间戳（毫秒）；从未同步时返回 {@code 0}
     */
    public static long getLastSyncTime(Context context) {
        SharedPreferences settings = context.getSharedPreferences(
                PREFERENCE_NAME, Context.MODE_PRIVATE);
        return settings.getLong(PREFERENCE_LAST_SYNC_TIME, 0);
    }

    /**
     * GTask 同步状态广播接收器内部类
     *
     * <p>监听 {@link GTaskSyncService} 发送的同步状态广播，
     * 在收到广播时：
     * <ul>
     *   <li>调用 {@link #refreshUI()} 刷新整个设置界面</li>
     *   <li>若广播携带了同步进度信息，实时更新同步状态文本的显示内容</li>
     * </ul>
     * </p>
     */
    private class GTaskReceiver extends BroadcastReceiver {

        /**
         * 接收 GTask 同步状态广播并更新 UI
         *
         * @param context 广播接收器的运行上下文
         * @param intent  携带同步状态信息的广播 Intent，
         *                包含是否正在同步（{@link GTaskSyncService#GTASK_SERVICE_BROADCAST_IS_SYNCING}）
         *                和当前进度文本（{@link GTaskSyncService#GTASK_SERVICE_BROADCAST_PROGRESS_MSG}）
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            // 刷新账户偏好项和同步按钮的显示状态
            refreshUI();

            // 若正在同步，更新同步进度文本为广播中携带的最新进度信息
            if (intent.getBooleanExtra(
                    GTaskSyncService.GTASK_SERVICE_BROADCAST_IS_SYNCING, false)) {
                TextView syncStatus =
                        (TextView) findViewById(R.id.prefenerece_sync_status_textview);
                syncStatus.setText(intent.getStringExtra(
                        GTaskSyncService.GTASK_SERVICE_BROADCAST_PROGRESS_MSG));
            }
        }
    }

    /**
     * 处理 ActionBar 和选项菜单的点击事件
     *
     * <p>当用户点击 ActionBar 左上角的返回（Home）按钮时，
     * 跳转回便签列表界面（{@link NotesListActivity}），
     * 并清除中间所有 Activity 的返回栈（{@link Intent#FLAG_ACTIVITY_CLEAR_TOP}）。</p>
     *
     * @param item 被点击的菜单项
     * @return 事件已处理返回 {@code true}；未处理返回 {@code false}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // 点击 ActionBar 返回按钮：跳转到便签列表页并清空中间的 Activity 栈
                Intent intent = new Intent(this, NotesListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                return true;
            default:
                return false;
        }
    }
}