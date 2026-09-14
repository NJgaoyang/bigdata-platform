package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DataSourceType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FlinkCdcConfigBuilder {
    private final DataSourceService dataSources;
    public FlinkCdcConfigBuilder(DataSourceService dataSources){this.dataSources=dataSources;}

    @SuppressWarnings("unchecked")
    public String build(Map<String,Object> spec, boolean maskSecrets){
        if ("YAML".equalsIgnoreCase(str(spec.get("editorMode")))) {
            String yaml = str(spec.get("yaml"));
            if (yaml.isBlank()) throw new BadRequestException("YAML 模式下配置不能为空");
            if (!yaml.contains("source:") || !yaml.contains("sink:") || !yaml.contains("pipeline:"))
                throw new BadRequestException("YAML 至少需要 source / sink / pipeline 配置段");
            return maskSecrets ? yaml.replaceAll("(?im)(password\s*:\s*)[^\n\r]+", "$1'***'") : yaml;
        }
        long sourceId=longValue(spec.get("sourceDataSourceId")); long sinkId=longValue(spec.get("sinkDataSourceId"));
        DataSourceService.ConnectionInfo source=dataSources.connectionInfo(sourceId), sink=dataSources.connectionInfo(sinkId);
        var sourceView=dataSources.get(sourceId); var sinkView=dataSources.get(sinkId);
        if(source.type()!= DataSourceType.MYSQL) throw new BadRequestException("实时同步源仅支持 MySQL");
        if(sink.type()!=DataSourceType.STARROCKS) throw new BadRequestException("实时同步目标仅支持 StarRocks");
        String sourceDb=str(spec.getOrDefault("sourceDatabase",source.databaseName()));
        String sinkDb=str(spec.getOrDefault("sinkDatabase",sink.databaseName()));
        List<Map<String,Object>> tables=(List<Map<String,Object>>)spec.getOrDefault("tables",List.of());
        if(tables.isEmpty()) throw new BadRequestException("至少选择一张同步表");
        String password=maskSecrets?"***":source.password(), sinkPassword=maskSecrets?"***":sink.password();
        StringBuilder y=new StringBuilder();
        y.append("source:\n  type: mysql\n  hostname: ").append(q(sourceView.host())).append("\n  port: ").append(sourceView.port())
         .append("\n  username: ").append(q(source.username())).append("\n  password: ").append(q(password))
         .append("\n  tables: ").append(q(sourceDb+"."+tableRegex(tables,"sourceTable")));
        String serverId=str(spec.get("serverId"));
        if(!serverId.isBlank()) y.append("\n  server-id: ").append(q(serverId));
        y.append("\n  scan.startup.mode: ").append(q(startup(str(spec.getOrDefault("startupMode","initial")))))
         .append("\n  scan.incremental.snapshot.chunk.size: ").append(intValue(spec.get("chunkSize"),8096))
         .append("\n  scan.snapshot.fetch.size: ").append(intValue(spec.get("fetchSize"),1024))
         .append("\n  scan.incremental.snapshot.backfill.skip: false")
         .append("\n  treat-tinyint1-as-boolean.enabled: false")
         .append("\n  debezium.bigint.unsigned.handling.mode: ").append(q("precise"))
         .append("\n  heartbeat.interval: ").append(intValue(spec.get("heartbeatMs"),30000)).append("ms")
         .append("\n  server-time-zone: ").append(q(sourceView.timezone()==null||sourceView.timezone().isBlank()?"Asia/Shanghai":sourceView.timezone())).append("\n");
        if("timestamp".equalsIgnoreCase(str(spec.get("startupMode")))) y.append("  scan.startup.timestamp-millis: ").append(longValue(spec.get("timestampMillis"))).append("\n");
        Map<String,Object> offset=spec.get("specificOffset") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        if("specific-offset".equalsIgnoreCase(str(spec.get("startupMode")))){
            if(offset.get("gtid")!=null)y.append("  scan.startup.specific-offset.gtid-set: ").append(q(str(offset.get("gtid")))).append("\n");
            else {y.append("  scan.startup.specific-offset.file: ").append(q(str(offset.get("file")))).append("\n");y.append("  scan.startup.specific-offset.pos: ").append(longValue(offset.get("pos"))).append("\n");}
        }
        y.append("sink:\n  type: starrocks\n  jdbc-url: ").append(q("jdbc:mysql://"+sinkView.host()+":"+sinkView.port()))
         .append("\n  load-url: ").append(q(sinkView.host()+":8030"))
         .append("\n  username: ").append(q(sink.username())).append("\n  password: ").append(q(sinkPassword))
         .append("\n  table.create.properties.replication_num: '1'\n");
        Map<String,Object> sinkCfg=spec.get("sink") instanceof Map<?,?>m?(Map<String,Object>)m:Map.of();
        long maxBytes = sinkCfg.get("maxBytes") == null ? 67108864L : longValue(sinkCfg.get("maxBytes"));
        if(maxBytes < 67108864L) maxBytes = 67108864L; // StarRocks connector minimum is 64 MiB.
        String labelPrefix=str(spec.getOrDefault("labelPrefix","datasphere_rt_draft"));
        y.append("  sink.buffer-flush.max-bytes: ").append(maxBytes).append("\n")
         .append("  sink.buffer-flush.interval-ms: ").append(intValue(sinkCfg.get("flushIntervalMs"),2000)).append("\n")
         .append("  sink.semantic: ").append(q("exactly-once")).append("\n")
         .append("  sink.version: ").append(q("V2")).append("\n")
         .append("  sink.label-prefix: ").append(q(labelPrefix)).append("\n")
         .append("  sink.at-least-once.use-transaction-stream-load: false\n")
         .append("  sink.properties.max_filter_ratio: '0'\n")
         .append("  sink.properties.strict_mode: 'true'\n")
         .append("  sink.properties.enable_merge_commit: 'false'\n");
        String dataFilter=str(spec.get("dataFilter"));
        if(!dataFilter.isBlank()){
            y.append("transform:\n");
            for(Map<String,Object> t:tables) y.append("  - source-table: ").append(q(sourceDb+"."+str(t.get("sourceTable")))).append("\n    filter: ").append(q(dataFilter)).append("\n");
        }
        y.append("pipeline:\n  name: ").append(q(str(spec.getOrDefault("name","datasphere-realtime")))).append("\n")
         .append("  parallelism: ").append(intValue(spec.get("parallelism"),1)).append("\n")
         .append("  schema.change.behavior: ").append(q(str(spec.getOrDefault("schemaEvolution","EXCEPTION")).toLowerCase(Locale.ROOT))).append("\n")
         .append("  local-time-zone: ").append(q(sourceView.timezone()==null||sourceView.timezone().isBlank()?"Asia/Shanghai":sourceView.timezone())).append("\n");
        y.append("route:\n");
        for(Map<String,Object> t:tables) y.append("  - source-table: ").append(q(sourceDb+"."+str(t.get("sourceTable")))).append("\n    sink-table: ").append(q(sinkDb+"."+str(t.getOrDefault("targetTable",t.get("sourceTable"))))).append("\n");
        return y.toString();
    }
    public void validate(Map<String,Object> spec){build(spec,true); if ("YAML".equalsIgnoreCase(str(spec.get("editorMode")))) return; String mode=str(spec.getOrDefault("startupMode","initial")); if(!Set.of("initial","latest-offset","timestamp","specific-offset").contains(mode))throw new BadRequestException("startupMode 不支持："+mode); int p=intValue(spec.get("parallelism"),1);if(p<1||p>128)throw new BadRequestException("parallelism 必须在 1-128");}
    private long longValue(Object o){if(o==null)return 0;return o instanceof Number n?n.longValue():Long.parseLong(String.valueOf(o));}
    private int intValue(Object o,int d){if(o==null)return d;return o instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(o));}
    private String str(Object o){return o==null?"":String.valueOf(o).trim();}
    private String q(String s){return "'"+(s==null?"":s.replace("'","''"))+"'";}
    private String tableRegex(List<Map<String,Object>> tables,String key){return "("+String.join("|",tables.stream().map(t->str(t.get(key)).replace(".","\\.")).toList())+")";}
    private String startup(String mode){return switch(mode){case "latest-offset"->"latest-offset";case "timestamp"->"timestamp";case "specific-offset"->"specific-offset";default->"initial";};}
}
