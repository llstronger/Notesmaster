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

/**
 * Google Task 同步字符串常量工具类
 *
 * <p>该类集中定义了与 Google Task（GTask）同步协议相关的所有字符串常量，
 * 涵盖 JSON 字段键名、操作类型、文件夹名称以及元数据标识等。</p>
 *
 * <p>设计目的：
 * <ul>
 *   <li>避免代码中出现"魔法字符串"（Magic String），提高代码可维护性</li>
 *   <li>集中管理所有协议相关常量，便于协议升级时统一修改</li>
 *   <li>通过常量命名清晰表达字段含义，提升代码可读性</li>
 * </ul>
 * </p>
 *
 * <p>常量分组说明：
 * <ol>
 *   <li>GTask JSON 通用字段键名（GTASK_JSON_*）</li>
 *   <li>GTask 操作类型常量（GTASK_JSON_ACTION_TYPE_*）</li>
 *   <li>GTask 实体类型常量（GTASK_JSON_TYPE_*）</li>
 *   <li>MIUI 文件夹名称常量（FOLDER_*）</li>
 *   <li>元数据标识常量（META_*）</li>
 * </ol>
 * </p>
 *
 * @author MiCode Open Source Community
 * @version 1.0
 */
public class GTaskStringUtils {

    // ========================================================================
    // GTask JSON 通用字段键名常量
    // 以下常量对应 Google Task API 请求与响应中 JSON 对象的字段键名
    // ========================================================================

    /** JSON 字段：操作 ID，用于标识每次 API 请求中具体操作的唯一编号 */
    public final static String GTASK_JSON_ACTION_ID = "action_id";

    /** JSON 字段：操作列表，包含本次请求中所有待执行操作的数组 */
    public final static String GTASK_JSON_ACTION_LIST = "action_list";

    /** JSON 字段：操作类型，标识当前操作的具体行为（如创建、更新、移动等） */
    public final static String GTASK_JSON_ACTION_TYPE = "action_type";

    // ========================================================================
    // GTask 操作类型常量
    // 对应 GTASK_JSON_ACTION_TYPE 字段的可选值
    // ========================================================================

    /** 操作类型：创建新任务或任务列表 */
    public final static String GTASK_JSON_ACTION_TYPE_CREATE = "create";

    /** 操作类型：获取所有任务列表及其内容 */
    public final static String GTASK_JSON_ACTION_TYPE_GETALL = "get_all";

    /** 操作类型：移动任务到指定列表或位置 */
    public final static String GTASK_JSON_ACTION_TYPE_MOVE = "move";

    /** 操作类型：更新已有任务或任务列表的属性 */
    public final static String GTASK_JSON_ACTION_TYPE_UPDATE = "update";

    // ========================================================================
    // GTask JSON 实体属性字段键名常量
    // ========================================================================

    /** JSON 字段：创建者 ID，标识任务的创建者身份 */
    public final static String GTASK_JSON_CREATOR_ID = "creator_id";

    /** JSON 字段：子实体，包含任务列表下的子任务集合 */
    public final static String GTASK_JSON_CHILD_ENTITY = "child_entity";

    /** JSON 字段：客户端版本号，用于 API 兼容性协商 */
    public final static String GTASK_JSON_CLIENT_VERSION = "client_version";

    /** JSON 字段：完成状态，标识任务是否已完成（布尔值） */
    public final static String GTASK_JSON_COMPLETED = "completed";

    /** JSON 字段：当前列表 ID，标识任务当前所属的任务列表 */
    public final static String GTASK_JSON_CURRENT_LIST_ID = "current_list_id";

    /** JSON 字段：默认列表 ID，标识用户的默认任务列表 */
    public final static String GTASK_JSON_DEFAULT_LIST_ID = "default_list_id";

    /** JSON 字段：删除状态，标识任务是否已被删除（布尔值） */
    public final static String GTASK_JSON_DELETED = "deleted";

    /** JSON 字段：目标列表，移动操作中任务的目标任务列表 ID */
    public final static String GTASK_JSON_DEST_LIST = "dest_list";

    /** JSON 字段：目标父节点，移动操作中任务的目标父级实体 ID */
    public final static String GTASK_JSON_DEST_PARENT = "dest_parent";

    /** JSON 字段：目标父节点类型，标识目标父级是任务列表还是任务组 */
    public final static String GTASK_JSON_DEST_PARENT_TYPE = "dest_parent_type";

    /** JSON 字段：实体变更量，包含需要更新的字段及其新值 */
    public final static String GTASK_JSON_ENTITY_DELTA = "entity_delta";

    /** JSON 字段：实体类型，标识当前操作对象是任务（TASK）还是任务组（GROUP） */
    public final static String GTASK_JSON_ENTITY_TYPE = "entity_type";

    /** JSON 字段：是否获取已删除项，控制同步时是否拉取已删除的任务数据 */
    public final static String GTASK_JSON_GET_DELETED = "get_deleted";

    /** JSON 字段：实体唯一 ID，GTask 服务端为每个任务或列表分配的全局唯一标识符 */
    public final static String GTASK_JSON_ID = "id";

    /** JSON 字段：排序索引，标识任务在列表中的显示顺序位置 */
    public final static String GTASK_JSON_INDEX = "index";

    /** JSON 字段：最后修改时间，记录任务最后一次被修改的时间戳 */
    public final static String GTASK_JSON_LAST_MODIFIED = "last_modified";

    /** JSON 字段：最新同步时间点，用于增量同步时确定上次同步的时间基准 */
    public final static String GTASK_JSON_LATEST_SYNC_POINT = "latest_sync_point";

    /** JSON 字段：列表 ID，标识任务所属的任务列表 */
    public final static String GTASK_JSON_LIST_ID = "list_id";

    /** JSON 字段：任务列表集合，包含用户所有任务列表的数组 */
    public final static String GTASK_JSON_LISTS = "lists";

    /** JSON 字段：名称，任务或任务列表的显示名称 */
    public final static String GTASK_JSON_NAME = "name";

    /** JSON 字段：新 ID，创建操作完成后服务端返回的新实体 ID */
    public final static String GTASK_JSON_NEW_ID = "new_id";

    /** JSON 字段：便签集合，包含与任务关联的便签数组 */
    public final static String GTASK_JSON_NOTES = "notes";

    /** JSON 字段：父节点 ID，标识任务所属的父级实体（列表或任务组）ID */
    public final static String GTASK_JSON_PARENT_ID = "parent_id";

    /** JSON 字段：前一个兄弟节点 ID，用于确定任务在列表中的插入位置 */
    public final static String GTASK_JSON_PRIOR_SIBLING_ID = "prior_sibling_id";

    /** JSON 字段：结果集合，包含服务端响应中返回的操作结果数组 */
    public final static String GTASK_JSON_RESULTS = "results";

    /** JSON 字段：源列表，移动操作中任务的来源任务列表 ID */
    public final static String GTASK_JSON_SOURCE_LIST = "source_list";

    /** JSON 字段：任务集合，包含某任务列表下所有任务的数组 */
    public final static String GTASK_JSON_TASKS = "tasks";

    /** JSON 字段：类型，标识实体的类别（如 GROUP 或 TASK） */
    public final static String GTASK_JSON_TYPE = "type";

    // ========================================================================
    // GTask 实体类型常量
    // 对应 GTASK_JSON_TYPE 和 GTASK_JSON_ENTITY_TYPE 字段的可选值
    // ========================================================================

    /** 实体类型：任务组（对应本地的文件夹概念） */
    public final static String GTASK_JSON_TYPE_GROUP = "GROUP";

    /** 实体类型：任务（对应本地的单条便签概念） */
    public final static String GTASK_JSON_TYPE_TASK = "TASK";

    /** JSON 字段：用户信息，包含当前操作用户的基本信息对象 */
    public final static String GTASK_JSON_USER = "user";

    // ========================================================================
    // MIUI 便签文件夹名称常量
    // 用于在 GTask 中标识 MIUI 便签专属文件夹，与普通 GTask 列表区分
    // ========================================================================

    /**
     * MIUI 便签文件夹名称前缀
     *
     * <p>所有由 MIUI 便签创建的 GTask 列表均以此前缀开头，
     * 用于在用户的 GTask 账户中区分 MIUI 便签数据与其他 GTask 数据。</p>
     */
    public final static String MIUI_FOLDER_PREFFIX = "[MIUI_Notes]";

    /** 文件夹名称：默认文件夹，存储未归类的普通便签 */
    public final static String FOLDER_DEFAULT = "Default";

    /** 文件夹名称：通话记录文件夹，存储由来电触发创建的通话便签 */
    public final static String FOLDER_CALL_NOTE = "Call_Note";

    /**
     * 文件夹名称：元数据文件夹
     *
     * <p>该文件夹专门用于存储同步过程中的元数据信息（如 GTask ID 映射关系），
     * 不对用户可见，不应被手动修改或删除。</p>
     */
    public final static String FOLDER_META = "METADATA";

    // ========================================================================
    // 元数据（META）标识常量
    // 用于在本地数据库中存储 GTask 同步所需的映射信息
    // ========================================================================

    /** 元数据键：GTask ID，用于存储本地便签对应的 GTask 服务端唯一标识符 */
    public final static String META_HEAD_GTASK_ID = "meta_gid";

    /** 元数据键：便签元信息头，用于标识元数据条目中的便签基本信息字段 */
    public final static String META_HEAD_NOTE = "meta_note";

    /** 元数据键：数据元信息头，用于标识元数据条目中的便签详细数据字段 */
    public final static String META_HEAD_DATA = "meta_data";

    /**
     * 元数据便签名称
     *
     * <p><strong>警告：请勿修改或删除该名称对应的便签！</strong></p>
     * <p>该常量标识存储同步元数据的特殊便签，是 GTask 同步机制正常运行的关键。
     * 如果该便签被误删或修改，将导致同步数据丢失或状态异常。</p>
     */
    public final static String META_NOTE_NAME = "[META INFO] DON'T UPDATE AND DELETE";

}