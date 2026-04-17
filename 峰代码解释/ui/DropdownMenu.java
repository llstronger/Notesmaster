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

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 自定义下拉菜单封装类
 *
 * <p>该类将 {@link Button} 与 {@link PopupMenu} 组合封装，
 * 提供一个点击按钮即可展开下拉菜单的复合 UI 组件。</p>
 *
 * <p>主要功能：
 * <ul>
 *   <li>通过传入的 {@link Button} 作为菜单触发锚点（Anchor）</li>
 *   <li>自动为按钮设置下拉图标背景样式</li>
 *   <li>从指定菜单资源文件中加载菜单项</li>
 *   <li>支持通过 {@link OnMenuItemClickListener} 监听菜单项的点击事件</li>
 *   <li>支持动态修改按钮标题文本</li>
 * </ul>
 * </p>
 *
 * <p>典型使用场景：
 * 便签列表工具栏中的排序方式选择、筛选条件切换等需要下拉选择的场景。</p>
 *
 * <p>使用示例：
 * <pre>
 *   DropdownMenu menu = new DropdownMenu(context, button, R.menu.sort_menu);
 *   menu.setOnDropdownMenuItemClickListener(item -> {
 *       // 处理菜单项点击
 *       return true;
 *   });
 * </pre>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 * @see PopupMenu
 */
public class DropdownMenu {

    /**
     * 触发下拉菜单展开的按钮控件
     *
     * <p>同时作为 {@link PopupMenu} 的锚点（Anchor View），
     * 菜单将在该按钮正下方弹出显示。</p>
     */
    private Button mButton;

    /**
     * 下拉弹出菜单实例
     *
     * <p>锚定在 {@link #mButton} 上，点击按钮时调用 {@link PopupMenu#show()} 展开。</p>
     */
    private PopupMenu mPopupMenu;

    /**
     * 弹出菜单的菜单对象
     *
     * <p>通过 {@link PopupMenu#getMenu()} 获取，用于从资源文件中加载菜单项
     * 以及通过 {@link #findItem(int)} 查找特定菜单项。</p>
     */
    private Menu mMenu;

    /**
     * 构造函数：创建并初始化下拉菜单组件
     *
     * <p>完成以下初始化工作：
     * <ol>
     *   <li>绑定触发按钮并设置下拉图标背景</li>
     *   <li>创建 {@link PopupMenu} 并将其锚定在按钮上</li>
     *   <li>从指定的菜单资源文件中填充菜单项</li>
     *   <li>为按钮注册点击监听器，点击时展开下拉菜单</li>
     * </ol>
     * </p>
     *
     * @param context  组件所在的上下文环境，用于创建 {@link PopupMenu}
     * @param button   作为菜单触发器和锚点的按钮控件，不可为 {@code null}
     * @param menuId   菜单资源文件的资源 ID（如 {@code R.menu.sort_menu}），
     *                 用于从 XML 中加载菜单项
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        // 绑定触发按钮并设置下拉图标背景样式
        mButton = button;
        mButton.setBackgroundResource(R.drawable.dropdown_icon);

        // 创建 PopupMenu，以 mButton 为锚点，菜单将在按钮附近弹出
        mPopupMenu = new PopupMenu(context, mButton);

        // 获取菜单对象并从指定资源文件中加载菜单项
        mMenu = mPopupMenu.getMenu();
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);

        // 为按钮注册点击监听器：点击按钮时展开下拉菜单
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 注册下拉菜单项点击事件监听器
     *
     * <p>当用户点击下拉菜单中的某个菜单项时，
     * 注册的 {@link OnMenuItemClickListener} 将收到对应的回调通知。</p>
     *
     * @param listener 实现了 {@link OnMenuItemClickListener} 接口的监听器对象；
     *                 传入 {@code null} 时不做任何操作（防止空指针）
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据菜单项资源 ID 查找对应的菜单项对象
     *
     * <p>可用于在运行时动态修改菜单项的标题、图标或启用状态等属性。</p>
     *
     * @param id 目标菜单项的资源 ID（如 {@code R.id.menu_sort_by_date}）
     * @return 对应的 {@link MenuItem} 对象；
     *         若未找到对应 ID 的菜单项则返回 {@code null}
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置触发按钮的显示文本
     *
     * <p>通常用于在用户选择某个菜单项后，
     * 将按钮文本更新为当前选中项的名称，以反馈当前状态。</p>
     *
     * @param title 需要显示在按钮上的文本内容，支持 {@link CharSequence} 类型
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}