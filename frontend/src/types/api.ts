export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export interface Movie {
  id: number;
  name: string;
  poster?: string;
  score: number;
  actors?: string;
  genre: string;
  duration: number;
  description: string;
}

export interface Cinema {
  id: number;
  name: string;
  address: string;
  city: string;
}

export interface Schedule {
  id: number;
  movieId: number;
  cinemaId: number;
  hallName: string;
  showDate: string;
  showTime: string;
  totalSeats: number;
  availableSeats: number;
  price: number;
}

export interface SeatCoordinate {
  row: number;
  col: number;
}

export interface SeatState extends SeatCoordinate {
  status: 'LOCKED' | 'SOLD';
}

export interface SeatLayout {
  scheduleId: number;
  rows: number;
  cols: number;
  totalSeats: number;
  availableSeats: number;
  price: number;
  unavailableSeats: SeatCoordinate[];
  lockedSeats: SeatState[];
}

export interface Order {
  id: number;
  orderNo: string;
  requestId: string;
  movieName: string;
  cinemaName: string;
  showTime: string;
  seatCount: number;
  seatsInfo: string;
  totalPrice: number;
  status: 0 | 1 | 2;
  expireTime: string;
  payTime?: string;
  createTime: string;
}

export interface AuthPayload {
  userId: number;
  nickname: string;
  accessToken: string;
  refreshToken: string;
}

export interface SeckillStatus {
  requestId: string;
  status: 'PROCESSING' | 'CREATED' | 'FAILED';
  message: string;
  order?: Order;
}
