
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 25 },
    { duration: '2m', target: 100 },
    { duration: '2m', target: 300 },
    { duration: '1m', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const response = http.get(
    'http://localhost:8080/actuator/health'
  );

  check(response, {
    'HTTP 200': (r) => r.status === 200,
  });

  sleep(1);
}
