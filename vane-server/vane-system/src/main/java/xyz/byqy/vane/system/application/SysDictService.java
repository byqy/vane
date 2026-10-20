package xyz.byqy.vane.system.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/**
 * 字典（ADM-06）：字典项标签是运营数据，按 locale 从 i18n 表取，缺失回退 item_value。
 * 类型编码与字典项值不可修改（作为稳定标识），只能改名称/标签/排序/状态。
 */
@Service
public class SysDictService {

    private final JdbcTemplate jdbc;

    public SysDictService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    public List<DictTypeView> types() {
        return jdbc.query("""
                select t.type_code, t.name_key, t.status, t.remark,
                       (select count(*) from sys_dict_item i
                         where i.type_id = t.id and i.deleted_at is null) as item_count
                from sys_dict_type t
                where t.deleted_at is null order by t.type_code
                """, (rs, rowNum) -> new DictTypeView(rs.getString("type_code"), rs.getString("name_key"),
                rs.getString("status"), rs.getString("remark"), rs.getInt("item_count")));
    }

    @Transactional(readOnly = true)
    public List<DictItemView> items(String typeCode, String locale) {
        UUID typeId = requireTypeId(typeCode);
        List<ItemRow> rows = jdbc.query("""
                select i.id, i.item_value, coalesce(ti.label, i.item_value) as label,
                       i.color, i.sort_no, i.status, i.builtin
                from sys_dict_item i
                left join sys_dict_item_i18n ti on ti.item_id = i.id and ti.locale = ?
                where i.type_id = ? and i.deleted_at is null
                order by i.sort_no, i.item_value
                """, (rs, rowNum) -> new ItemRow(rs.getObject("id", UUID.class), rs.getString("item_value"),
                rs.getString("label"), rs.getString("color"), rs.getInt("sort_no"), rs.getString("status"),
                rs.getBoolean("builtin")), locale, typeId);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, Map<String, String>> labels = labelsOf(rows.stream().map(ItemRow::id).toList());
        return rows.stream()
                .map(row -> new DictItemView(row.value(), row.label(), row.color(), row.sortNo(), row.status(),
                        row.builtin(), labels.getOrDefault(row.id(), Map.of())))
                .toList();
    }

    // ---------- 字典类型 ----------

    @Transactional
    public void createType(String typeCode, String nameKey, String remark, UUID actorId) {
        Integer exists = jdbc.queryForObject("""
                select count(*) from sys_dict_type where type_code = ? and deleted_at is null
                """, Integer.class, typeCode);
        if (exists != null && exists > 0) {
            throw new ApiException(CommonErrors.SYS_DICT_TYPE_TAKEN);
        }
        jdbc.update("""
                insert into sys_dict_type (type_code, name_key, remark, created_by) values (?, ?, ?, ?)
                """, typeCode, nameKey, remark, actorId);
    }

    @Transactional
    public void updateType(String typeCode, String nameKey, String status, String remark, UUID actorId) {
        UUID typeId = requireTypeId(typeCode);
        jdbc.update("""
                update sys_dict_type set name_key = ?, status = ?, remark = ?, updated_by = ?, updated_at = now() where id = ?
                """, nameKey, status, remark, actorId, typeId);
    }

    @Transactional
    public void deleteType(String typeCode, UUID actorId) {
        UUID typeId = requireTypeId(typeCode);
        Integer items = jdbc.queryForObject("""
                select count(*) from sys_dict_item where type_id = ? and deleted_at is null
                """, Integer.class, typeId);
        if (items != null && items > 0) {
            throw new ApiException(CommonErrors.SYS_DICT_HAS_ITEMS);
        }
        jdbc.update("update sys_dict_type set deleted_at = now(), updated_by = ? where id = ?", actorId, typeId);
    }

    // ---------- 字典项 ----------

    public record Translation(String locale, String label) {
    }

    @Transactional
    public void createItem(String typeCode, String value, String color, int sortNo,
                           List<Translation> translations, UUID actorId) {
        UUID typeId = requireTypeId(typeCode);
        Integer exists = jdbc.queryForObject("""
                select count(*) from sys_dict_item where type_id = ? and item_value = ? and deleted_at is null
                """, Integer.class, typeId, value);
        if (exists != null && exists > 0) {
            throw new ApiException(CommonErrors.SYS_DICT_TYPE_TAKEN);
        }
        UUID itemId = UUID.randomUUID();
        jdbc.update("""
                insert into sys_dict_item (id, type_id, item_value, color, sort_no, status, created_by)
                values (?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, itemId, typeId, value, color, sortNo, actorId);
        replaceTranslations(itemId, translations);
    }

    @Transactional
    public void updateItem(String typeCode, String value, String color, int sortNo, String status,
                           List<Translation> translations, UUID actorId) {
        UUID itemId = requireItemId(typeCode, value);
        jdbc.update("""
                update sys_dict_item set color = ?, sort_no = ?, status = ?, updated_by = ?, updated_at = now() where id = ?
                """, color, sortNo, status, actorId, itemId);
        replaceTranslations(itemId, translations);
    }

    @Transactional
    public void deleteItem(String typeCode, String value, UUID actorId) {
        UUID itemId = requireItemId(typeCode, value);
        Boolean builtin = jdbc.queryForObject("select builtin from sys_dict_item where id = ?", Boolean.class, itemId);
        if (Boolean.TRUE.equals(builtin)) {
            throw new ApiException(CommonErrors.SYS_DICT_ITEM_BUILTIN);
        }
        jdbc.update("update sys_dict_item set deleted_at = now(), updated_by = ? where id = ?", actorId, itemId);
    }

    // ---------- 内部 ----------

    private UUID requireTypeId(String typeCode) {
        List<UUID> ids = jdbc.query("""
                select id from sys_dict_type where type_code = ? and deleted_at is null
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), typeCode);
        if (ids.isEmpty()) {
            throw new ApiException(CommonErrors.SYS_DICT_NOT_FOUND);
        }
        return ids.get(0);
    }

    private UUID requireItemId(String typeCode, String value) {
        UUID typeId = requireTypeId(typeCode);
        List<UUID> ids = jdbc.query("""
                select id from sys_dict_item where type_id = ? and item_value = ? and deleted_at is null
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), typeId, value);
        if (ids.isEmpty()) {
            throw new ApiException(CommonErrors.SYS_DICT_NOT_FOUND);
        }
        return ids.get(0);
    }

    private void replaceTranslations(UUID itemId, List<Translation> translations) {
        jdbc.update("delete from sys_dict_item_i18n where item_id = ?", itemId);
        if (translations == null) {
            return;
        }
        for (Translation translation : translations) {
            if (translation.locale() == null || translation.label() == null) {
                continue;
            }
            jdbc.update("""
                    insert into sys_dict_item_i18n (item_id, locale, label) values (?, ?, ?)
                    """, itemId, translation.locale(), translation.label());
        }
    }

    private Map<UUID, Map<String, String>> labelsOf(List<UUID> itemIds) {
        String placeholders = String.join(",", itemIds.stream().map(id -> "?").toList());
        Map<UUID, Map<String, String>> result = new LinkedHashMap<>();
        String sql = "select item_id, locale, label from sys_dict_item_i18n where item_id in ("
                + placeholders + ")";
        for (Map<String, Object> row : jdbc.queryForList(sql, itemIds.toArray())) {
            result.computeIfAbsent((UUID) row.get("item_id"), key -> new LinkedHashMap<>())
                    .put((String) row.get("locale"), (String) row.get("label"));
        }
        return result;
    }

    private record ItemRow(UUID id, String value, String label, String color, int sortNo, String status,
                           boolean builtin) {
    }
}



