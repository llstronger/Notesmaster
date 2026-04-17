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

package net.micode.notes.tool;

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析工具类
 *
 * <p>该类集中管理便签应用中所有与 UI 资源相关的常量与解析方法，
 * 包括便签背景颜色、字体大小、列表项背景以及桌面小部件背景资源的映射关系。</p>
 *
 * <p>类结构说明：
 * <ul>
 *   <li>外部类：定义颜色索引常量、字体大小常量及默认值</li>
 *   <li>{@link NoteBgResources}：便签编辑页面背景资源管理</li>
 *   <li>{@link NoteItemBgResources}：便签列表项背景资源管理</li>
 *   <li>{@link WidgetBgResources}：桌面小部件背景资源管理</li>
 *   <li>{@link TextAppearanceResources}：文本样式资源管理</li>
 * </ul>
 * </p>
 *
 * <p>设计原则：
 * <ul>
 *   <li>使用整型索引统一映射颜色与资源，降低耦合度</li>
 *   <li>所有资源数组与颜色索引一一对应，顺序严格保持一致</li>
 *   <li>通过静态内部类对不同场景的资源进行分组管理</li>
 * </ul>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 */
public class ResourceParser {

    // ========================================================================
    // 便签背景颜色索引常量
    // 以下常量作为颜色的整型索引，用于从各资源数组中检索对应的 Drawable 资源
    // 索引顺序须与各内部类中资源数组的定义顺序严格一致
    // ========================================================================

    /** 颜色索引：黄色 */
    public static final int YELLOW  = 0;

    /** 颜色索引：蓝色 */
    public static final int BLUE    = 1;

    /** 颜色索引：白色 */
    public static final int WHITE   = 2;

    /** 颜色索引：绿色 */
    public static final int GREEN   = 3;

    /** 颜色索引：红色 */
    public static final int RED     = 4;

    /**
     * 默认背景颜色索引
     *
     * <p>当用户未在设置中开启随机背景色时，
     * 新建便签将使用该颜色（黄色）作为默认背景。</p>
     */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // ========================================================================
    // 便签字体大小索引常量
    // 以下常量作为字体大小的整型索引，用于从文本样式资源数组中检索对应样式
    // ========================================================================

    /** 字体大小索引：小号字体 */
    public static final int TEXT_SMALL  = 0;

    /** 字体大小索引：中号字体 */
    public static final int TEXT_MEDIUM = 1;

    /** 字体大小索引：大号字体 */
    public static final int TEXT_LARGE  = 2;

    /** 字体大小索引：超大号字体 */
    public static final int TEXT_SUPER  = 3;

    /**
     * 默认字体大小索引
     *
     * <p>当用户未设置字体大小偏好时，便签编辑页面将使用中号字体作为默认显示大小。</p>
     */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    // ========================================================================
    // 便签编辑页面背景资源管理内部类
    // ========================================================================

    /**
     * 便签编辑页面背景资源管理类
     *
     * <p>管理便签编辑界面中正文区域与标题区域的背景 Drawable 资源，
     * 提供根据颜色索引获取对应资源 ID 的静态方法。</p>
     *
     * <p>资源数组与颜色索引的对应顺序：
     * 黄色(0) → 蓝色(1) → 白色(2) → 绿色(3) → 红色(4)</p>
     */
    public static class NoteBgResources {

        /**
         * 便签编辑正文区域背景资源数组
         * 按颜色索引顺序存储各颜色对应的编辑区背景 Drawable 资源 ID
         */
        private final static int[] BG_EDIT_RESOURCES = new int[]{
                R.drawable.edit_yellow,  // 黄色背景
                R.drawable.edit_blue,    // 蓝色背景
                R.drawable.edit_white,   // 白色背景
                R.drawable.edit_green,   // 绿色背景
                R.drawable.edit_red      // 红色背景
        };

        /**
         * 便签编辑标题区域背景资源数组
         * 按颜色索引顺序存储各颜色对应的标题栏背景 Drawable 资源 ID
         */
        private final static int[] BG_EDIT_TITLE_RESOURCES = new int[]{
                R.drawable.edit_title_yellow,  // 黄色标题背景
                R.drawable.edit_title_blue,    // 蓝色标题背景
                R.drawable.edit_title_white,   // 白色标题背景
                R.drawable.edit_title_green,   // 绿色标题背景
                R.drawable.edit_title_red      // 红色标题背景
        };

        /**
         * 根据颜色索引获取便签编辑正文区域的背景资源 ID
         *
         * @param id 颜色索引，对应 {@link ResourceParser} 中定义的颜色常量
         *           （如 {@link ResourceParser#YELLOW}、{@link ResourceParser#BLUE} 等）
         * @return 对应颜色的编辑正文背景 Drawable 资源 ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 根据颜色索引获取便签编辑标题区域的背景资源 ID
         *
         * @param id 颜色索引，对应 {@link ResourceParser} 中定义的颜色常量
         * @return 对应颜色的标题栏背景 Drawable 资源 ID
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取新建便签的默认背景颜色索引
     *
     * <p>根据用户在设置页面（{@link NotesPreferenceActivity}）中的偏好决定：
     * <ul>
     *   <li>若用户开启了随机背景色设置，则在所有可用颜色中随机选取一个</li>
     *   <li>否则返回系统默认颜色索引（黄色 {@link #BG_DEFAULT_COLOR}）</li>
     * </ul>
     * </p>
     *
     * @param context 应用上下文，用于读取 SharedPreferences 中的用户偏好设置
     * @return 背景颜色索引，可直接用于 {@link NoteBgResources} 中的资源查找方法
     */
    public static int getDefaultBgId(Context context) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // 用户已开启随机背景色：在所有可用背景资源中随机选取一个颜色索引
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            // 用户未开启随机背景色：返回系统默认颜色（黄色）
            return BG_DEFAULT_COLOR;
        }
    }

    // ========================================================================
    // 便签列表项背景资源管理内部类
    // ========================================================================

    /**
     * 便签列表项背景资源管理类
     *
     * <p>管理便签列表界面中各列表项的背景 Drawable 资源。
     * 为了实现列表的圆角分组视觉效果，根据列表项在组中的位置，
     * 分为四种类型：</p>
     * <ul>
     *   <li>首项（First）：组内第一条，顶部圆角</li>
     *   <li>末项（Last）：组内最后一条，底部圆角</li>
     *   <li>单项（Single）：该组只有一条，四角均圆角</li>
     *   <li>普通项（Normal）：组内中间条目，无圆角</li>
     * </ul>
     *
     * <p>资源数组与颜色索引的对应顺序：
     * 黄色(0) → 蓝色(1) → 白色(2) → 绿色(3) → 红色(4)</p>
     */
    public static class NoteItemBgResources {

        /**
         * 列表首项背景资源数组（顶部圆角样式）
         * 用于同色系分组中的第一个列表项
         */
        private final static int[] BG_FIRST_RESOURCES = new int[]{
                R.drawable.list_yellow_up,   // 黄色首项背景
                R.drawable.list_blue_up,     // 蓝色首项背景
                R.drawable.list_white_up,    // 白色首项背景
                R.drawable.list_green_up,    // 绿色首项背景
                R.drawable.list_red_up       // 红色首项背景
        };

        /**
         * 列表普通项背景资源数组（无圆角样式）
         * 用于同色系分组中的中间列表项
         */
        private final static int[] BG_NORMAL_RESOURCES = new int[]{
                R.drawable.list_yellow_middle,  // 黄色普通项背景
                R.drawable.list_blue_middle,    // 蓝色普通项背景
                R.drawable.list_white_middle,   // 白色普通项背景
                R.drawable.list_green_middle,   // 绿色普通项背景
                R.drawable.list_red_middle      // 红色普通项背景
        };

        /**
         * 列表末项背景资源数组（底部圆角样式）
         * 用于同色系分组中的最后一个列表项
         */
        private final static int[] BG_LAST_RESOURCES = new int[]{
                R.drawable.list_yellow_down,  // 黄色末项背景
                R.drawable.list_blue_down,    // 蓝色末项背景
                R.drawable.list_white_down,   // 白色末项背景
                R.drawable.list_green_down,   // 绿色末项背景
                R.drawable.list_red_down      // 红色末项背景
        };

        /**
         * 列表单项背景资源数组（四角圆角样式）
         * 用于该颜色分组中只有一条记录的情况
         */
        private final static int[] BG_SINGLE_RESOURCES = new int[]{
                R.drawable.list_yellow_single,  // 黄色单项背景
                R.drawable.list_blue_single,    // 蓝色单项背景
                R.drawable.list_white_single,   // 白色单项背景
                R.drawable.list_green_single,   // 绿色单项背景
                R.drawable.list_red_single      // 红色单项背景
        };

        /**
         * 根据颜色索引获取列表首项的背景资源 ID（顶部圆角）
         *
         * @param id 颜色索引，对应 {@link ResourceParser} 中定义的颜色常量
         * @return 对应颜色的列表首项背景 Drawable 资源 ID
         */
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        /**
         * 根据颜色索引获取列表末项的背景资源 ID（底部圆角）
         *
         * @param id 颜色索引，对应 {@link ResourceParser} 中定义的颜色常量
         * @return 对应颜色的列表末项背景 Drawable 资源 ID
         */
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        /**
         * 根据颜色索引获取列表单项的背景资源 ID（四角圆角）
         *
         * @param id 颜色索引，对应 {@link ResourceParser} 中定义的颜色常量
         * @return 对应颜色的列表单项背景 Drawable 资源 ID
         */
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        /**
         * 根据颜色索引获取列表普通项的背景资源 ID（无圆角）
         *
         * @param id 颜色索引，对应 {@link ResourceParser} 中定义的颜色常量
         * @return 对应颜色的列表普通项背景 Drawable 资源 ID
         */
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /**
         * 获取文件夹列表项的背景资源 ID
         *
         * <p>文件夹列表项使用固定的统一背景样式，不受颜色索引影响。</p>
         *
         * @return 文件夹列表项背景 Drawable 资源 ID
         */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    // ========================================================================
    // 桌面小部件背景资源管理内部类
    // ========================================================================

    /**
     * 桌面小部件（Widget）背景资源管理类
     *
     * <p>管理便签桌面小部件的背景 Drawable 资源，支持两种尺寸规格：
     * <ul>
     *   <li>2x 尺寸：小型桌面小部件</li>
     *   <li>4x 尺寸：大型桌面小部件</li>
     * </ul>
     * 每种尺寸均支持五种背景颜色。</p>
     *
     * <p>资源数组与颜色索引的对应顺序：
     * 黄色(0) → 蓝色(1) → 白色(2) → 绿色(3) → 红色(4)</p>
     */
    public static class WidgetBgResources {

        /**
         * 2x 尺寸桌面小部件背景资源数组
         * 按颜色索引顺序存储各颜色对应的 2x Widget 背景 Drawable 资源 ID
         */
        private final static int[] BG_2X_RESOURCES = new int[]{
                R.drawable.widget_2x_yellow,  // 黄色 2x Widget 背景
                R.drawable.widget_2x_blue,    // 蓝色 2x Widget 背景
                R.drawable.widget_2x_white,   // 白色 2x Widget 背景
                R.drawable.widget_2x_green,   // 绿色 2x Widget 背景
                R.drawable.widget_2x_red      // 红色 2x Widget 背景
        };

        /**
         * 根据颜色索引获取 2x 尺寸桌面小部件的背景资源 ID
         *
         * @param id 颜色索引，对应 {@link ResourceParser} 中定义的颜色常量
         * @return 对应颜色的 2x Widget 背景 Drawable 资源 ID
         */
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        /**
         * 4x 尺寸桌面小部件背景资源数组
         * 按颜色索引顺序存储各颜色对应的 4x Widget 背景 Drawable 资源 ID
         */
        private final static int[] BG_4X_RESOURCES = new int[]{
                R.drawable.widget_4x_yellow,  // 黄色 4x Widget 背景
                R.drawable.widget_4x_blue,    // 蓝色 4x Widget 背景
                R.drawable.widget_4x_white,   // 白色 4x Widget 背景
                R.drawable.widget_4x_green,   // 绿色 4x Widget 背景
                R.drawable.widget_4x_red      // 红色 4x Widget 背景
        };

        /**
         * 根据颜色索引获取 4x 尺寸桌面小部件的背景资源 ID
         *
         * @param id 颜色索引，对应 {@link ResourceParser} 中定义的颜色常量
         * @return 对应颜色的 4x Widget 背景 Drawable 资源 ID
         */
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    // ========================================================================
    // 文本样式资源管理内部类
    // ========================================================================

    /**
     * 文本外观样式资源管理类
     *
     * <p>管理便签编辑界面中文本显示样式（TextAppearance）的资源映射，
     * 对应四种字体大小级别：小号、中号、大号、超大号。</p>
     *
     * <p>资源数组与字体大小索引的对应顺序：
     * 小号(0) → 中号(1) → 大号(2) → 超大号(3)</p>
     */
    public static class TextAppearanceResources {

        /**
         * 文本外观样式资源数组
         * 按字体大小索引顺序存储对应的 Style 资源 ID
         */
        private final static int[] TEXTAPPEARANCE_RESOURCES = new int[]{
                R.style.TextAppearanceNormal,  // 小号字体样式
                R.style.TextAppearanceMedium,  // 中号字体样式
                R.style.TextAppearanceLarge,   // 大号字体样式
                R.style.TextAppearanceSuper    // 超大号字体样式
        };

        /**
         * 根据字体大小索引获取对应的文本外观样式资源 ID
         *
         * <p><strong>HACKME（已知问题修复）：</strong>
         * SharedPreferences 中存储的资源 ID 可能因数据版本迁移或异常写入，
         * 导致其值超出资源数组的有效索引范围。
         * 针对此边界情况，当传入的 ID 超出数组长度时，
         * 自动回退到默认字体大小（{@link ResourceParser#BG_DEFAULT_FONT_SIZE}），
         * 以防止发生 {@link ArrayIndexOutOfBoundsException}。</p>
         *
         * @param id 字体大小索引，对应 {@link ResourceParser} 中定义的字体大小常量
         *           （如 {@link ResourceParser#TEXT_SMALL}、
         *           {@link ResourceParser#TEXT_MEDIUM} 等）
         * @return 对应的文本外观 Style 资源 ID；
         *         若 id 超出有效范围则返回默认字体大小索引
         *         {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
         */
        public static int getTexAppearanceResource(int id) {
            // 边界检查：防止因持久化数据异常导致数组越界
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取文本外观样式资源的总数量
         *
         * <p>通常用于设置界面中构建字体大小选择列表，
         * 避免硬编码字体大小选项数量。</p>
         *
         * @return 可用文本外观样式的总数量
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}