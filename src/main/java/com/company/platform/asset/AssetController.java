package com.company.platform.asset;

import com.company.platform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetController {
    private final AssetService service; public AssetController(AssetService service){this.service=service;}
    @GetMapping("/catalog") public Result<List<AssetService.AssetItem>> catalog(HttpServletRequest req){return Result.ok(service.catalog(operator(req)));}
    @GetMapping("/favorites") public Result<List<AssetService.AssetItem>> favorites(HttpServletRequest req){return Result.ok(service.favorites(operator(req)));}
    @PostMapping("/favorites") public Result<Void> favorite(@RequestBody FavoriteRequest r,HttpServletRequest req){service.favorite(operator(req),r.assetType(),r.assetRef());return Result.ok(null,"已收藏");}
    @DeleteMapping("/favorites") public Result<Void> unfavorite(@RequestParam String assetRef,HttpServletRequest req){service.unfavorite(operator(req),assetRef);return Result.ok(null,"已取消收藏");}
    @GetMapping("/datasets") public Result<List<AssetService.DatasetView>> datasets(){return Result.ok(service.datasets());}
    @PostMapping("/datasets") public Result<AssetService.DatasetView> createDataset(@RequestBody AssetService.DatasetRequest r){return Result.ok(service.saveDataset(null,r),"数据集已创建");}
    @PutMapping("/datasets/{id}") public Result<AssetService.DatasetView> updateDataset(@PathVariable long id,@RequestBody AssetService.DatasetRequest r){return Result.ok(service.saveDataset(id,r),"数据集已更新");}
    @DeleteMapping("/datasets/{id}") public Result<Void> deleteDataset(@PathVariable long id){service.deleteDataset(id);return Result.ok(null,"数据集已删除");}
    private String operator(HttpServletRequest req){Object o=req.getAttribute("platform.operator");return o==null?"admin":String.valueOf(o);}
    public record FavoriteRequest(String assetType,String assetRef){}
}
