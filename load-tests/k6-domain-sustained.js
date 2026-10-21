import http from 'k6/http';
import { check, sleep } from 'k6';
import { acquireToken } from './auth-helper.js';

export const options = {
    vus: 15,
    duration: '45s',
    thresholds: {
        http_req_failed: ['rate<0.02'],
        http_req_duration: ['p(95)<500', 'p(99)<1000'],
    },
};

const BASE_URL = __ENV.API_BASE_URL || 'http://localhost:8080/v1';

export function setup() {
    return { token: acquireToken('manager@farmflow.com', 'password123') };
}

export default function (data) {
    const vu = __VU;
    const iter = __ITER;
    const sensorTypes = ['SOIL_MOISTURE', 'TEMPERATURE', 'HUMIDITY', 'BATTERY_LEVEL'];
    const sensorType = sensorTypes[iter % sensorTypes.length];
    const value = 15.0 + (iter % 40) + Math.random();

    const payload = JSON.stringify({
        farmId: `farm-${vu}`,
        equipmentId: `sensor-${vu}-${iter % 200}`,
        sensorType: sensorType,
        value: value,
        unit: 'celsius',
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

    sleep(0.04);
}