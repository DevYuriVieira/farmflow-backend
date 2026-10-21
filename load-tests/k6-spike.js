import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '5s', target: 5 },
        { duration: '10s', target: 40 },
        { duration: '5s', target: 5 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.005'],
        http_req_duration: ['p(95)<30'],
    },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/v1';

export default function () {
    const res = http.get(`${BASE_URL}/health/live`);
    check(res, {
        'status is 200': (r) => r.status === 200,
    });
    sleep(0.01);
}