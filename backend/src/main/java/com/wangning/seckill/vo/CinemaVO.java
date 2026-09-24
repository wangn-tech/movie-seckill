package com.wangning.seckill.vo;

import com.wangning.seckill.entity.Cinema;

/** 对外影院读模型。 */
public record CinemaVO(Long id, String name, String address, String city) {
    public static CinemaVO from(Cinema cinema) {
        return new CinemaVO(cinema.getId(), cinema.getName(), cinema.getAddress(), cinema.getCity());
    }
}
