package com.iptnet.consume.dao;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Category {

    private Integer id;
    private Integer parentId;
    private String name;
    private Integer level;
    private Integer sort;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
