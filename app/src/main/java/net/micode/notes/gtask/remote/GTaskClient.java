/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * GTaskClient 是底层的网络请求客户端，用于直接与 Google Tasks 的 HTTP API 交互。
 * 它处理诸如身份验证（使用 AuthToken 获取 Cookie）、发送 GET/POST 请求，
 * 并以 JSON 的形式将 CRUD（创建/获取/更新/删除）动作包装后发送给服务器。
 */
public class GTaskClient {
    private static final String TAG = GTaskClient.class.getSimpleName();

    // Google Tasks 相关的访问 URL 端点
    private static final String GTASK_URL = "https://mail.google.com/tasks/";
    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";
    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    private static GTaskClient mInstance = null;

    // 移除 DefaultHttpClient 相关的字段
    // private DefaultHttpClient mHttpClient;  // 删除这行

    // 添加 Cookie 管理器
    // 用于自动维护后续 HTTP 连接中的会话 Cookie 信息，如授权后的 'GTL' cookie
    private java.net.CookieManager mCookieManager;

    private String mGetUrl;
    private String mPostUrl;
    // 客户端版本，用来确保请求和服务器端兼容性，防并发版本冲突
    private long mClientVersion;
    private boolean mLoggedin;
    private long mLastLoginTime;
    // 请求的增量 Action ID 计数器，保证 JSON Action 列表的顺序性
    private int mActionId;
    private Account mAccount;
    // 用于暂存将要批量提交更新操作的 JSON 数组队列
    private JSONArray mUpdateArray;

    private GTaskClient() {
        // 初始化 Cookie 管理器
        mCookieManager = new java.net.CookieManager();
        java.net.CookieHandler.setDefault(mCookieManager);

        mGetUrl = GTASK_GET_URL;
        mPostUrl = GTASK_POST_URL;
        mClientVersion = -1;
        mLoggedin = false;
        mLastLoginTime = 0;
        mActionId = 1;
        mAccount = null;
        mUpdateArray = null;
    }

    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    /**
     * 登录验证过程，校验当前登录状态过期情况并使用系统的 AccountManager 获取 AuthToken
     */
    public boolean login(Activity activity) {
        // ... 保持原有代码不变 ...
        final long interval = 1000 * 60 * 5; // 5分钟有效间隔时间
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
        }

        if (mLoggedin
                && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity
                .getSyncAccountName(activity))) {
            // 如果本地设置的同步账号被切换了，使其失效重新登录
            mLoggedin = false;
        }

        if (mLoggedin) {
            Log.d(TAG, "already logged in");
            return true;
        }

        mLastLoginTime = System.currentTimeMillis();
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "login google account failed");
            return false;
        }

        // 针对特殊域名的企业账户构建特殊的登录 URL 路由
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") || mAccount.name.toLowerCase()
                .endsWith("googlemail.com"))) {
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true;
        return true;
    }

    /**
     * 使用 Android 系统的 AccountManager 获取所选 Google 账号的登录 Token
     */
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        // ... 保持原有代码不变 ...
        String authToken;
        AccountManager accountManager = AccountManager.get(activity);
        Account[] accounts = accountManager.getAccountsByType("com.google");

        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }
        if (account != null) {
            mAccount = account;
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);
            if (invalidateToken) {
                accountManager.invalidateAuthToken("com.google", authToken);
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }

        return authToken;
    }

    /**
     * 包装了第一次尝试验证及验证失败后刷新 token 的重试逻辑
     */
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            authToken = loginGoogleAccount(activity, true);
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }

            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed");
                return false;
            }
        }
        return true;
    }

    /**
     * 用拿到的 AuthToken 对 Google Tasks 的接口发起 GET 请求以建立有效的 Web Cookie 并解析服务器版本号
     */
    private boolean loginGtask(String authToken) {
        int timeoutConnection = 10000;
        int timeoutSocket = 15000;

        try {
            String loginUrl = mGetUrl + "?auth=" + authToken;
            URL url = new URL(loginUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setConnectTimeout(timeoutConnection);
            connection.setReadTimeout(timeoutSocket);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String response = getResponseContent(connection);

                // 检查 Cookie
                // 判断是否成功拿到了包含身份信息的 'GTL' cookie 字段
                String cookieHeader = connection.getHeaderField("Set-Cookie");
                boolean hasAuthCookie = cookieHeader != null && cookieHeader.contains("GTL");

                if (!hasAuthCookie) {
                    Log.w(TAG, "it seems that there is no auth cookie");
                }

                // 获取客户端版本
                // 从返回的网页 HTML/JS 脚本中正则/匹配提取版本号(_setup(...)中的'v'字段)
                String jsBegin = "_setup(";
                String jsEnd = ")}</script>";
                int begin = response.indexOf(jsBegin);
                int end = response.lastIndexOf(jsEnd);
                if (begin != -1 && end != -1 && begin < end) {
                    String jsString = response.substring(begin + jsBegin.length(), end);
                    JSONObject js = new JSONObject(jsString);
                    mClientVersion = js.getLong("v");
                }

                connection.disconnect();
                return true;
            } else {
                Log.e(TAG, "HTTP GET failed with response code: " + responseCode);
                connection.disconnect();
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "loginGtask failed: " + e.toString());
            e.printStackTrace();
            return false;
        }
    }

    private int getActionId() {
        return mActionId++;
    }

    /**
     * 提取、读取并正确解码 HttpURLConnection 中的响应输入流数据
     */
    private String getResponseContent(HttpURLConnection connection) throws IOException {
        InputStream inputStream = null;
        if (connection.getResponseCode() >= HttpURLConnection.HTTP_BAD_REQUEST) {
            inputStream = connection.getErrorStream();
        } else {
            inputStream = connection.getInputStream();
        }

        // 处理并解压被 GZIP 或 Deflate 压缩过的流内容
        String contentEncoding = connection.getContentEncoding();
        if (contentEncoding != null) {
            if (contentEncoding.equalsIgnoreCase("gzip")) {
                inputStream = new GZIPInputStream(inputStream);
            } else if (contentEncoding.equalsIgnoreCase("deflate")) {
                inputStream = new InflaterInputStream(inputStream, new Inflater(true));
            }
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return response.toString();
    }

    /**
     * 封装 POST 请求的核心方法，负责将 JSON 指令包序列化并推送到远端 API，获取并返回 JSON 响应结果
     */
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            URL url = new URL(mPostUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            // Google Tasks 特定的表单请求头格式要求
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
            connection.setRequestProperty("AT", "1");
            connection.setDoOutput(true);

            // 发送数据需要以 "r=" 的参数名并通过 URLEncoder 转义封装
            String postData = "r=" + URLEncoder.encode(js.toString(), "UTF-8");

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = postData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String response = getResponseContent(connection);
                return new JSONObject(response);
            } else {
                throw new NetworkFailureException("HTTP POST failed with code: " + responseCode);
            }

        } catch (Exception e) {
            Log.e(TAG, "postRequest failed: " + e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed: " + e.getMessage());
        }
    }

    // ... 以下方法保持原有逻辑，但会使用新的 postRequest 方法 ...

    /**
     * 发送新建 Task(普通便签) 的网络请求
     */
    public void createTask(Task task) throws NetworkFailureException {
        commitUpdate(); // 提交前置所有积攒的操作保证时序正确
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            actionList.put(task.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            // 从返回的结果集中提取刚创建的元素的远程唯一标识符 GID 并写回到对象中
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create task: handing jsonobject failed");
        }
    }

    /**
     * 发送新建 TaskList(便签文件夹) 的网络请求
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            actionList.put(tasklist.getCreateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("create tasklist: handing jsonobject failed");
        }
    }

    /**
     * 将缓存数组中的更新操作合并成一个网络请求批量发给服务器，减少网络请求数和消耗
     */
    public void commitUpdate() throws NetworkFailureException {
        if (mUpdateArray != null) {
            try {
                JSONObject jsPost = new JSONObject();
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);
                postRequest(jsPost);
                mUpdateArray = null; // 成功后清空待更新列表
            } catch (JSONException e) {
                Log.e(TAG, e.toString());
                e.printStackTrace();
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }

    /**
     * 向待更新队列里追加一条更新动作记录。队列满10个时主动执行一次 commitUpdate 提交
     */
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate();
            }

            if (mUpdateArray == null)
                mUpdateArray = new JSONArray();
            mUpdateArray.put(node.getUpdateAction(getActionId()));
        }
    }

    /**
     * 移动任务操作：包括更改顺序、以及跨文件夹（源父列表到目的父列表）的移动逻辑
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid());
            // 如果是在同一个列表内的移动，指定它的排列位置（前向相邻兄弟节点）
            if (preParent == curParent && task.getPriorSibling() != null) {
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid());
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid());
            // 如果发生了跨文件夹的转移，必须附带目标列表的信息
            if (preParent != curParent) {
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed");
        }
    }

    /**
     * 执行节点的删除操作
     */
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();

            node.setDeleted(true); // 本地内存标记设定
            actionList.put(node.getUpdateAction(getActionId()));
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            postRequest(jsPost);
            mUpdateArray = null;
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed");
        }
    }

    /**
     * 发起 GET 请求拉取用户账户下所有的任务列表 (即所有文件夹)
     */
    public JSONArray getTaskLists() throws NetworkFailureException {
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            URL url = new URL(mGetUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                String response = getResponseContent(connection);

                String jsBegin = "_setup(";
                String jsEnd = ")}</script>";
                int begin = response.indexOf(jsBegin);
                int end = response.lastIndexOf(jsEnd);
                if (begin != -1 && end != -1 && begin < end) {
                    String jsString = response.substring(begin + jsBegin.length(), end);
                    JSONObject js = new JSONObject(jsString);
                    // 提取 JSON 中 key 包含 "lists" 的核心数组
                    return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);
                } else {
                    throw new ActionFailureException("Invalid response format");
                }
            } else {
                throw new NetworkFailureException("HTTP GET failed with code: " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "getTaskLists failed: " + e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("getTaskLists failed: " + e.getMessage());
        }
    }

    /**
     * 发送网络请求获取特定的一个任务列表(文件夹)里面存在的所有任务(便签数据)
     */
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        try {
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // 构建一个 "获取全部 (getall)" 的拉取请求
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false); // 不拉取远程已删除的
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            JSONObject jsResponse = postRequest(jsPost);
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    public Account getSyncAccount() {
        return mAccount;
    }

    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}