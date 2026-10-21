import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 5,
    duration: '30s',
    thresholds: {
        http_req_failed: ['rate<0.001'],
        http_req_duration: ['p(95)<10', 'p(99)<25'],
    },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/v1';

export default function () {
    const res = http.get(`${BASE_URL}/health/live`);
    check(res, {
        'status is 200': (r) => r.status === 200,
        'status is UP': (r) => r.json('status') === 'UP',
    });
    sleep(0.01);
}