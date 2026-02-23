package com.iptnet.consume.controller.product;

import com.iptnet.consume.common.Result;
import com.iptnet.consume.service.product.ProductService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /** 你的 map 里产品主键字段名（默认 p.id -> id） */
    private static final String ID_KEY = "id";

    /** session key 前缀：按 platformId 维度记住 lastId */
    private static final String SESSION_LAST_ID_PREFIX = "product:lastId:";

    /**
     * 产品列表：
     * - 第一次访问：不传 productId（且 session 没记录）=> listAll
     * - 后续访问：传 productId（或 session 有记录）=> listNew
     *
     * 调用示例：
     * 1) 第一次：GET /product/list?platformId=1&limit=50
     * 2) 后续：  GET /product/list?platformId=1&productId=1877&limit=50&offset=0
     *    （如果你不想前端传 productId，也可以不传；后端会用 session 里的 lastId）
     */
    @GetMapping("/list")
    public Result list(
            @RequestParam Integer platformId,
            @RequestParam(required = false) Integer productId,
            @RequestParam(defaultValue = "1") Integer status,
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset,
            HttpSession session
    ) {
        String sessionKey = SESSION_LAST_ID_PREFIX + platformId;

        // 1) 优先用前端传来的 productId；如果没传，就用 session 里记住的 lastId
        Integer lastId = productId != null ? productId : (Integer) session.getAttribute(sessionKey);

        List<HashMap<String, Object>> data;

        // 2) 第一次访问：lastId 为空 -> 全量首屏
        if (lastId == null) {
            data = productService.listAll(platformId, status, limit);
            Integer maxId = extractMaxId(data);
            if (maxId != null) {
                session.setAttribute(sessionKey, maxId);
            }
            return Result.success(data); // 如果你项目里不是 success，请改成你 Result 的成功方法
        }

        // 3) 后续访问：增量/分页
        data = productService.listNew(platformId, lastId, status, limit, offset);

        // 4) 更新 session 里的 lastId：取本次返回数据的最大 id
        Integer newMaxId = extractMaxId(data);
        if (newMaxId != null && newMaxId > lastId) {
            session.setAttribute(sessionKey, newMaxId);
        }

        return Result.success(data);
    }

    @GetMapping("/listByPlatform")
    public Result listByPlatform(
            @RequestParam Integer platformId,
            @RequestParam(defaultValue = "1") Integer status,
            @RequestParam(defaultValue = "5000") Integer limit,
            @RequestParam(defaultValue = "0") Integer offset
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 10000);
        int safeOffset = Math.max(offset, 0);
        return Result.success(productService.listByPlatform(platformId, status, safeLimit, safeOffset));
    }

    @GetMapping("/one")
    public Result getOne(@RequestParam Integer productId) {
        return Result.success(productService.getOneProduct(productId));
    }

    @PostMapping("/insert")
    public Result insert(
            @RequestParam Integer platformId,
            @RequestParam String brand,
            @RequestParam String productName,
            @RequestParam BigDecimal amount,
            @RequestParam String category
    ) {
        Boolean ok = productService.insert(platformId, brand, productName, amount,category);
        return Result.success(ok);
    }

    /**
     * 软删除：你 service 是 delete(status, productId)，默认把 status 置为 0
     */
    @PostMapping("/delete")
    public Result delete(
            @RequestParam Integer productId,
            @RequestParam(defaultValue = "0") Integer status
    ) {
        Integer ok = productService.delete(status, productId);
        return Result.success(ok);
    }

    @PostMapping("/update")
    public Result update(
            @RequestParam Integer productId,
            @RequestParam(required = false) String productName,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category
    ) {
        Integer ok = productService.update(productName, brand, category, productId);
        return Result.success(ok);
    }

    @GetMapping("/showCategory")
    public Result showCategory(@RequestParam Integer platformId) {
        return Result.success(productService.showCategory(platformId));
    }

    @GetMapping("/showPlatform")
    public Result showPlatform() {
        return Result.success(productService.showPlatform());
    }

    /**
     * 从 List<Map> 里取最大 id（用于更新 lastId）
     */
    private Integer extractMaxId(List<HashMap<String, Object>> list) {
        if (list == null || list.isEmpty()) return null;

        Integer max = null;
        for (HashMap<String, Object> row : list) {
            if (row == null) continue;
            Object v = row.get(ID_KEY);
            if (v == null) continue;

            Integer id;
            if (v instanceof Number) {
                id = ((Number) v).intValue();
            } else {
                try {
                    id = Integer.parseInt(v.toString());
                } catch (Exception ignore) {
                    continue;
                }
            }

            if (max == null || id > max) max = id;
        }
        return max;
    }
}
