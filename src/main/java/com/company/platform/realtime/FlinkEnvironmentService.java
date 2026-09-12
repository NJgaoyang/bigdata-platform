package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.company.platform.common.NotFoundException;
import com.company.platform.datasource.PasswordCipher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FlinkEnvironmentService {
    private final JdbcTemplate jdbc;
    private final PasswordCipher cipher;
    public FlinkEnvironmentService(JdbcTemplate jdbc, PasswordCipher cipher) { this.jdbc=jdbc; this.cipher=cipher; }

    public List<FlinkEnvironmentView> list() {
        return jdbc.query("SELECT * FROM flink_environment ORDER BY default_environment DESC,name", (rs,n)->view(rs));
    }
    public FlinkEnvironmentView get(long id) {
        return jdbc.query("SELECT * FROM flink_environment WHERE id=?", (rs,n)->view(rs), id).stream().findFirst()
                .orElseThrow(()->new NotFoundException("Flink 环境不存在："+id));
    }
    @Transactional
    public FlinkEnvironmentView save(Long id, RealtimeRequests.EnvironmentRequest r) {
        boolean enabled=r.enabled()==null||r.enabled(); boolean def=Boolean.TRUE.equals(r.defaultEnvironment());
        if(def) jdbc.update("UPDATE flink_environment SET default_environment=FALSE");
        String password = r.sshPassword();
        String encrypted = password==null||password.isBlank()||"***".equals(password) ? null : cipher.encrypt(password);
        if(id==null) {
            jdbc.update("INSERT INTO flink_environment(name,engine_type,deployment_mode,submitter_type,rest_url,flink_home,flink_cdc_home,java_home,flink_version,flink_cdc_version,ssh_host,ssh_port,ssh_username,ssh_password_ciphertext,enabled,default_environment) VALUES(?,'FLINK_CDC',?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    r.name(), value(r.deploymentMode(),"REMOTE"), value(r.submitterType(),"LOCAL"), r.restUrl(),r.flinkHome(),r.flinkCdcHome(),r.javaHome(),r.flinkVersion(),r.flinkCdcVersion(),r.sshHost(),r.sshPort()==null?22:r.sshPort(),r.sshUsername(),encrypted,enabled,def);
            Long newId=jdbc.queryForObject("SELECT id FROM flink_environment WHERE name=?",Long.class,r.name()); return get(newId);
        }
        get(id);
        if(encrypted==null) encrypted=jdbc.queryForObject("SELECT ssh_password_ciphertext FROM flink_environment WHERE id=?",String.class,id);
        jdbc.update("UPDATE flink_environment SET name=?,deployment_mode=?,submitter_type=?,rest_url=?,flink_home=?,flink_cdc_home=?,java_home=?,flink_version=?,flink_cdc_version=?,ssh_host=?,ssh_port=?,ssh_username=?,ssh_password_ciphertext=?,enabled=?,default_environment=? WHERE id=?",
                r.name(),value(r.deploymentMode(),"REMOTE"),value(r.submitterType(),"LOCAL"),r.restUrl(),r.flinkHome(),r.flinkCdcHome(),r.javaHome(),r.flinkVersion(),r.flinkCdcVersion(),r.sshHost(),r.sshPort()==null?22:r.sshPort(),r.sshUsername(),encrypted,enabled,def,id);
        return get(id);
    }
    @Transactional public void delete(long id){ get(id); jdbc.update("DELETE FROM flink_environment WHERE id=?",id); }
    public RuntimeEnvironment runtime(long id) {
        FlinkEnvironmentView v=get(id); String enc=jdbc.queryForObject("SELECT ssh_password_ciphertext FROM flink_environment WHERE id=?",String.class,id);
        return new RuntimeEnvironment(v, enc==null||enc.isBlank()?"":cipher.decrypt(enc));
    }
    public FlinkEnvironmentView defaultEnvironment(){ return list().stream().filter(FlinkEnvironmentView::defaultEnvironment).findFirst().orElseGet(()->list().stream().filter(FlinkEnvironmentView::enabled).findFirst().orElseThrow(()->new BadRequestException("请先配置 Flink 运行环境"))); }
    private FlinkEnvironmentView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        var c=rs.getTimestamp("created_at"); var u=rs.getTimestamp("updated_at");
        return new FlinkEnvironmentView(rs.getLong("id"),rs.getString("name"),rs.getString("engine_type"),rs.getString("deployment_mode"),rs.getString("submitter_type"),rs.getString("rest_url"),rs.getString("flink_home"),rs.getString("flink_cdc_home"),rs.getString("java_home"),rs.getString("flink_version"),rs.getString("flink_cdc_version"),rs.getString("ssh_host"),rs.getInt("ssh_port"),rs.getString("ssh_username"),rs.getBoolean("enabled"),rs.getBoolean("default_environment"),c==null?LocalDateTime.now():c.toLocalDateTime(),u==null?LocalDateTime.now():u.toLocalDateTime());
    }
    private String value(String v,String d){return v==null||v.isBlank()?d:v.trim().toUpperCase();}
    public record RuntimeEnvironment(FlinkEnvironmentView view,String sshPassword){}
}
