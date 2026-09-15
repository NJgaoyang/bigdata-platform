package com.company.platform.realtime;

import com.company.platform.common.BadRequestException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class FlinkRestClient {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    public FlinkRestClient(ObjectMapper mapper){this.mapper=mapper;}
    public JsonNode get(String base,String path){return send(base,path,"GET",null);}
    public JsonNode patch(String base,String path){return send(base,path,"PATCH",null);}
    public JsonNode post(String base,String path,JsonNode body){return send(base,path,"POST",body);}
    private JsonNode send(String base,String path,String method,JsonNode body){
        if(base==null||base.isBlank()) throw new BadRequestException("Flink 环境未配置 REST URL");
        try{
            String url=base.replaceAll("/+$","")+path;
            HttpRequest.Builder b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
            HttpRequest req;
            if("PATCH".equals(method)) req=b.method("PATCH",HttpRequest.BodyPublishers.noBody()).build();
            else if("POST".equals(method)) req=b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body==null?"{}":mapper.writeValueAsString(body))).build();
            else req=b.GET().build();
            HttpResponse<String> resp=client.send(req,HttpResponse.BodyHandlers.ofString());
            if(resp.statusCode()<200||resp.statusCode()>=300) throw new BadRequestException("Flink REST 返回 "+resp.statusCode()+": "+resp.body());
            return resp.body()==null||resp.body().isBlank()?mapper.createObjectNode():mapper.readTree(resp.body());
        }catch(BadRequestException ex){throw ex;}catch(Exception ex){throw new BadRequestException("Flink REST 调用失败："+ex.getMessage());}
    }
}
