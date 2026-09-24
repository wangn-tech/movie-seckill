package com.wangning.seckill.vo;

import com.wangning.seckill.entity.Schedule;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 对外场次读模型；不暴露版本号、缓存布局 JSON 等持久化细节。 */
public record ScheduleVO(
        Long id,
        Long movieId,
        Long cinemaId,
        String hallName,
        LocalDate showDate,
        String showTime,
        Integer totalSeats,
        Integer availableSeats,
        Integer seatRows,
        Integer seatCols,
        BigDecimal price,
        Integer status
) {
    public static ScheduleVO from(Schedule schedule) {
        if (schedule == null) {
            return null;
        }
        return new ScheduleVO(schedule.getId(), schedule.getMovieId(), schedule.getCinemaId(),
                schedule.getHallName(), schedule.getShowDate(), schedule.getShowTime(), schedule.getTotalSeats(),
                schedule.getAvailableSeats(), schedule.getSeatRows(), schedule.getSeatCols(), schedule.getPrice(),
                schedule.getStatus());
    }
}
