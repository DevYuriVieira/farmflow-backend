import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/v1';

export function acquireToken(email, password) {
  const payload = JSON.stringify({
    email: email || 'manager@farmflow.com',
    password: password || 'password123'
  });

  const params = {
    headers: { 'Content-Type': 'application/json' },
  };

  const res = http.post(`${BASE_URL}/auth/login`, payload, params);
  check(res, {
    'login successful (200)': (r) => r.status === 200,
  });

  const json = res.json();
  return json.token || json.accessToken;
}