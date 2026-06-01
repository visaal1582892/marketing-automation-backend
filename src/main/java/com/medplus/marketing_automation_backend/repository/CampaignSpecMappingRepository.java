package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.CampaignSpecMapping;
import com.medplus.marketing_automation_backend.domain.MasterItem;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the two hierarchical mapping tables:
 * <ul>
 *   <li>business_vertical_business_type_mapping</li>
 *   <li>business_type_store_format_mapping</li>
 * </ul>
 */
@Repository
public class CampaignSpecMappingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CampaignSpecMappingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------------------
    // Business Vertical → Business Type
    // -------------------------------------------------------------------------

    public List<CampaignSpecMapping> findAllVerticalTypeMappings() {
        String sql = """
                SELECT m.mapping_id,
                       m.business_vertical_id  AS parent_id,
                       bv.business_vertical_name AS parent_name,
                       m.business_type_id       AS child_id,
                       bt.business_type_name    AS child_name
                FROM   business_vertical_business_type_mapping m
                JOIN   business_verticals bv ON bv.business_vertical_id = m.business_vertical_id
                JOIN   business_types     bt ON bt.business_type_id     = m.business_type_id
                ORDER  BY bv.business_vertical_name, bt.business_type_name
                """;
        return jdbc.query(sql, mappingMapper());
    }

    /** Returns business types allowed for a given vertical. */
    public List<MasterItem> findBusinessTypesByVertical(String verticalId) {
        String sql = """
                SELECT bt.business_type_id   AS id,
                       bt.business_type_name AS name,
                       bt.status
                FROM   business_vertical_business_type_mapping m
                JOIN   business_types bt ON bt.business_type_id = m.business_type_id
                WHERE  m.business_vertical_id = :verticalId
                  AND  bt.status = 'ACTIVE'
                ORDER  BY bt.business_type_name
                """;
        return jdbc.query(sql, new MapSqlParameterSource("verticalId", verticalId), masterItemMapper());
    }

    public Optional<CampaignSpecMapping> findVerticalTypeMappingById(Integer id) {
        String sql = """
                SELECT m.mapping_id,
                       m.business_vertical_id  AS parent_id,
                       bv.business_vertical_name AS parent_name,
                       m.business_type_id       AS child_id,
                       bt.business_type_name    AS child_name
                FROM   business_vertical_business_type_mapping m
                JOIN   business_verticals bv ON bv.business_vertical_id = m.business_vertical_id
                JOIN   business_types     bt ON bt.business_type_id     = m.business_type_id
                WHERE  m.mapping_id = :id
                """;
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mappingMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsVerticalTypeMapping(String verticalId, String typeId, Integer excludeMappingId) {
        String sql = "SELECT COUNT(*) FROM business_vertical_business_type_mapping "
                + "WHERE business_vertical_id = :vid AND business_type_id = :tid"
                + (excludeMappingId != null ? " AND mapping_id <> :mid" : "");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("vid", verticalId).addValue("tid", typeId);
        if (excludeMappingId != null) p.addValue("mid", excludeMappingId);
        Integer c = jdbc.queryForObject(sql, p, Integer.class);
        return c != null && c > 0;
    }

    public Integer insertVerticalTypeMapping(String verticalId, String typeId) {
        var kh = new GeneratedKeyHolder();
        jdbc.update(
                "INSERT INTO business_vertical_business_type_mapping (business_vertical_id, business_type_id) "
                        + "VALUES (:vid, :tid)",
                new MapSqlParameterSource().addValue("vid", verticalId).addValue("tid", typeId),
                kh);
        return kh.getKey() != null ? kh.getKey().intValue() : null;
    }

    public int deleteVerticalTypeMapping(Integer id) {
        return jdbc.update(
                "DELETE FROM business_vertical_business_type_mapping WHERE mapping_id = :id",
                new MapSqlParameterSource("id", id));
    }

    // -------------------------------------------------------------------------
    // Business Type → Store Format Type
    // -------------------------------------------------------------------------

    public List<CampaignSpecMapping> findAllTypeFormatMappings() {
        String sql = """
                SELECT m.mapping_id,
                       m.business_type_id        AS parent_id,
                       bt.business_type_name      AS parent_name,
                       m.store_format_type_id     AS child_id,
                       sft.store_format_type_name AS child_name
                FROM   business_type_store_format_mapping m
                JOIN   business_types     bt  ON bt.business_type_id       = m.business_type_id
                JOIN   store_format_types sft ON sft.store_format_type_id  = m.store_format_type_id
                ORDER  BY bt.business_type_name, sft.store_format_type_name
                """;
        return jdbc.query(sql, mappingMapper());
    }

    /** Returns store format types allowed for a given business type. */
    public List<MasterItem> findStoreFormatsByBusinessType(String businessTypeId) {
        String sql = """
                SELECT sft.store_format_type_id   AS id,
                       sft.store_format_type_name AS name,
                       sft.status
                FROM   business_type_store_format_mapping m
                JOIN   store_format_types sft ON sft.store_format_type_id = m.store_format_type_id
                WHERE  m.business_type_id = :typeId
                  AND  sft.status = 'ACTIVE'
                ORDER  BY sft.store_format_type_name
                """;
        return jdbc.query(sql, new MapSqlParameterSource("typeId", businessTypeId), masterItemMapper());
    }

    public Optional<CampaignSpecMapping> findTypeFormatMappingById(Integer id) {
        String sql = """
                SELECT m.mapping_id,
                       m.business_type_id        AS parent_id,
                       bt.business_type_name      AS parent_name,
                       m.store_format_type_id     AS child_id,
                       sft.store_format_type_name AS child_name
                FROM   business_type_store_format_mapping m
                JOIN   business_types     bt  ON bt.business_type_id       = m.business_type_id
                JOIN   store_format_types sft ON sft.store_format_type_id  = m.store_format_type_id
                WHERE  m.mapping_id = :id
                """;
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mappingMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public boolean existsTypeFormatMapping(String typeId, String formatId, Integer excludeMappingId) {
        String sql = "SELECT COUNT(*) FROM business_type_store_format_mapping "
                + "WHERE business_type_id = :tid AND store_format_type_id = :fid"
                + (excludeMappingId != null ? " AND mapping_id <> :mid" : "");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("tid", typeId).addValue("fid", formatId);
        if (excludeMappingId != null) p.addValue("mid", excludeMappingId);
        Integer c = jdbc.queryForObject(sql, p, Integer.class);
        return c != null && c > 0;
    }

    public Integer insertTypeFormatMapping(String typeId, String formatId) {
        var kh = new GeneratedKeyHolder();
        jdbc.update(
                "INSERT INTO business_type_store_format_mapping (business_type_id, store_format_type_id) "
                        + "VALUES (:tid, :fid)",
                new MapSqlParameterSource().addValue("tid", typeId).addValue("fid", formatId),
                kh);
        return kh.getKey() != null ? kh.getKey().intValue() : null;
    }

    public int deleteTypeFormatMapping(Integer id) {
        return jdbc.update(
                "DELETE FROM business_type_store_format_mapping WHERE mapping_id = :id",
                new MapSqlParameterSource("id", id));
    }

    // -------------------------------------------------------------------------
    // Row mappers
    // -------------------------------------------------------------------------

    private static RowMapper<CampaignSpecMapping> mappingMapper() {
        return (rs, rowNum) -> CampaignSpecMapping.builder()
                .mappingId(rs.getInt("mapping_id"))
                .parentId(rs.getString("parent_id"))
                .parentName(rs.getString("parent_name"))
                .childId(rs.getString("child_id"))
                .childName(rs.getString("child_name"))
                .build();
    }

    private static RowMapper<MasterItem> masterItemMapper() {
        return (rs, rowNum) -> {
            String raw = rs.getString("status");
            com.medplus.marketing_automation_backend.enums.RecordStatus status =
                    raw == null
                            ? com.medplus.marketing_automation_backend.enums.RecordStatus.ACTIVE
                            : com.medplus.marketing_automation_backend.enums.RecordStatus.valueOf(raw);
            return MasterItem.builder()
                    .id(rs.getString("id"))
                    .name(rs.getString("name"))
                    .status(status)
                    .build();
        };
    }
}
