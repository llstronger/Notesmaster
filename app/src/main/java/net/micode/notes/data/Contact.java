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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人数据处理类
 * 用于通过电话号码查询手机系统联系人数据库，获取对应的联系人姓名，
 * 同时在内存中维护一个缓存字典以提高重复查询时的效率。
 */
public class Contact {
    // 用于缓存已查询过的电话号码及其对应的联系人姓名，键为电话号码，值为姓名
    private static HashMap<String, String> sContactCache;
    private static final String TAG = "Contact";

    // 查询联系人数据库的 SQL 条件语句模板
    // PHONE_NUMBERS_EQUAL: 用于判断电话号码是否匹配
    // Data.MIMETYPE: 确保查询的是电话号码类型的数据记录
    // RAW_CONTACT_ID: 配合 phone_lookup 表进行快速匹配过滤，模板中的 '+' 会在后续被替换为实际的最小匹配串
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
    + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
    + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取对应的联系人姓名
     *
     * @param context     上下文环境，用于获取 ContentResolver
     * @param phoneNumber 需要查询的电话号码
     * @return 如果找到联系人则返回联系人姓名，否则返回 null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 如果缓存字典还未初始化，则先实例化 HashMap
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 优先从内存缓存中获取，如果缓存中已存在该号码，直接返回对应的姓名，避免执行耗时的数据库查询
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 构建最终的查询条件，将模板中的 '+' 替换为该电话号码的 CallerID 最小匹配字符串
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        
        // 使用 ContentResolver 查询系统的联系人数据库
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,                     // 查询的系统联系人数据 URI
                new String [] { Phone.DISPLAY_NAME }, // 需要返回的列：仅获取联系人显示名称
                selection,                            // 查询条件 (WHERE 语句)
                new String[] { phoneNumber },         // 查询条件中的占位符参数（对应 ? 的位置）
                null);                                // 排序方式

        // 检查游标是否有效，并且尝试移动到第一条记录（即判断是否查询到了匹配的数据）
        if (cursor != null && cursor.moveToFirst()) {
            try {
                // 获取查询结果集中的第一列内容（索引为0），即 Phone.DISPLAY_NAME
                String name = cursor.getString(0);
                // 将查询到的号码和对应姓名存入缓存，方便下次快速读取
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                // 捕获可能出现的索引越界异常并记录错误日志
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                // 无论是否发生异常，都必须确保关闭 Cursor 以释放数据库资源，防止内存泄漏
                cursor.close();
            }
        } else {
            // 如果游标为空或没有查到任何匹配的联系人，则记录调试日志并返回 null
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}