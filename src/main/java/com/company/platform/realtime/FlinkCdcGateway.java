package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FlinkCdcGateway {
    private static final Pattern JOB_ID=Pattern.compile("(?i)(?:JobID|Job ID|jobId)[^0-9a-f]*([0-9a-f]{32})");
    private final FlinkRestClient rest;
    public FlinkCdcGateway(FlinkRestClient rest){this.rest=rest;}
    public SubmitResult submit(FlinkEnvironmentService.RuntimeEnvironment env,String yaml){ return submit(env,yaml,Map.of()); }
    public SubmitResult submit(FlinkEnvironmentService.RuntimeEnvironment env,String yaml,Map<String,Object> spec){
        if(!env.view().enabled()) throw new BadRequestException("Flink 环境已禁用");
        yaml = sanitizeYaml(yaml);
        List<String> dynamic = dynamicOptions(spec);
        return "SSH".equalsIgnoreCase(env.view().submitterType())?submitSsh(env,yaml,dynamic):submitLocal(env,yaml,dynamic);
    }
    public void cancel(FlinkEnvironmentView env,String jobId){rest.patch(env.restUrl(),"/jobs/"+jobId+"?mode=cancel");}
    public JsonNode job(FlinkEnvironmentView env,String jobId){return rest.get(env.restUrl(),"/jobs/"+jobId);}
    public JsonNode checkpoints(FlinkEnvironmentView env,String jobId){return rest.get(env.restUrl(),"/jobs/"+jobId+"/checkpoints");}
    public JsonNode metrics(FlinkEnvironmentView env,String jobId){
        JsonNode job=rest.get(env.restUrl(),"/jobs/"+jobId);
        JsonNode jobMetrics=rest.get(env.restUrl(),"/jobs/"+jobId+"/metrics?get=uptime,runningTime,numRestarts,totalNumberOfCheckpoints,numberOfCompletedCheckpoints,numberOfFailedCheckpoints,lastCompletedCheckpointId,lastCheckpointDuration,lastCheckpointSize");
        ObjectNode result=JsonNodeFactory.instance.objectNode();
        result.set("job",jobMetrics);
        try { result.set("taskmanagers", rest.get(env.restUrl(), "/taskmanagers")); } catch (RuntimeException ignored) { }
        ArrayNode vertices=result.putArray("vertices");
        for(JsonNode vertex:job.path("vertices")){
            String vertexId=vertex.path("id").asText();
            if(vertexId.isBlank()) continue;
            ObjectNode item=vertices.addObject();
            item.put("id",vertexId);
            item.put("name",vertex.path("name").asText());
            item.put("status",vertex.path("status").asText());
            item.set("metrics",rest.get(env.restUrl(),"/jobs/"+jobId+"/vertices/"+vertexId+"/metrics?get=0.numRecordsIn,0.numRecordsOut,0.numBytesIn,0.numBytesOut,0.busyTimeMsPerSecond,0.backPressuredTimeMsPerSecond,0.idleTimeMsPerSecond"));
        }
        return result;
    }
    public JsonNode exceptions(FlinkEnvironmentView env,String jobId){return rest.get(env.restUrl(),"/jobs/"+jobId+"/exceptions");}
    private SubmitResult submitLocal(FlinkEnvironmentService.RuntimeEnvironment env,String yaml,List<String> dynamic){
        Path file=null;
        try{
            if(env.view().flinkCdcHome()==null||env.view().flinkCdcHome().isBlank())throw new BadRequestException("Flink CDC Home 未配置");
            file=Files.createTempFile("datasphere-cdc-",".yaml");Files.writeString(file,yaml,StandardCharsets.UTF_8);
            String script=Path.of(env.view().flinkCdcHome(),"bin","flink-cdc.sh").toString();
            List<String> command=new ArrayList<>(); command.add(script); command.addAll(dynamic); command.add(file.toString()); command.add("--flink-home"); command.add(env.view().flinkHome());
            ProcessBuilder pb=new ProcessBuilder(command);
            if(env.view().javaHome()!=null&&!env.view().javaHome().isBlank())pb.environment().put("JAVA_HOME",env.view().javaHome());
            Process proc=pb.redirectErrorStream(true).start();
            boolean done=proc.waitFor(Duration.ofSeconds(45).toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS);
            String out=new String(proc.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            if(!done){proc.destroyForcibly();throw new BadRequestException("Flink CDC 提交超时");}
            if(proc.exitValue()!=0)throw new BadRequestException("Flink CDC 提交失败："+out);
            String job=parseJobId(out);return new SubmitResult(job,out);
        }catch(BadRequestException ex){throw ex;}catch(Exception ex){throw new BadRequestException("Flink CDC 提交失败："+ex.getMessage());}
        finally{if(file!=null)try{Files.deleteIfExists(file);}catch(IOException ignored){}}
    }
    private SubmitResult submitSsh(FlinkEnvironmentService.RuntimeEnvironment env,String yaml,List<String> dynamic){
        Session session=null;ChannelSftp sftp=null;ChannelExec exec=null;String remote="/tmp/datasphere-cdc-"+UUID.randomUUID()+".yaml";
        try{
            JSch jsch=new JSch();
            String password=env.sshPassword();
            Path sshDir=Path.of(System.getProperty("user.home"),".ssh");
            Path privateKey=sshDir.resolve("id_ed25519");
            Path knownHosts=sshDir.resolve("known_hosts");
            if(password!=null&&!password.isBlank()) {
                // Password remains supported for legacy environments, but key-based auth is preferred.
            } else if(Files.isRegularFile(privateKey)) {
                jsch.addIdentity(privateKey.toString());
            } else {
                throw new BadRequestException("SSH 环境未配置密码，且未找到默认私钥："+privateKey);
            }
            if(Files.isRegularFile(knownHosts)) jsch.setKnownHosts(knownHosts.toString());
            session=jsch.getSession(env.view().sshUsername(),env.view().sshHost(),env.view().sshPort());
            if(password!=null&&!password.isBlank()) session.setPassword(password);
            session.setConfig("PreferredAuthentications",password!=null&&!password.isBlank()?"publickey,password":"publickey");
            session.setConfig("StrictHostKeyChecking",Files.isRegularFile(knownHosts)?"yes":"no");
            session.connect(10000);
            sftp=(ChannelSftp)session.openChannel("sftp");sftp.connect(5000);sftp.put(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),remote);sftp.disconnect();sftp=null;
            List<String> args=new ArrayList<>();args.add(env.view().flinkCdcHome()+"/bin/flink-cdc.sh");args.addAll(dynamic);args.add(remote);args.add("--flink-home");args.add(env.view().flinkHome());
            String command=String.join(" ",args.stream().map(this::shellQuote).toList());
            exec=(ChannelExec)session.openChannel("exec");exec.setCommand(command);ByteArrayOutputStream out=new ByteArrayOutputStream();exec.setOutputStream(out);exec.setErrStream(out);exec.connect(5000);
            long deadline=System.currentTimeMillis()+45000;while(!exec.isClosed()&&System.currentTimeMillis()<deadline)Thread.sleep(200);
            if(!exec.isClosed())throw new BadRequestException("SSH 提交 Flink CDC 超时");String text=out.toString(StandardCharsets.UTF_8);if(exec.getExitStatus()!=0)throw new BadRequestException("Flink CDC 提交失败："+text);return new SubmitResult(parseJobId(text),text);
        }catch(BadRequestException ex){throw ex;}catch(Exception ex){throw new BadRequestException("SSH 提交 Flink CDC 失败："+ex.getMessage());}
        finally{if(exec!=null)exec.disconnect();if(sftp!=null)sftp.disconnect();if(session!=null){try{ChannelSftp cleanup=(ChannelSftp)session.openChannel("sftp");cleanup.connect(2000);cleanup.rm(remote);cleanup.disconnect();}catch(Exception ignored){}session.disconnect();}}
    }
    @SuppressWarnings("unchecked")
    private List<String> dynamicOptions(Map<String,Object> spec){
        List<String> out=new ArrayList<>();
        Map<String,Object> checkpoint=spec.get("checkpoint") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        if(bool(checkpoint.get("enabled"),true)){
            addD(out,"execution.checkpointing.interval",seconds(checkpoint.get("intervalSec"),3));
            addD(out,"execution.checkpointing.timeout",seconds(checkpoint.get("timeoutSec"),600));
            addD(out,"execution.checkpointing.tolerable-failed-checkpoints",String.valueOf(intValue(checkpoint.get("tolerableFailures"),3)));
            addD(out,"execution.checkpointing.min-pause",seconds(checkpoint.get("minPauseSec"),0));
            addD(out,"execution.checkpointing.max-concurrent-checkpoints",String.valueOf(intValue(checkpoint.get("maxConcurrent"),1)));
            String ext=str(checkpoint.get("externalized"));
            addD(out,"execution.checkpointing.externalized-checkpoint-retention","DELETE".equalsIgnoreCase(ext)?"DELETE_ON_CANCELLATION":"RETAIN_ON_CANCELLATION");
            String storage=str(checkpoint.get("storage")); if(!storage.isBlank()) addD(out,"execution.checkpointing.dir",storage);
        }
        Map<String,Object> restart=spec.get("restart") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        String strategy=str(restart.getOrDefault("strategy","FIXED_DELAY")).toLowerCase(Locale.ROOT).replace('_','-');
        if("none".equals(strategy)) addD(out,"restart-strategy.type","disable");
        else if("failure-rate".equals(strategy)){
            addD(out,"restart-strategy.type","failure-rate");addD(out,"restart-strategy.failure-rate.max-failures-per-interval",String.valueOf(intValue(restart.get("attempts"),3)));addD(out,"restart-strategy.failure-rate.failure-rate-interval","5 min");addD(out,"restart-strategy.failure-rate.delay",seconds(restart.get("delaySec"),10));
        }else{
            addD(out,"restart-strategy.type","fixed-delay");addD(out,"restart-strategy.fixed-delay.attempts",String.valueOf(intValue(restart.get("attempts"),3)));addD(out,"restart-strategy.fixed-delay.delay",seconds(restart.get("delaySec"),10));
        }
        Map<String,Object> resources=spec.get("resources") instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        addIf(out,"jobmanager.memory.process.size",resources.get("jobManagerMemory"));
        addIf(out,"taskmanager.memory.process.size",resources.get("taskManagerMemory"));
        if(resources.get("taskSlots")!=null)addD(out,"taskmanager.numberOfTaskSlots",String.valueOf(intValue(resources.get("taskSlots"),2)));
        addIf(out,"state.backend.type",resources.get("stateBackend"));
        String advanced=str(resources.get("advancedParams"));
        for(String line:advanced.split("[\r\n,]+")){String v=line.trim();if(v.isBlank())continue;int idx=v.indexOf('=');if(idx<=0)throw new BadRequestException("高级 Flink 参数格式必须为 key=value");String key=v.substring(0,idx).trim(),value=v.substring(idx+1).trim();if(!key.matches("[A-Za-z0-9._-]+"))throw new BadRequestException("高级 Flink 参数名不合法："+key);if(isUnsupportedOption(key)) continue;addD(out,key,value);}
        return out;
    }
    private String sanitizeYaml(String yaml){
        if(yaml==null||yaml.isBlank()) return yaml==null?"":yaml;
        return Arrays.stream(yaml.split("\\R",-1))
                .filter(line -> !line.matches("(?i)^\\s*[\'\"]?scan\\.incremental\\.snapshot\\.backfill\\.skip[\'\"]?\\s*:.*$"))
                .collect(java.util.stream.Collectors.joining("\n"));
    }
    private boolean isUnsupportedOption(String key){return "scan.incremental.snapshot.backfill.skip".equalsIgnoreCase(str(key));}
    private void addIf(List<String> out,String key,Object value){String v=str(value);if(!v.isBlank())addD(out,key,v);}
    private void addD(List<String> out,String key,String value){if(value!=null&&!value.isBlank())out.add("-D"+key+"="+value);}
    private String seconds(Object value,int fallback){return intValue(value,fallback)+"s";}
    private int intValue(Object value,int fallback){if(value==null)return fallback;try{return value instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(value));}catch(Exception ex){return fallback;}}
    private boolean bool(Object value,boolean fallback){if(value==null)return fallback;return value instanceof Boolean b?b:Boolean.parseBoolean(String.valueOf(value));}
    private String str(Object value){return value==null?"":String.valueOf(value).trim();}
    private String shellQuote(String value){return "'"+String.valueOf(value).replace("'","'\"'\"'")+"'";}
    private String parseJobId(String text){Matcher m=JOB_ID.matcher(text==null?"":text);if(!m.find())throw new BadRequestException("Flink CDC 已返回但未识别到 Job ID，请检查提交日志："+text);return m.group(1);}
    public record SubmitResult(String jobId,String log){}
}
