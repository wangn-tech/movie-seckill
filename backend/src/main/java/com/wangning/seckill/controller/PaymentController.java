package com.wangning.seckill.controller;

import com.wangning.seckill.common.context.UserContextHolder;
import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "支付")
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "模拟支付")
    @PostMapping("/{orderNo}")
    public Result<Void> pay(@PathVariable String orderNo) {
        paymentService.pay(UserContextHolder.requireUserId(), orderNo);
        return Result.success();
    }
}
