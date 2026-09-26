# Local models: Ollama and LM Studio

A local model is a model file that runs on the user's own computer. Nothing is sent to a model company, nothing is billed, and it works offline for the model part. The cost is speed and quality: a laptop model is much slower and less capable than a frontier cloud model. Any agent on `ollama` or `lmstudio` sends the whole pipeline to the runner.

## Is the computer good enough?

| Computer | Realistic choice |
|---|---|
| Graphics card with 8-12 GB memory (or Apple Silicon with 16 GB+) | 7-8B models: `qwen3:8b`, `qwen2.5:7b` |
| 12 GB+ graphics memory (or Apple Silicon 32 GB+) | Up to `qwen3:14b` |
| No graphics card, 16 GB RAM | Works, but expect minutes per agent turn; use for private one-off tasks, not for validation |
| Less | Use a cloud model |

Size rule of thumb for agents that must use tools:

| Model size | Behaviour in pipelines |
|---|---|
| 7B and above | Reliable tool use for normal pipelines |
| 4B to 7B | Simple single-tool steps; struggles with multi-step work |
| Below 4B | Text only in practice; loops or never calls tools |

Recommended picks (tool calling):

| Model | Graphics memory | Speed on a decent card | Tool calling |
|---|---|---|---|
| `qwen3:8b` | about 6.5 GB | 35-45 tokens/s | Excellent; Melaya templates default to it |
| `qwen2.5:7b` | about 5.5 GB | 40-50 tokens/s | Good |
| `qwen3:14b` | about 10.5 GB | 15-20 tokens/s | Excellent; only with 12 GB+ and nothing else using the card |
| `glm4:9b` | about 7 GB | 30-40 tokens/s | Decent; weaker at strict formats |

Laptops slow down when hot; long pipelines on a laptop may run at half these speeds.

## Ollama, step by step

1. Install from ollama.com. It runs in the background (on Windows it appears in the tray).
2. Open a terminal and pull a model: `ollama pull qwen3:8b`. Wait for the download to finish.
3. Check: `ollama list` shows it.
4. Start or restart the runner. `melaya_runner_status {}` lists `{provider: "ollama", name: "qwen3:8b"}`.
5. In the config: `"model_provider": "ollama", "model_name": "qwen3:8b"` exactly as listed.

If a run names a model that is not pulled, it stops immediately with "not pulled in Ollama. Run: ollama pull <name>". Pull it and run again (restart the runner so the list updates).

**Context size.** Ollama reads only a small amount of text unless told otherwise. Melaya asks Ollama for the model's own window, capped at about 16k tokens, so agents keep their instructions and tool results. If the computer runs out of graphics memory, Melaya halves the window automatically and remembers the smaller size. Consequence: a local agent cannot read a 100-page document at once. Keep inputs short, use file-based tools that return summaries, or use a cloud model for that step.

## LM Studio, step by step

1. Install from lmstudio.ai.
2. In the app, search for a model (for example Qwen3 8B, an "Instruct" build) and download it.
3. Start the local server (Developer tab, start server; it listens on the standard local port).
4. Load the model with a context length of at least 16k (the loading dialog has a context length setting). Larger costs more memory.
5. Start or restart the runner. `melaya_runner_status {}` lists `{provider: "lmstudio", name: "<id>"}`.
6. Config: `"model_provider": "lmstudio", "model_name": "<id exactly as listed>"`.

If the model is downloaded but not loaded when the run starts, the runner loads it first and shows progress; this can take 1-2 minutes, so a slow start is normal. If the model is not downloaded at all, the run stops at once asking the user to download it in LM Studio.

**Context size** is whatever the model was loaded with. "Context too small" symptoms (the agent forgets its task, cut-off tool results, errors about context length): reload the model with a larger context length, or use a cloud model for that step.

## A company model server instead of the runner

If the company runs Ollama or LM Studio on a server reachable from the internet, the user can save that server's address on the Connectors page (Ollama / LM Studio entry, optional token). Then pipelines with those providers run in Melaya's cloud and call that server; the runner is not needed. For other self-hosted OpenAI-compatible servers (vLLM, TGI, LocalAI, a LiteLLM proxy), use provider `openai_compatible` or `litellm` with the server's base URL instead. Private network addresses are refused for cloud runs; those servers need the runner.

## Local embedders (retrieval)

For retrieval over documents, `rag_embedder_provider` can be `ollama` or `lmstudio` with an embedding model pulled or loaded the same way (for example an embedding model from the Ollama library). A local embedder sends the run to the runner. A local document folder for retrieval (`rag_local_folder_path`) requires a local embedder.

## Speed expectations to give the user

- A cloud model finishes a typical 3-step pipeline in a few minutes; the same pipeline on an 8B local model with a graphics card can take 3-5 times longer, and much longer without one.
- The first run after starting the computer is slower (model loading).
- Parallel steps on one local model do not run truly in parallel; they queue on the same card.
