-- =====================================================
-- 电影票秒杀系统 - 数据库初始化脚本
-- MySQL 8.0, 库名 movie_seckill
-- =====================================================

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE DATABASE IF NOT EXISTS movie_seckill
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE movie_seckill;

-- ---------- 用户表 ----------
CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  account     VARCHAR(64)  NOT NULL UNIQUE COMMENT '账号/手机号',
  password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密',
  nickname    VARCHAR(64),
  balance     INT NOT NULL DEFAULT 100000 COMMENT '模拟余额，单位分，默认1000元',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ---------- 电影表 ----------
CREATE TABLE IF NOT EXISTS movie (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(200) NOT NULL,
  poster      VARCHAR(500),
  score       DECIMAL(3,1) DEFAULT 0,
  actors      VARCHAR(500),
  genre       VARCHAR(100),
  duration    INT COMMENT '时长(分钟)',
  description TEXT,
  status      TINYINT DEFAULT 1 COMMENT '1热映 2即将上映',
  version     INT DEFAULT 0,
  deleted     TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电影表';

-- ---------- 影院表 ----------
CREATE TABLE IF NOT EXISTS cinema (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(200) NOT NULL,
  address     VARCHAR(500),
  city        VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='影院表';

-- ---------- 场次表（防超卖核心） ----------
CREATE TABLE IF NOT EXISTS movie_schedule (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  movie_id        BIGINT NOT NULL,
  cinema_id       BIGINT NOT NULL,
  hall_name       VARCHAR(50),
  show_date       DATE NOT NULL,
  show_time       VARCHAR(10) NOT NULL,
  total_seats     INT NOT NULL DEFAULT 120,
  available_seats INT NOT NULL DEFAULT 120,
  seat_rows       INT NOT NULL DEFAULT 10 COMMENT '座位图行边界',
  seat_cols       INT NOT NULL DEFAULT 14 COMMENT '座位图列边界',
  unavailable_seats JSON NULL COMMENT '过道/空位/不可售座位坐标',
  price           DECIMAL(10,2) NOT NULL DEFAULT 39.90,
  status          TINYINT DEFAULT 1 COMMENT '1可售 0停售',
  version         INT DEFAULT 0,
  create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_movie_date (movie_id, show_date),
  KEY idx_cinema_date (cinema_id, show_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场次表';

-- ---------- 座位锁定表（防座位重售核心） ----------
CREATE TABLE IF NOT EXISTS seat_lock (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_id BIGINT NOT NULL,
  row_num     INT NOT NULL,
  col_num     INT NOT NULL,
  user_id     BIGINT NOT NULL,
  lock_token  VARCHAR(64) NOT NULL COMMENT '= requestId 幂等键',
  order_no    VARCHAR(64),
  lock_until  DATETIME NOT NULL,
  status      TINYINT DEFAULT 1 COMMENT '1锁定中 0已释放 2已售',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_seat (schedule_id, row_num, col_num),
  KEY idx_schedule_status (schedule_id, status),
  KEY idx_lock_until (lock_until, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='座位锁定表';

-- ---------- 订单表 ----------
CREATE TABLE IF NOT EXISTS ticket_order (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_no    VARCHAR(64) NOT NULL UNIQUE,
  user_id     BIGINT NOT NULL,
  schedule_id BIGINT NOT NULL,
  lock_token  VARCHAR(64) NOT NULL COMMENT '幂等键=requestId',
  movie_name  VARCHAR(200),
  cinema_name VARCHAR(200),
  show_time   VARCHAR(30),
  seat_count  INT NOT NULL DEFAULT 1,
  seats_info  TEXT COMMENT '展示用座位摘要；权威明细存储于 order_seat',
  total_price DECIMAL(10,2),
  status      TINYINT DEFAULT 0 COMMENT '0待支付 1已支付 2已取消',
  expire_time DATETIME NOT NULL,
  pay_time    DATETIME NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_lock_token (lock_token),
  KEY idx_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ---------- 订单座位明细 ----------
CREATE TABLE IF NOT EXISTS order_seat (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  order_id    BIGINT NOT NULL,
  order_no    VARCHAR(64) NOT NULL,
  schedule_id BIGINT NOT NULL,
  row_num     INT NOT NULL,
  col_num     INT NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_schedule_seat (schedule_id, row_num, col_num)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单座位明细';

-- ---------- Outbox 本地消息表 ----------
CREATE TABLE IF NOT EXISTS outbox_event (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_key    VARCHAR(64) NOT NULL COMMENT '业务幂等键',
  user_id      BIGINT NULL,
  schedule_id  BIGINT NULL,
  event_type   VARCHAR(64) NOT NULL COMMENT 'ORDER_CREATED/ORDER_PAID/ORDER_CANCELLED',
  topic        VARCHAR(128) NOT NULL,
  payload      TEXT NOT NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/DEAD',
  retry_count  INT DEFAULT 0,
  max_retry    INT DEFAULT 10,
  process_status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/SUCCEEDED/FAILED',
  fail_reason  VARCHAR(500) NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  sent_time    DATETIME NULL,
  UNIQUE KEY uk_event_key (event_key),
  KEY idx_status_create (status, create_time),
  KEY idx_process_status (process_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Outbox 本地消息表';

-- =====================================================
-- 初始测试数据
-- =====================================================

-- 两个演示用户，密码都是 123456 (BCrypt)
INSERT INTO sys_user (account, password, nickname, balance) VALUES
('13800000001', '$2a$10$BSdP2zKUyr6vRXAXfl5phuS9XPMogHNP.Quo1oI4g0Nu7.UEKujR.', '用户一', 100000),
('13800000002', '$2a$10$BSdP2zKUyr6vRXAXfl5phuS9XPMogHNP.Quo1oI4g0Nu7.UEKujR.', '用户二', 100000);

-- 电影
INSERT INTO movie (name, poster, score, actors, genre, duration, description, status) VALUES
('流浪地球3', '/posters/earth.svg', 9.5, '吴京/刘德华', '科幻/冒险', 173, '太阳即将毁灭，人类带着地球逃离太阳系。', 1),
('唐探2026', '/posters/detective.svg', 7.2, '王宝强/刘昊然', '喜剧/悬疑', 136, '唐仁秦风再探曼谷奇案。', 1),
('沙丘3', '/posters/dune.svg', 8.9, '甜茶/赞达亚', '科幻/剧情', 155, '弗雷曼人的最终战争。', 1);

-- 影院
INSERT INTO cinema (name, address, city) VALUES
('万达影城(福田店)', '深圳市福田区福华三路 Coco Park 3F', '深圳'),
('CGV影城(南山店)', '深圳市南山区海岸城 5F', '深圳');

-- 场次：每个影院对每部电影排一场，库存 120
-- 10x14 是布局边界；第 5/10 列为过道，因此有效座位为 120，而不是 140。
SET @aisles = JSON_ARRAY(
  JSON_OBJECT('row',1,'col',5), JSON_OBJECT('row',1,'col',10),
  JSON_OBJECT('row',2,'col',5), JSON_OBJECT('row',2,'col',10),
  JSON_OBJECT('row',3,'col',5), JSON_OBJECT('row',3,'col',10),
  JSON_OBJECT('row',4,'col',5), JSON_OBJECT('row',4,'col',10),
  JSON_OBJECT('row',5,'col',5), JSON_OBJECT('row',5,'col',10),
  JSON_OBJECT('row',6,'col',5), JSON_OBJECT('row',6,'col',10),
  JSON_OBJECT('row',7,'col',5), JSON_OBJECT('row',7,'col',10),
  JSON_OBJECT('row',8,'col',5), JSON_OBJECT('row',8,'col',10),
  JSON_OBJECT('row',9,'col',5), JSON_OBJECT('row',9,'col',10),
  JSON_OBJECT('row',10,'col',5), JSON_OBJECT('row',10,'col',10)
);

INSERT INTO movie_schedule (movie_id, cinema_id, hall_name, show_date, show_time,
  total_seats, available_seats, seat_rows, seat_cols, unavailable_seats, price) VALUES
(1, 1, 'IMAX 厅', CURDATE(), '19:30', 120, 120, 10, 14, @aisles, 39.90),
(1, 2, '杜比厅', CURDATE(), '20:15', 120, 120, 10, 14, @aisles, 45.00),
(2, 1, '普通厅', CURDATE(), '21:00', 120, 120, 10, 14, @aisles, 35.00),
(3, 2, 'IMAX 厅', CURDATE(), '22:00', 120, 120, 10, 14, @aisles, 49.90);

-- 压测专用隐藏电影与大场次，不出现在“正在热映”列表。
-- 100x101 是座位图边界，第 51 列为过道，实际可售座位为 10,000。
INSERT INTO movie (id, name, poster, score, actors, genre, duration, description, status) VALUES
(9001, '核心链路压测专用', '/posters/cinema.svg', 0, '-', '测试', 1, '仅供 k6 使用', 2);

WITH RECURSIVE perf_rows AS (
  SELECT 1 AS row_num
  UNION ALL
  SELECT row_num + 1 FROM perf_rows WHERE row_num < 100
)
SELECT JSON_ARRAYAGG(JSON_OBJECT('row', row_num, 'col', 51)) INTO @perf_aisle FROM perf_rows;

INSERT INTO movie_schedule (id, movie_id, cinema_id, hall_name, show_date, show_time,
  total_seats, available_seats, seat_rows, seat_cols, unavailable_seats, price) VALUES
(9001, 9001, 1, '压测厅', CURDATE(), '23:59', 10000, 10000, 100, 101, @perf_aisle, 1.00);
