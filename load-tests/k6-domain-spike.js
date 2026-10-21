import http from 'k6/http';
import { check, sleep } from 'k6';
import { acquireToken } from './auth-helper.js';

export const options = {
    stages: [
        { duration: '5s', target: 5 },
        { duration: '10s', target: 35 },
        { duration: '5s', target: 5 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<800'],
    },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/v1';

export function setup() {
    return { token: acquireToken('manager@farmflow.com', 'password123') };
}

export default function (data) {
    const vu = __VU;
    const iter = __ITER;

    const payload = JSON.stringify({
        farmId: `farm-${vu}`,
        equipmentId: `harvester-${vu}`,
        sensorType: 'SPEED_KMH',
        value: 12.5 + Math.random(),
        unit: 'km/h',
        timestamp: new Date().toISOString()
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${data.token}`
        }
    };

    const res = http.post(`${BASE_URL}/telemetry`, payload, params);

    check(res, {
        'status is 201 Created': (r) => r.status === 201,
    });

    sleep(0.02);
}