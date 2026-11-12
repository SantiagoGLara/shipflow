import json, secrets, time
from pathlib import Path

user = {
    "name": "ShipFlow Load Test",
    "email": f"loadtest-{int(time.time())}@example.invalid",
    "password": secrets.token_urlsafe(24),
}
p = Path("/tmp/shipflow-prod-register.json")
p.write_text(json.dumps(user))
p.chmod(0o600)