package com.helmsail.seckill.service.seckill;

import com.helmsail.seckill.base.seckill.SeckillRequest;
import com.helmsail.seckill.base.seckill.SeckillResultVO;
import com.helmsail.seckill.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @PostMapping
    public Result<String> seckill(@Valid @RequestBody SeckillRequest request) {
        return Result.success(seckillService.executeSeckill(request));
    }

    @GetMapping("/poll")
    public Result<SeckillResultVO> poll(@RequestParam String traceId) {
        return Result.success(seckillService.pollResult(traceId));
    }
}
