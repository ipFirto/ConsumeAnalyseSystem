package com.iptnet.consume.controller.category;


import com.iptnet.consume.common.Result;
import com.iptnet.consume.service.category.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping("/{platformId}/{categoryName}")
    public Result getCategoryData(@PathVariable("platformId") Integer platformId, @PathVariable("categoryName") String categoryName) {
        List<HashMap<String, Object>> categoryData = categoryService.getCategoryData(platformId, categoryName);
//        System.out.println("platformId=" + platformId + ", categoryName=" + categoryName);
        return Result.success(categoryData);
    }

}
