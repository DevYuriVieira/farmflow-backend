import http from 'k6/http';
import { check, sleep } from 'k6';
import { acquireToken } from './auth-helper.js';

export const options = {
    vus: 10,
    duration: '10s',
    thresholds: {
        http_req_duration: ['p(95)<300'],
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
        equipmentId: `burst-sensor-${vu}`,
        sensorType: 'VOLTAGE',
        value: 3.3,
        unit: 'V',
        timestamp: new Date().toISOString()
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${data.token}`
        }
    };

    const res = http.post(`${BASE_URL}/telemetry`, payload, params);

    if (res.status === 429) {
        check(res, {
            'status is 429': (r) => r.status === 429,
            'content-type is application/problem+json': (r) => r.headers['Content-Type'] && r.headers['Content-Type'].includes('application/problem+json'),
            'has Retry-After header': (r) => r.headers['Retry-After'] !== undefined,
            'has RFC 9457 title': (r) => r.body && r.body.includes('Too Many Requests'),
        });
    } else {
        check(res, {
            'status is 201 Created': (r) => r.status === 201,
        });
    }

    sleep(0.01);
}