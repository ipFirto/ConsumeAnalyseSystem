package com.iptnet.consume.service.consume;



import com.iptnet.consume.dto.ConsumeOrderDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ConsumeService {

    void enqueueOrder(Integer uid, String orderNo, Integer productId, Integer cityId,
                      java.math.BigDecimal amount, String remark);

    void autoInsert(Integer userId, String orderNo, Integer productId, Integer cityId, BigDecimal amount, String remark);

    List<ConsumeOrderDTO> orderByOrigen(Integer platformId);

    List<ConsumeOrderDTO> orderNewByOrigen(Integer platformId, Integer id);

    List<String> showBarByPlatform();

    List<Map<String,Object>> showLineByPlatform(Integer platformId);

    List<Map<String,Object>> showSmoothByOriginal();

    List<Map<String,Object>> showUserOrder(Integer Id);
}
