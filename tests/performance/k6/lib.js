import crypto from 'k6/crypto';
import encoding from 'k6/encoding';

export const baseUrl = __ENV.BASE_URL || 'http://maoyan-nginx/api';
const jwtSecret = __ENV.JWT_SECRET || 'movie-seckill-prod-secret-change-me-32bytes-minimum';

function base64UrlJson(value) {
  return encoding.b64encode(JSON.stringify(value), 'rawurl');
}

export function accessToken(userId) {
  const now = Math.floor(Date.now() / 1000);
  const unsigned = `${base64UrlJson({ alg: 'HS256' })}.${base64UrlJson({ sub: String(userId), type: 'access', iat: now, exp: now + 3600 })}`;
  const signature = crypto.hmac('sha256', jwtSecret, unsigned, 'base64rawurl');
  return `${unsigned}.${signature}`;
}

export function authHeaders(userId) {
  return {
    headers: {
      Authorization: `Bearer ${accessToken(userId)}`,
      'Content-Type': 'application/json',
    },
  };
}

export function seatByIndex(index) {
  const row = Math.floor(index / 100) + 1;
  const logicalCol = (index % 100) + 1;
  return { row, col: logicalCol >= 51 ? logicalCol + 1 : logicalCol };
}
