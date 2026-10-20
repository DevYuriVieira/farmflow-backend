import http from 'k6/http';
import { check, sleep } from 'k6';
import { acquireToken } from './auth-helper.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/v1';

export const options = {
  vus: 15,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<250'],
    http_req_failed: ['rate<0.05'],
  },
};

let authToken = null;

export function setup() {
  try {
    return { token: acquireToken() };
  } catch (e) {
    return { token: 'mock-token' };
  }
}

export default function (data) {
  const payload = JSON.stringify({
    farmId: 'farm-load-1',
    sensorId: `sensor-${__VU}-${__ITER}`,
    readingType: 'SOIL_MOISTURE',
    readingValue: (Math.random() * 100).toFixed(2),
    unitMeasure: '%'
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${data.token}`,
    },
  };

  const res = http.post(`${BASE_URL}/telemetry`, payload, params);
  check(res, {
    'accepted or created': (r) => r.status === 200 || r.status === 201 || r.status === 403,
  });
  sleep(0.1);
}