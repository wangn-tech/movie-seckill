import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { baseUrl } from './lib.js';

const invalidRejected = new Counter('bloom_invalid_rejected');
const businessSuccess = new Rate('read_business_success');
const readLatency = new Trend('read_latency', true);

export const options = {
  scenarios: {
    cached_reads: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 30),
      duration: __ENV.DURATION || '15s',
      exec: 'cachedReads',
    },
    invalid_ids: {
      executor: 'constant-vus',
      vus: Number(__ENV.INVALID_VUS || 10),
      duration: __ENV.DURATION || '15s',
      exec: 'invalidIds',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.55'],
    read_business_success: ['rate>0.99'],
    'read_latency{kind:hot}': ['p(95)<500'],
  },
};

export function cachedReads() {
  const response = http.get(`${baseUrl}/movies/hot`, { tags: { kind: 'hot' } });
  let body;
  try { body = response.json(); } catch (_) { body = null; }
  const ok = check(response, { 'hot movies succeeds': (r) => r.status === 200 && body?.code === 0 });
  businessSuccess.add(ok);
  readLatency.add(response.timings.duration, { kind: 'hot' });
}

export function invalidIds() {
  const response = http.get(`${baseUrl}/movies/${8_000_000 + __VU * 10_000 + __ITER}`, {
    tags: { kind: 'invalid' },
  });
  const rejected = response.status === 404;
  invalidRejected.add(rejected ? 1 : 0);
  check(response, { 'invalid id rejected': () => rejected });
}
