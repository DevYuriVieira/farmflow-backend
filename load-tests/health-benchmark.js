import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/v1';

export const options = {
  scenarios: {
    baseline_load: {
      executor: 'constant-vus',
      vus: 10,
      duration: '15s',
    },
    spike_test: {
      executor: 'ramping-vus',
      startVUs: 5,
      stages: [
        { duration: '10s', target: 50 },
        { duration: '15s', target: 50 },
        { duration: '5s', target: 5 },
      ],
      startTime: '15s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<50'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/health/live`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'status is UP': (r) => r.json().status === 'UP',
  });
  sleep(0.05);
}