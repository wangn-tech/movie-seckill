package com.wangning.seckill.service;

import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.vo.SeatLayoutVO;

import java.util.List;

public interface ScheduleService {

    List<Schedule> listByMovieAndCinema(Long movieId, Long cinemaId);

    Schedule detail(Long scheduleId);

    SeatLayoutVO seatLayout(Long scheduleId);
}
