import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
  vus: 20,
  duration: "30s",
};

export default function () {
  const payload = JSON.stringify({
    externalReference: `load-${__VU}-${__ITER}`,
    riskCategory: __ITER % 2 === 0 ? "LOW_RISK" : "HIGH_RISK",
    amount: 1000 + __ITER,
    payload: "load-test-payload",
    synchronousReview: false,
  });

  const res = http.post("http://localhost:8080/api/v1/decisions", payload, {
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer replace-with-valid-jwt",
      "Idempotency-Key": `load-${__VU}-${__ITER}`,
    },
  });

  check(res, {
    accepted: (r) => r.status === 202,
  });

  sleep(1);
}
