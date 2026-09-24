import http from 'k6/http';
import exec from 'k6/execution';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { authHeaders, baseUrl, seatByIndex } from './lib.js';

const created = new Counter('reservation_created');
const conflict = new Counter('reservation_conflict');
const throttled = new Counter('reservation_throttled');
const accepted = new Rate('reservation_accepted');
const luaLatency = new Trend('lua_http_latency', true);

const scenario = __ENV.SCENARIO || 'unique';
const defaultVus = scenario === 'batch' ? 5 : scenario === 'rate-limit' ? 1 : 50;
const vus = Number(__ENV.VUS || defaultVus);

export const options = {
  scenarios: {
    reservation: {
      executor: 'per-vu-iterations',
      vus,
      iterations: scenario === 'rate-limit' ? Number(__ENV.ITERATIONS || 30) : 1,
      maxDuration: '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.99'],
    lua_http_latency: ['p(95)<1000'],
  },
};

function batchSeats(vu) {
  const sizes = [1, 10, 30, 60, 120];
  const size = sizes[(vu - 1) % sizes.length];
  const start = (vu - 1) * 200;
  return { size, seats: Array.from({ length: size }, (_, i) => seatByIndex(start + i)) };
}

export default function () {
  const iteration = exec.scenario.iterationInTest;
  const sharedRateUser = 990001;
  const userId = scenario === 'rate-limit' ? sharedRateUser : 900000 + __VU;
  let seats;
  let batchSize = 1;

  if (scenario === 'contention') {
    seats = [{ row: 1, col: 1 }];
  } else if (scenario === 'batch') {
    const batch = batchSeats(__VU);
    seats = batch.seats;
    batchSize = batch.size;
  } else {
    seats = [seatByIndex(iteration)];
  }

  const requestId = `k6-${scenario}-${Date.now()}-${__VU}-${__ITER}`;
  const requestOptions = authHeaders(userId);
  requestOptions.tags = { scenario, batch_size: String(batchSize) };
  const response = http.post(`${baseUrl}/seckill/seize`, JSON.stringify({
    scheduleId: 9001,
    requestId,
    seats,
  }), requestOptions);

  luaLatency.add(response.timings.duration, { scenario, batch_size: String(batchSize) });
  if (response.status === 200) created.add(1);
  if (response.status === 409) conflict.add(1);
  if (response.status === 429) throttled.add(1);

  const expected = scenario === 'contention'
    ? response.status === 200 || response.status === 409
    : scenario === 'rate-limit'
      ? response.status === 200 || response.status === 429
      : response.status === 200;
  accepted.add(expected);
  check(response, { 'response matches scenario contract': () => expected });
  if (scenario === 'rate-limit') sleep(0.01);
}
