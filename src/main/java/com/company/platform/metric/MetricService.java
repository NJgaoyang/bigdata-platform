package com.company.platform.metric;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class MetricService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MetricService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Overview overview() {
        return new Overview(
                count("SELECT COUNT(*) FROM metric_definition"),
                count("SELECT COUNT(*) FROM metric_definition WHERE status='CERTIFIED'"),
                count("SELECT COUNT(*) FROM metric_definition WHERE status IN ('PUBLISHED','CERTIFIED')"),
                count("SELECT COUNT(*) FROM metric_definition WHERE status='PENDING_APPROVAL'"),
                count("SELECT COUNT(*) FROM metric_definition WHERE status='DRAFT'"),
                count("SELECT COUNT(*) FROM metric_definition WHERE status='REJECTED'"),
                count("SELECT COUNT(*) FROM metric_dimension"),
                count("SELECT COUNT(*) FROM metric_lineage"),
                count("SELECT COUNT(*) FROM metric_domain"),
                count("SELECT COUNT(*) FROM metric_theme")
        );
    }

    public List<MetricView> list(String type, String status) {
        StringBuilder sql = new StringBuilder(metricSelect() + " WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (type != null && !type.isBlank()) { sql.append(" AND m.metric_type=?"); args.add(type.toUpperCase(Locale.ROOT)); }
        if (status != null && !status.isBlank()) { sql.append(" AND m.status=?"); args.add(status.toUpperCase(Locale.ROOT)); }
        sql.append(" ORDER BY m.updated_at DESC");
        return jdbc.query(sql.toString(), (rs, n) -> metric(rs), args.toArray());
    }

    public MetricView get(long id) {
        return jdbc.query(metricSelect() + " WHERE m.id=?", (rs, n) -> metric(rs), id).stream()
                .findFirst().orElseThrow(() -> new NotFoundException("指标不存在：" + id));
    }

    @Transactional
    public MetricView create(MetricRequest r, String operator) {
        validate(r);
        DomainView domain = resolveDomain(r.domainId());
        ThemeView theme = resolveTheme(r.themeId(), r.domainId());
        String domainName = domain == null ? blankToNull(r.businessDomain()) : domain.domainName();
        jdbc.update("INSERT INTO metric_definition(metric_code,metric_name,metric_type,description,business_domain,domain_id,theme_id,owner_name,status,current_version,source_datasource_id,source_database,source_table,source_field,aggregation,filter_expression,time_field,expression_text) VALUES(?,?,?,?,?,?,?,?,'DRAFT',1,?,?,?,?,?,?,?,?)",
                code(r.metricCode()), r.metricName(), type(r.metricType()), r.description(), domainName, r.domainId(), theme == null ? null : theme.id(), r.ownerName(),
                r.sourceDataSourceId(), r.sourceDatabase(), r.sourceTable(), r.sourceField(), r.aggregation(), r.filterExpression(), r.timeField(), r.expressionText());
        long id = jdbc.queryForObject("SELECT id FROM metric_definition WHERE metric_code=?", Long.class, code(r.metricCode()));
        writeVersion(id, 1, r, operator);
        replaceRelations(id, r.dimensionIds(), r.upstreamMetricCodes());
        return get(id);
    }

    @Transactional
    public MetricView update(long id, MetricRequest r, String operator) {
        MetricView current = get(id);
        if ("PENDING_APPROVAL".equals(current.status())) throw new BadRequestException("指标正在审批中，不能修改");
        validate(r);
        DomainView domain = resolveDomain(r.domainId());
        ThemeView theme = resolveTheme(r.themeId(), r.domainId());
        String domainName = domain == null ? blankToNull(r.businessDomain()) : domain.domainName();
        int version = current.currentVersion() + 1;
        jdbc.update("UPDATE metric_definition SET metric_code=?,metric_name=?,metric_type=?,description=?,business_domain=?,domain_id=?,theme_id=?,owner_name=?,status='DRAFT',current_version=?,source_datasource_id=?,source_database=?,source_table=?,source_field=?,aggregation=?,filter_expression=?,time_field=?,expression_text=? WHERE id=?",
                code(r.metricCode()), r.metricName(), type(r.metricType()), r.description(), domainName, r.domainId(), theme == null ? null : theme.id(), r.ownerName(), version,
                r.sourceDataSourceId(), r.sourceDatabase(), r.sourceTable(), r.sourceField(), r.aggregation(), r.filterExpression(), r.timeField(), r.expressionText(), id);
        writeVersion(id, version, r, operator);
        replaceRelations(id, r.dimensionIds(), r.upstreamMetricCodes());
        return get(id);
    }

    @Transactional public MetricView publish(long id, int requestedVersion, String operator) {
        MetricView current = get(id);
        if (requestedVersion > 0 && current.currentVersion() != requestedVersion) throw new BadRequestException("指标已产生新版本，请重新提交审批");
        jdbc.update("UPDATE metric_definition SET status='PUBLISHED' WHERE id=?", id);
        return get(id);
    }
    @Transactional public MetricView markPending(long id) { get(id); jdbc.update("UPDATE metric_definition SET status='PENDING_APPROVAL' WHERE id=?", id); return get(id); }
    @Transactional public MetricView markRejected(long id) { get(id); jdbc.update("UPDATE metric_definition SET status='REJECTED' WHERE id=?", id); return get(id); }
    @Transactional public MetricView certify(long id, String operator) { get(id); jdbc.update("UPDATE metric_definition SET status='CERTIFIED' WHERE id=?", id); return get(id); }
    @Transactional public MetricView revokeCertification(long id) { get(id); jdbc.update("UPDATE metric_definition SET status='DRAFT' WHERE id=?", id); return get(id); }

    @Transactional
    public void delete(long id) {
        MetricView v = get(id);
        if (Set.of("CERTIFIED", "PUBLISHED", "PENDING_APPROVAL").contains(v.status())) throw new BadRequestException("已发布或审批中的指标不能删除");
        jdbc.update("DELETE FROM metric_dimension_relation WHERE metric_id=?", id);
        jdbc.update("DELETE FROM metric_lineage WHERE metric_id=?", id);
        jdbc.update("DELETE FROM metric_version WHERE metric_id=?", id);
        jdbc.update("DELETE FROM metric_definition WHERE id=?", id);
    }

    public List<VersionView> versions(long id) {
        get(id);
        return jdbc.query("SELECT id,metric_id,version_no,definition_json,created_by,created_at FROM metric_version WHERE metric_id=? ORDER BY version_no DESC",
                (rs, n) -> new VersionView(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getTimestamp(6).toLocalDateTime()), id);
    }

    public List<LineageView> lineage(Long metricId) {
        String sql = metricId == null
                ? "SELECT l.*,m.metric_code,m.metric_name FROM metric_lineage l JOIN metric_definition m ON m.id=l.metric_id ORDER BY l.id DESC"
                : "SELECT l.*,m.metric_code,m.metric_name FROM metric_lineage l JOIN metric_definition m ON m.id=l.metric_id WHERE l.metric_id=? ORDER BY l.id";
        return metricId == null ? jdbc.query(sql, (rs, n) -> lineage(rs)) : jdbc.query(sql, (rs, n) -> lineage(rs), metricId);
    }

    public List<DimensionView> dimensions() { return jdbc.query("SELECT * FROM metric_dimension ORDER BY dimension_name", (rs, n) -> dimension(rs)); }
    @Transactional public DimensionView saveDimension(Long id, DimensionRequest r) {
        if (r.dimensionCode() == null || r.dimensionCode().isBlank() || r.dimensionName() == null || r.dimensionName().isBlank()) throw new BadRequestException("维度编码和名称不能为空");
        if (id == null) {
            jdbc.update("INSERT INTO metric_dimension(dimension_code,dimension_name,description,source_datasource_id,source_database,source_table,source_field,owner_name) VALUES(?,?,?,?,?,?,?,?)", code(r.dimensionCode()), r.dimensionName(), r.description(), r.sourceDataSourceId(), r.sourceDatabase(), r.sourceTable(), r.sourceField(), r.ownerName());
            id = jdbc.queryForObject("SELECT id FROM metric_dimension WHERE dimension_code=?", Long.class, code(r.dimensionCode()));
        } else {
            dimension(id);
            jdbc.update("UPDATE metric_dimension SET dimension_code=?,dimension_name=?,description=?,source_datasource_id=?,source_database=?,source_table=?,source_field=?,owner_name=? WHERE id=?", code(r.dimensionCode()), r.dimensionName(), r.description(), r.sourceDataSourceId(), r.sourceDatabase(), r.sourceTable(), r.sourceField(), r.ownerName(), id);
        }
        return dimension(id);
    }
    @Transactional public void deleteDimension(long id) { dimension(id); if (count("SELECT COUNT(*) FROM metric_dimension_relation WHERE dimension_id=" + id) > 0) throw new BadRequestException("维度正在被指标使用，不能删除"); jdbc.update("DELETE FROM metric_dimension WHERE id=?", id); }
    public DimensionView dimension(long id) { return jdbc.query("SELECT * FROM metric_dimension WHERE id=?", (rs, n) -> dimension(rs), id).stream().findFirst().orElseThrow(() -> new NotFoundException("维度不存在：" + id)); }

    public List<DomainView> domains() {
        return jdbc.query("SELECT d.*,(SELECT COUNT(*) FROM metric_theme t WHERE t.domain_id=d.id) theme_count,(SELECT COUNT(*) FROM metric_definition m WHERE m.domain_id=d.id) metric_count FROM metric_domain d ORDER BY d.sort_order,d.domain_name", (rs, n) -> domain(rs));
    }
    public DomainView domain(long id) {
        return jdbc.query("SELECT d.*,(SELECT COUNT(*) FROM metric_theme t WHERE t.domain_id=d.id) theme_count,(SELECT COUNT(*) FROM metric_definition m WHERE m.domain_id=d.id) metric_count FROM metric_domain d WHERE d.id=?", (rs, n) -> domain(rs), id).stream().findFirst().orElseThrow(() -> new NotFoundException("主题域不存在：" + id));
    }
    @Transactional public DomainView saveDomain(Long id, DomainRequest r) {
        requireNameCode(r.domainName(), r.domainCode(), "主题域");
        String status = configStatus(r.status());
        if (id == null) {
            jdbc.update("INSERT INTO metric_domain(domain_code,domain_name,description,owner_name,status,sort_order) VALUES(?,?,?,?,?,?)", code(r.domainCode()), r.domainName().trim(), r.description(), r.ownerName(), status, r.sortOrder() == null ? 10 : r.sortOrder());
            id = jdbc.queryForObject("SELECT id FROM metric_domain WHERE domain_code=?", Long.class, code(r.domainCode()));
        } else {
            DomainView current = domain(id);
            jdbc.update("UPDATE metric_domain SET domain_code=?,domain_name=?,description=?,owner_name=?,status=?,sort_order=? WHERE id=?", code(r.domainCode()), r.domainName().trim(), r.description(), r.ownerName(), status, r.sortOrder() == null ? 10 : r.sortOrder(), id);
            if (!Objects.equals(current.domainName(), r.domainName().trim())) jdbc.update("UPDATE metric_definition SET business_domain=? WHERE domain_id=?", r.domainName().trim(), id);
        }
        return domain(id);
    }
    @Transactional public void deleteDomain(long id) {
        DomainView d = domain(id);
        if (d.themeCount() > 0 || d.metricCount() > 0) throw new BadRequestException("主题域下存在主题或指标，不能删除");
        jdbc.update("DELETE FROM metric_domain WHERE id=?", id);
    }

    public List<ThemeView> themes(Long domainId) {
        String sql = "SELECT t.*,d.domain_name,(SELECT COUNT(*) FROM metric_definition m WHERE m.theme_id=t.id) metric_count FROM metric_theme t JOIN metric_domain d ON d.id=t.domain_id" + (domainId == null ? "" : " WHERE t.domain_id=?") + " ORDER BY d.sort_order,t.sort_order,t.theme_name";
        return domainId == null ? jdbc.query(sql, (rs, n) -> theme(rs)) : jdbc.query(sql, (rs, n) -> theme(rs), domainId);
    }
    public ThemeView theme(long id) {
        return jdbc.query("SELECT t.*,d.domain_name,(SELECT COUNT(*) FROM metric_definition m WHERE m.theme_id=t.id) metric_count FROM metric_theme t JOIN metric_domain d ON d.id=t.domain_id WHERE t.id=?", (rs, n) -> theme(rs), id).stream().findFirst().orElseThrow(() -> new NotFoundException("主题不存在：" + id));
    }
    @Transactional public ThemeView saveTheme(Long id, ThemeRequest r) {
        requireNameCode(r.themeName(), r.themeCode(), "主题");
        domain(r.domainId());
        String status = configStatus(r.status());
        if (id == null) {
            jdbc.update("INSERT INTO metric_theme(domain_id,theme_code,theme_name,description,owner_name,status,sort_order) VALUES(?,?,?,?,?,?,?)", r.domainId(), code(r.themeCode()), r.themeName().trim(), r.description(), r.ownerName(), status, r.sortOrder() == null ? 10 : r.sortOrder());
            id = jdbc.queryForObject("SELECT id FROM metric_theme WHERE theme_code=?", Long.class, code(r.themeCode()));
        } else {
            ThemeView current = theme(id);
            jdbc.update("UPDATE metric_theme SET domain_id=?,theme_code=?,theme_name=?,description=?,owner_name=?,status=?,sort_order=? WHERE id=?", r.domainId(), code(r.themeCode()), r.themeName().trim(), r.description(), r.ownerName(), status, r.sortOrder() == null ? 10 : r.sortOrder(), id);
            if (current.domainId() != r.domainId()) {
                DomainView target = domain(r.domainId());
                jdbc.update("UPDATE metric_definition SET domain_id=?,business_domain=? WHERE theme_id=?", r.domainId(), target.domainName(), id);
            }
        }
        return theme(id);
    }
    @Transactional public void deleteTheme(long id) { ThemeView t = theme(id); if (t.metricCount() > 0) throw new BadRequestException("主题下存在指标，不能删除"); jdbc.update("DELETE FROM metric_theme WHERE id=?", id); }

    private void validate(MetricRequest r) {
        if (r.metricCode() == null || r.metricCode().isBlank() || r.metricName() == null || r.metricName().isBlank()) throw new BadRequestException("指标编码和名称不能为空");
        if (r.domainId() != null) resolveDomain(r.domainId());
        if (r.themeId() != null) resolveTheme(r.themeId(), r.domainId());
        String t = type(r.metricType());
        if ("ATOMIC".equals(t) && (r.sourceTable() == null || r.sourceTable().isBlank() || r.sourceField() == null || r.sourceField().isBlank())) throw new BadRequestException("原子指标必须绑定来源表和字段");
        if (!"ATOMIC".equals(t) && (r.expressionText() == null || r.expressionText().isBlank())) throw new BadRequestException("派生/复合指标必须填写计算表达式");
    }
    private DomainView resolveDomain(Long id) { return id == null ? null : domain(id); }
    private ThemeView resolveTheme(Long themeId, Long domainId) { if (themeId == null) return null; ThemeView t = theme(themeId); if (domainId == null || t.domainId() != domainId) throw new BadRequestException("主题必须属于所选主题域"); return t; }
    private void writeVersion(long id, int version, MetricRequest r, String operator) { try { jdbc.update("INSERT INTO metric_version(metric_id,version_no,definition_json,created_by) VALUES(?,?,?,?)", id, version, mapper.writeValueAsString(r), operator(operator)); } catch (Exception ex) { throw new BadRequestException("指标版本保存失败：" + ex.getMessage()); } }
    private void replaceRelations(long id, List<Long> dims, List<String> upstream) {
        jdbc.update("DELETE FROM metric_dimension_relation WHERE metric_id=?", id);
        if (dims != null) for (Long d : dims) { dimension(d); jdbc.update("INSERT INTO metric_dimension_relation(metric_id,dimension_id) VALUES(?,?)", id, d); }
        jdbc.update("DELETE FROM metric_lineage WHERE metric_id=?", id);
        MetricView m = get(id);
        if (m.sourceTable() != null && !m.sourceTable().isBlank()) jdbc.update("INSERT INTO metric_lineage(metric_id,upstream_type,upstream_ref,downstream_type,downstream_ref) VALUES(?,'FIELD',?,'METRIC',?)", id, (m.sourceDatabase() == null ? "" : m.sourceDatabase() + ".") + m.sourceTable() + "." + m.sourceField(), m.metricCode());
        if (upstream != null) for (String value : upstream) if (value != null && !value.isBlank()) jdbc.update("INSERT INTO metric_lineage(metric_id,upstream_type,upstream_ref,downstream_type,downstream_ref) VALUES(?,'METRIC',?,'METRIC',?)", id, value.trim(), m.metricCode());
    }

    private String metricSelect() { return "SELECT m.*,d.domain_name AS domain_name_join,t.theme_name AS theme_name_join FROM metric_definition m LEFT JOIN metric_domain d ON d.id=m.domain_id LEFT JOIN metric_theme t ON t.id=m.theme_id"; }
    private MetricView metric(java.sql.ResultSet rs) throws java.sql.SQLException {
        long id = rs.getLong("id");
        var c = rs.getTimestamp("created_at"); var u = rs.getTimestamp("updated_at");
        List<DimensionView> dims = jdbc.query("SELECT d.* FROM metric_dimension d JOIN metric_dimension_relation r ON r.dimension_id=d.id WHERE r.metric_id=? ORDER BY d.dimension_name", (r, n) -> dimension(r), id);
        return new MetricView(id, rs.getString("metric_code"), rs.getString("metric_name"), rs.getString("metric_type"), rs.getString("description"), rs.getString("business_domain"), rs.getObject("domain_id", Long.class), rs.getObject("theme_id", Long.class), rs.getString("domain_name_join"), rs.getString("theme_name_join"), rs.getString("owner_name"), rs.getString("status"), rs.getInt("current_version"), rs.getObject("source_datasource_id", Long.class), rs.getString("source_database"), rs.getString("source_table"), rs.getString("source_field"), rs.getString("aggregation"), rs.getString("filter_expression"), rs.getString("time_field"), rs.getString("expression_text"), dims, c == null ? null : c.toLocalDateTime(), u == null ? null : u.toLocalDateTime());
    }
    private DimensionView dimension(java.sql.ResultSet rs) throws java.sql.SQLException { return new DimensionView(rs.getLong("id"), rs.getString("dimension_code"), rs.getString("dimension_name"), rs.getString("description"), rs.getObject("source_datasource_id", Long.class), rs.getString("source_database"), rs.getString("source_table"), rs.getString("source_field"), rs.getString("owner_name")); }
    private LineageView lineage(java.sql.ResultSet rs) throws java.sql.SQLException { return new LineageView(rs.getLong("id"), rs.getLong("metric_id"), rs.getString("metric_code"), rs.getString("metric_name"), rs.getString("upstream_type"), rs.getString("upstream_ref"), rs.getString("downstream_type"), rs.getString("downstream_ref")); }
    private DomainView domain(java.sql.ResultSet rs) throws java.sql.SQLException { var u = rs.getTimestamp("updated_at"); return new DomainView(rs.getLong("id"), rs.getString("domain_code"), rs.getString("domain_name"), rs.getString("description"), rs.getString("owner_name"), rs.getString("status"), rs.getInt("sort_order"), rs.getLong("theme_count"), rs.getLong("metric_count"), u == null ? null : u.toLocalDateTime()); }
    private ThemeView theme(java.sql.ResultSet rs) throws java.sql.SQLException { var u = rs.getTimestamp("updated_at"); return new ThemeView(rs.getLong("id"), rs.getLong("domain_id"), rs.getString("domain_name"), rs.getString("theme_code"), rs.getString("theme_name"), rs.getString("description"), rs.getString("owner_name"), rs.getString("status"), rs.getInt("sort_order"), rs.getLong("metric_count"), u == null ? null : u.toLocalDateTime()); }

    private long count(String sql) { Long v = jdbc.queryForObject(sql, Long.class); return v == null ? 0 : v; }
    private void requireNameCode(String name, String value, String label) { if (name == null || name.isBlank() || value == null || value.isBlank()) throw new BadRequestException(label + "名称和编码不能为空"); }
    private String configStatus(String value) { String s = value == null || value.isBlank() ? "ENABLED" : value.trim().toUpperCase(Locale.ROOT); if (!Set.of("ENABLED", "DISABLED").contains(s)) throw new BadRequestException("状态仅支持 ENABLED / DISABLED"); return s; }
    private String code(String value) { return value == null ? "" : value.trim().replaceAll("[^A-Za-z0-9_]", "_"); }
    private String type(String value) { String t = value == null ? "ATOMIC" : value.trim().toUpperCase(Locale.ROOT); if (!Set.of("ATOMIC", "DERIVED", "COMPOSITE").contains(t)) throw new BadRequestException("指标类型仅支持 ATOMIC / DERIVED / COMPOSITE"); return t; }
    private String operator(String value) { return value == null || value.isBlank() ? "admin" : value.trim(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record Overview(long total,long certified,long published,long pendingApproval,long draft,long rejected,long dimensions,long lineageRelations,long domains,long themes) {}
    public record MetricRequest(String metricCode,String metricName,String metricType,String description,String businessDomain,Long domainId,Long themeId,String ownerName,Long sourceDataSourceId,String sourceDatabase,String sourceTable,String sourceField,String aggregation,String filterExpression,String timeField,String expressionText,List<Long> dimensionIds,List<String> upstreamMetricCodes) {}
    public record MetricView(long id,String metricCode,String metricName,String metricType,String description,String businessDomain,Long domainId,Long themeId,String domainName,String themeName,String ownerName,String status,int currentVersion,Long sourceDataSourceId,String sourceDatabase,String sourceTable,String sourceField,String aggregation,String filterExpression,String timeField,String expressionText,List<DimensionView> dimensions,LocalDateTime createdAt,LocalDateTime updatedAt) {}
    public record DimensionRequest(String dimensionCode,String dimensionName,String description,Long sourceDataSourceId,String sourceDatabase,String sourceTable,String sourceField,String ownerName) {}
    public record DimensionView(long id,String dimensionCode,String dimensionName,String description,Long sourceDataSourceId,String sourceDatabase,String sourceTable,String sourceField,String ownerName) {}
    public record VersionView(long id,long metricId,int versionNo,String definitionJson,String createdBy,LocalDateTime createdAt) {}
    public record LineageView(long id,long metricId,String metricCode,String metricName,String upstreamType,String upstreamRef,String downstreamType,String downstreamRef) {}
    public record DomainRequest(String domainCode,String domainName,String description,String ownerName,String status,Integer sortOrder) {}
    public record DomainView(long id,String domainCode,String domainName,String description,String ownerName,String status,int sortOrder,long themeCount,long metricCount,LocalDateTime updatedAt) {}
    public record ThemeRequest(long domainId,String themeCode,String themeName,String description,String ownerName,String status,Integer sortOrder) {}
    public record ThemeView(long id,long domainId,String domainName,String themeCode,String themeName,String description,String ownerName,String status,int sortOrder,long metricCount,LocalDateTime updatedAt) {}
}
