package com.wangning.seckill.vo;

import java.math.BigDecimal;
import java.util.List;

public record SeatLayoutVO(
        Long scheduleId,
        int rows,
        int cols,
        int totalSeats,
        int availableSeats,
        BigDecimal price,
        List<Seat> unavailableSeats,
        List<SeatState> lockedSeats) {

    public record Seat(int row, int col) {
    }

    public record SeatState(int row, int col, String status) {
    }
}
