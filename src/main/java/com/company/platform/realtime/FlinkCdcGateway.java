package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FlinkCdcGateway {
    private static final Pattern JOB_ID=Pattern.compile("(?i)(?:JobID|Job ID|jobId)[^0-9a-f]*([0-9a-f]{32})");
    private final FlinkRestClient rest;
    public FlinkCdcGateway(FlinkRestClient rest){this.rest=rest;}
    public SubmitResult submit(FlinkEnvironmentService.RuntimeEnvironment env,String yaml){
        if(!env.view().enabled()) throw new BadRequestException("Flink 环境已禁用");
        return "SSH".equalsIgnoreCase(env.view().submitterType())?submitSsh(env,yaml):submitLocal(env,yaml);
    }
    public void cancel(FlinkEnvironmentView env,String jobId){rest.patch(env.restUrl(),"/jobs/"+jobId+"?mode=cancel");}
    public JsonNode job(FlinkEnvironmentView env,String jobId){return rest.get(env.restUrl(),"/jobs/"+jobId);}
    public JsonNode checkpoints(FlinkEnvironmentView env,String jobId){return rest.get(env.restUrl(),"/jobs/"+jobId+"/checkpoints");}
    public JsonNode metrics(FlinkEnvironmentView env,String jobId){return rest.get(env.restUrl(),"/jobs/"+jobId+"/metrics?get=numRecordsIn,numRecordsOut,numBytesIn,numBytesOut,busyTimeMsPerSecond,backPressuredTimeMsPerSecond,idleTimeMsPerSecond");}
    public JsonNode exceptions(FlinkEnvironmentView env,String jobId){return rest.get(env.restUrl(),"/jobs/"+jobId+"/exceptions");}
    private SubmitResult submitLocal(FlinkEnvironmentService.RuntimeEnvironment env,String yaml){
        Path file=null;
        try{
            if(env.view().flinkCdcHome()==null||env.view().flinkCdcHome().isBlank())throw new BadRequestException("Flink CDC Home 未配置");
            file=Files.createTempFile("datasphere-cdc-",".yaml");Files.writeString(file,yaml,StandardCharsets.UTF_8);
            String script=Path.of(env.view().flinkCdcHome(),"bin","flink-cdc.sh").toString();
            ProcessBuilder pb=new ProcessBuilder(script,file.toString(),"--flink-home",env.view().flinkHome());
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
    private SubmitResult submitSsh(FlinkEnvironmentService.RuntimeEnvironment env,String yaml){
        Session session=null;ChannelSftp sftp=null;ChannelExec exec=null;String remote="/tmp/datasphere-cdc-"+UUID.randomUUID()+".yaml";
        try{
            JSch jsch=new JSch();session=jsch.getSession(env.view().sshUsername(),env.view().sshHost(),env.view().sshPort());session.setPassword(env.sshPassword());session.setConfig("StrictHostKeyChecking","no");session.connect(10000);
            sftp=(ChannelSftp)session.openChannel("sftp");sftp.connect(5000);sftp.put(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)),remote);sftp.disconnect();sftp=null;
            String command=env.view().flinkCdcHome()+"/bin/flink-cdc.sh "+remote+" --flink-home "+env.view().flinkHome();
            exec=(ChannelExec)session.openChannel("exec");exec.setCommand(command);ByteArrayOutputStream out=new ByteArrayOutputStream();exec.setOutputStream(out);exec.setErrStream(out);exec.connect(5000);
            long deadline=System.currentTimeMillis()+45000;while(!exec.isClosed()&&System.currentTimeMillis()<deadline)Thread.sleep(200);
            if(!exec.isClosed())throw new BadRequestException("SSH 提交 Flink CDC 超时");String text=out.toString(StandardCharsets.UTF_8);if(exec.getExitStatus()!=0)throw new BadRequestException("Flink CDC 提交失败："+text);return new SubmitResult(parseJobId(text),text);
        }catch(BadRequestException ex){throw ex;}catch(Exception ex){throw new BadRequestException("SSH 提交 Flink CDC 失败："+ex.getMessage());}
        finally{if(exec!=null)exec.disconnect();if(sftp!=null)sftp.disconnect();if(session!=null){try{ChannelSftp cleanup=(ChannelSftp)session.openChannel("sftp");cleanup.connect(2000);cleanup.rm(remote);cleanup.disconnect();}catch(Exception ignored){}session.disconnect();}}
    }
    private String parseJobId(String text){Matcher m=JOB_ID.matcher(text==null?"":text);if(!m.find())throw new BadRequestException("Flink CDC 已返回但未识别到 Job ID，请检查提交日志："+text);return m.group(1);}
    public record SubmitResult(String jobId,String log){}
}
