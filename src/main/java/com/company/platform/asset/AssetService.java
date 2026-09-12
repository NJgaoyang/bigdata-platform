package com.company.platform.asset;

import com.company.platform.common.BadRequestException;
import com.company.platform.datasource.DataSourceService;
import com.company.platform.datasource.DataSourceType;
import com.company.platform.metadata.MetadataService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AssetService {
    private final JdbcTemplate jdbc; private final DataSourceService dataSources; private final MetadataService metadata;
    public AssetService(JdbcTemplate jdbc,DataSourceService dataSources,MetadataService metadata){this.jdbc=jdbc;this.dataSources=dataSources;this.metadata=metadata;}

    public List<AssetItem> catalog(String username){Set<String> favorites=favoriteRefs(username);List<AssetItem> out=new ArrayList<>();
        dataSources.list().stream().filter(ds->ds.type()== DataSourceType.STARROCKS&&ds.metadataVisible()).forEach(ds->{
            try{for(var db:metadata.databases(ds.id(),ds.type())){try{for(var table:metadata.tables(ds.id(),db.name())){String ref="TABLE:"+ds.id()+":"+db.name()+"."+table.name();out.add(new AssetItem("TABLE",ref,table.name(),table.comment(),null,"ACTIVE",ds.name(),db.name()+"."+table.name(),favorites.contains(ref)));}}catch(RuntimeException ignored){}}}catch(RuntimeException ignored){}
        });
        jdbc.query("SELECT id,metric_code,metric_name,description,owner_name,status,business_domain FROM metric_definition WHERE status='CERTIFIED' ORDER BY metric_name",rs->{String ref="METRIC:"+rs.getLong("id");out.add(new AssetItem("METRIC",ref,rs.getString("metric_name"),rs.getString("description"),rs.getString("owner_name"),rs.getString("status"),rs.getString("business_domain"),rs.getString("metric_code"),favorites.contains(ref)));});
        jdbc.query("SELECT * FROM dataset_definition WHERE status='ACTIVE' ORDER BY dataset_name",rs->{String ref="DATASET:"+rs.getLong("id");out.add(new AssetItem("DATASET",ref,rs.getString("dataset_name"),rs.getString("description"),rs.getString("owner_name"),rs.getString("status"),String.valueOf(rs.getObject("source_datasource_id")),rs.getString("source_database")+"."+rs.getString("source_table"),favorites.contains(ref)));});
        return out;
    }
    public List<AssetItem> favorites(String username){return catalog(username).stream().filter(AssetItem::favorite).toList();}
    @Transactional public void favorite(String username,String type,String ref){if(ref==null||ref.isBlank())throw new BadRequestException("assetRef 不能为空");jdbc.update("INSERT INTO asset_favorite(username,asset_type,asset_ref) VALUES(?,?,?) ON DUPLICATE KEY UPDATE asset_ref=VALUES(asset_ref)",operator(username),type==null?"UNKNOWN":type.toUpperCase(Locale.ROOT),ref);}
    @Transactional public void unfavorite(String username,String ref){jdbc.update("DELETE FROM asset_favorite WHERE username=? AND asset_ref=?",operator(username),ref);}
    public List<DatasetView> datasets(){return jdbc.query("SELECT * FROM dataset_definition ORDER BY updated_at DESC",(rs,n)->dataset(rs));}
    @Transactional public DatasetView saveDataset(Long id,DatasetRequest r){if(r.datasetCode()==null||r.datasetCode().isBlank()||r.datasetName()==null||r.datasetName().isBlank())throw new BadRequestException("数据集编码和名称不能为空");if(id==null){jdbc.update("INSERT INTO dataset_definition(dataset_code,dataset_name,description,source_datasource_id,source_database,source_table,owner_name,status) VALUES(?,?,?,?,?,?,?,'ACTIVE')",code(r.datasetCode()),r.datasetName(),r.description(),r.sourceDataSourceId(),r.sourceDatabase(),r.sourceTable(),r.ownerName());id=jdbc.queryForObject("SELECT id FROM dataset_definition WHERE dataset_code=?",Long.class,code(r.datasetCode()));}else{jdbc.update("UPDATE dataset_definition SET dataset_code=?,dataset_name=?,description=?,source_datasource_id=?,source_database=?,source_table=?,owner_name=?,status=? WHERE id=?",code(r.datasetCode()),r.datasetName(),r.description(),r.sourceDataSourceId(),r.sourceDatabase(),r.sourceTable(),r.ownerName(),r.status()==null?"ACTIVE":r.status(),id);}return dataset(id);}
    @Transactional public void deleteDataset(long id){jdbc.update("DELETE FROM dataset_definition WHERE id=?",id);jdbc.update("DELETE FROM asset_favorite WHERE asset_ref=?","DATASET:"+id);}
    public DatasetView dataset(long id){return jdbc.query("SELECT * FROM dataset_definition WHERE id=?",(rs,n)->dataset(rs),id).stream().findFirst().orElseThrow(()->new BadRequestException("数据集不存在："+id));}
    private Set<String> favoriteRefs(String username){return new HashSet<>(jdbc.query("SELECT asset_ref FROM asset_favorite WHERE username=?",(rs,n)->rs.getString(1),operator(username)));}
    private DatasetView dataset(java.sql.ResultSet rs)throws java.sql.SQLException{var c=rs.getTimestamp("created_at");var u=rs.getTimestamp("updated_at");return new DatasetView(rs.getLong("id"),rs.getString("dataset_code"),rs.getString("dataset_name"),rs.getString("description"),rs.getObject("source_datasource_id",Long.class),rs.getString("source_database"),rs.getString("source_table"),rs.getString("owner_name"),rs.getString("status"),c==null?null:c.toLocalDateTime(),u==null?null:u.toLocalDateTime());}
    private String operator(String v){return v==null||v.isBlank()?"admin":v.trim();}
    private String code(String value){return value.trim().replaceAll("[^A-Za-z0-9_]","_");}
    public record AssetItem(String type,String ref,String name,String description,String owner,String status,String source,String detail,boolean favorite){}
    public record DatasetRequest(String datasetCode,String datasetName,String description,Long sourceDataSourceId,String sourceDatabase,String sourceTable,String ownerName,String status){}
    public record DatasetView(long id,String datasetCode,String datasetName,String description,Long sourceDataSourceId,String sourceDatabase,String sourceTable,String ownerName,String status,LocalDateTime createdAt,LocalDateTime updatedAt){}
}
