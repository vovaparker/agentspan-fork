#!/usr/bin/env python3
"""Live test: verify retry_policy flows through to Conductor TaskDef.

Registers tools with different retry policies, then queries the Conductor
metadata API to confirm the TaskDef has the correct retryLogic.

Prerequisites:
    - Agentspan server running (e.g. docker compose up)
    - httpbin running at http://localhost:8081
    - AGENTSPAN_SERVER_URL=http://localhost:8080/api
"""

import json
import time
import urllib.request

from agentspan.agents import Agent, AgentRuntime, tool
from settings import settings


@tool(retry_policy="fixed", retry_count=3, retry_delay_seconds=5)
def fetch_fixed(name: str) -> dict:
    """Call httpbin with FIXED retry policy."""
    url = f"http://host.docker.internal:8081/api/hello?name={name}"
    return json.loads(urllib.request.urlopen(url).read())


@tool(retry_policy="exponential_backoff", retry_count=5, retry_delay_seconds=1)
def fetch_exponential(name: str) -> dict:
    """Call httpbin with EXPONENTIAL_BACKOFF retry policy."""
    url = f"http://host.docker.internal:8081/api/hello?name={name}"
    return json.loads(urllib.request.urlopen(url).read())


@tool(retry_policy="linear_backoff", retry_count=2, retry_delay_seconds=3)
def fetch_linear(name: str) -> dict:
    """Call httpbin with LINEAR_BACKOFF retry policy."""
    url = f"http://host.docker.internal:8081/api/hello?name={name}"
    return json.loads(urllib.request.urlopen(url).read())


agent = Agent(
    name="retry_policy_verifier",
    model=settings.llm_model,
    tools=[fetch_fixed, fetch_exponential, fetch_linear],
    instructions="You help verify retry policies.",
)


EXPECTED = {
    "fetch_fixed": {"retryLogic": "FIXED", "retryCount": 3, "retryDelaySeconds": 5},
    "fetch_exponential": {"retryLogic": "EXPONENTIAL_BACKOFF", "retryCount": 5, "retryDelaySeconds": 1},
    "fetch_linear": {"retryLogic": "LINEAR_BACKOFF", "retryCount": 2, "retryDelaySeconds": 3},
}


def check_task_defs(server_url: str) -> None:
    """Query Conductor metadata API and validate each TaskDef."""
    base = server_url.replace("/api", "")
    passed = 0
    failed = 0

    for task_name, expect in EXPECTED.items():
        url = f"{base}/api/metadata/taskdefs/{task_name}"
        try:
            resp = urllib.request.urlopen(url)
            td = json.loads(resp.read())
        except Exception as e:
            print(f"  FAIL  {task_name}: could not fetch TaskDef — {e}")
            failed += 1
            continue

        ok = True
        for key, want in expect.items():
            got = td.get(key)
            if got != want:
                print(f"  FAIL  {task_name}.{key}: expected {want!r}, got {got!r}")
                ok = False
                failed += 1

        if ok:
            print(f"  PASS  {task_name}: retryLogic={td['retryLogic']}, retryCount={td['retryCount']}, retryDelaySeconds={td['retryDelaySeconds']}")
            passed += 1

    print(f"\nResults: {passed} passed, {failed} failed")
    return failed == 0


if __name__ == "__main__":
    with AgentRuntime() as runtime:
        print("Registering tools (starts workers, registers TaskDefs)...")
        runtime._prepare_workers(agent)
        time.sleep(3)

        server_url = runtime._config.server_url
        print(f"\nChecking TaskDefs on {server_url}:\n")
        all_ok = check_task_defs(server_url)

        if all_ok:
            print("\nAll retry policies correctly propagated to Conductor!")
        else:
            print("\nSome checks failed — retry_policy not flowing through correctly.")
