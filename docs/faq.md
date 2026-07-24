# Frequently asked questions

## What is Melaya?

Melaya is a platform for building and running high-trust AI agents. Its current public products are Agent Builder and flagship Mobile Device Control. Melaya Trading is planned for a later public release.

## What does Agent Builder do?

Agent Builder lets you define agents, instructions, model selection, scoped tools, execution steps, knowledge, evaluations, schedules, cost limits, and human approvals as a pipeline. Pipelines can be created, run, observed, cancelled, and updated through the visual Studio or official SDKs.

## What is Mobile Device Control?

Mobile Device Control lets an authorized agent operate a paired Android phone through the visible interface. The agent can read the screen and use assigned phone tools to navigate approved apps. Device pairing and app authorization remain under the user's control.

## Does Device Control require an API from every mobile app?

No target-app vendor API is required. The Melaya SDK itself still requires an authenticated Melaya platform key, and the phone must be paired and authorized.

## Can an SDK directly tap or type on a phone?

The public phone SDK namespace manages pairing, devices, app authorization, screen reads, and active-run association. Tap, type, navigation, and app actions are tools executed inside an authorized agent pipeline.

## Which SDKs are available?

TypeScript/JavaScript, Python, Go, Rust, Java, Kotlin, C#/.NET, Ruby, and PHP.

## How are provider credentials configured?

Store them in Melaya Connectors. Pipeline configs select a provider and model but must not contain plaintext API keys, access tokens, client secrets, or private keys. The public API rejects inline credential fields.

## Can I use local models?

Supported local providers can run through the Melaya runner. The exact providers and models are available through the live platform catalog.

## How are agents constrained?

Each agent receives an explicit tool set. Projects and API calls are tenant scoped, phone apps require user authorization, sensitive tools can require approval, and runs expose status, cost, traces, outputs, and cancellation.

## What should I test before production?

Use a non-production project and phone. Verify pairing, app authorization, pipeline creation, execution target, tool behavior, approval, cancellation, run status, and outputs. Confirm that logs and artifacts contain no credentials or personal data.

## How do I track a run?

Use **runIds(pipelineName)** to list retained run IDs and **runStatus(pipelineName, runId)** for status and structured cost information. Use **outputs(pipelineName)** for artifacts and **cancelRun(pipelineName, runId)** to cancel an active run.

## Which execution targets are valid?

The explicit values are **local-runner** and **cloud-spawn**. Omit the option to use the pipeline's configured/default target.

## Is Melaya Trading available now?

No general-availability claim is made in this repository. Melaya Trading is coming later. Trading namespaces and documents are preview material and must not be used with real funds.

## Is this repository the Melaya engine source?

No. It contains public SDK clients and documentation. The hosted platform, agent runtime, Device Control service, and trading engine are proprietary and are not included.

## What is licensed?

The public SDKs and documentation are Apache-2.0 licensed. Third-party trademarks remain the property of their owners.