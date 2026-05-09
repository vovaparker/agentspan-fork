// Copyright (c) 2025 Agentspan
// Licensed under the MIT License.

// Credentials — External worker credential resolution.
//
// Demonstrates:
//   - [Tool(External = true, Credentials = ["GITHUB_TOKEN"])] declares
//     credentials that the Agentspan server resolves for an external worker
//   - The external worker calls AgentHttpClient.ResolveCredentialsAsync()
//     to fetch the plaintext credential value at runtime
//   - Works for workers running in separate processes, containers, or machines
//
// Two sides are shown:
//   1. Agent definition (declares the external tool with credentials)
//   2. External worker pattern (shown in comments; runs in a separate process)
//
// Setup (one-time):
//   agentspan credentials set GITHUB_TOKEN <your-github-token>
//
// Requirements:
//   - Agentspan server running at AGENTSPAN_SERVER_URL
//   - AGENTSPAN_LLM_MODEL set in environment
//   - GITHUB_TOKEN stored via `agentspan credentials set`
//   - An external worker polling for "github_lookup" tasks (see comments below)

using Agentspan;
using Agentspan.Examples;

// ── Agent side: declare external tool with credentials ──────────

var agent = new Agent("external_cred_agent_16h")
{
    Model        = Settings.LlmModel,
    Tools        = ToolRegistry.FromInstance(new ExternalGithubTools()),
    Instructions =
        "You can look up GitHub users. Use the github_lookup tool. " +
        "GITHUB_TOKEN is automatically resolved by the external worker.",
};

// ── Run ───────────────────────────────────────────────────────

Console.WriteLine("=== External Worker Credentials ===");
Console.WriteLine("The agent declares the external tool; a separate worker handles execution.");
Console.WriteLine("The worker resolves GITHUB_TOKEN from the server at runtime.\n");
Console.WriteLine("Note: This example requires an external worker to be running.");
Console.WriteLine("See the comment block below for the worker implementation pattern.\n");

await using var runtime = new AgentRuntime();
var result = await runtime.RunAsync(agent, "Look up the GitHub profile for torvalds.");
result.PrintResult();

/*
 * ── External worker side (runs in a separate process) ─────────────────
 *
 * The external worker polls Conductor for tasks named "github_lookup".
 * It uses AgentHttpClient.ResolveCredentialsAsync() to fetch the
 * GITHUB_TOKEN value from the Agentspan server at runtime.
 *
 * Implementation sketch:
 *
 *   using Agentspan;
 *   using OrchestratorSDK.Client;           // Conductor .NET SDK
 *   using OrchestratorSDK.Client.Worker;
 *   using System.Net.Http.Headers;
 *   using System.Text.Json;
 *
 *   var serverUrl = Environment.GetEnvironmentVariable("AGENTSPAN_SERVER_URL")!;
 *   var http = new AgentHttpClient(serverUrl);
 *
 *   // Poll Conductor for tasks named "github_lookup"
 *   var taskClient = new TaskResourceApi(new Configuration { BasePath = serverUrl });
 *
 *   while (true)
 *   {
 *       var task = await taskClient.PollAsync("github_lookup", workerId: "worker-1");
 *       if (task is null) { await Task.Delay(1000); continue; }
 *
 *       // Extract the execution token injected by Agentspan into __agentspan_ctx__
 *       string? executionToken = null;
 *       if (task.InputData.TryGetValue("__agentspan_ctx__", out var ctxRaw))
 *       {
 *           var ctx = JsonSerializer.Deserialize<Dictionary<string, JsonElement>>(
 *               ctxRaw.ToString()!);
 *           if (ctx?.TryGetValue("execution_token", out var tok) == true)
 *               executionToken = tok.GetString();
 *       }
 *
 *       // Resolve GITHUB_TOKEN from the server using the execution token
 *       var creds = await http.ResolveCredentialsAsync(executionToken, ["GITHUB_TOKEN"]);
 *       var token = creds.GetValueOrDefault("GITHUB_TOKEN", "");
 *
 *       // Use the credential to call the GitHub API
 *       var username = task.InputData["username"].ToString();
 *       using var ghClient = new HttpClient();
 *       ghClient.DefaultRequestHeaders.Authorization =
 *           new AuthenticationHeaderValue("Bearer", token);
 *       ghClient.DefaultRequestHeaders.Add("User-Agent", "agentspan-worker");
 *
 *       var resp = await ghClient.GetAsync($"https://api.github.com/users/{username}");
 *       // ... complete the Conductor task with the response data
 *   }
 */

// ── Tool stub (agent side) ────────────────────────────────────────────

internal sealed class ExternalGithubTools
{
    // External = true  → the method body is never called locally.
    // Credentials = ["GITHUB_TOKEN"] → the server resolves this credential
    //   and makes it available to the external worker via __agentspan_ctx__.
    [Tool("Look up a GitHub user's public profile. Runs on an external worker.",
          External = true, Credentials = ["GITHUB_TOKEN"])]
    public Dictionary<string, object> GithubLookup(string username) => default!;
}
