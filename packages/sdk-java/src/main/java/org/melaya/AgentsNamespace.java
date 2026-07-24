package org.melaya;

/**
 * The {@code agents} namespace — groups all AI-agent plane modules into one
 * crystal-clear entry point.
 *
 * <p>Access via {@link Melaya#agents()}:
 * <pre>{@code
 * Melaya melaya = new Melaya("mk_yourkey");
 *
 * // Run a pipeline
 * JsonNode pending = melaya.agents().hitl().pending();
 * melaya.agents().hitl().approve(pending.get(0).get("requestId").asText(), null);
 *
 * // Inspect pipeline traces
 * melaya.agents().pipelines().traces("run-123");
 *
 * // Phone device control
 * melaya.agents().phone().listDevices();
 *
 * // Evaluation results
 * melaya.agents().evals().summary();
 * }</pre>
 *
 * <p>Modules in this namespace:
 * <ul>
 *   <li>{@link #pipelines()} — run overview, traces, and cron schedules</li>
 *   <li>{@link #hitl()} — HITL approval queue: list, approve, reject</li>
 *   <li>{@link #assistant()} — onboarding/persona profile (get + set)</li>
 *   <li>{@link #phone()} — Android device pairing and control</li>
 *   <li>{@link #evals()} — pipeline evaluation results and comparisons</li>
 * </ul>
 */
public final class AgentsNamespace {

    private final PipelinesAPI pipelines;
    private final HitlAPI      hitl;
    private final AssistantAPI assistant;
    private final PhoneAPI     phone;
    private final EvalsAPI     evals;

    AgentsNamespace(
            PipelinesAPI pipelines,
            HitlAPI      hitl,
            AssistantAPI assistant,
            PhoneAPI     phone,
            EvalsAPI     evals) {
        this.pipelines = pipelines;
        this.hitl      = hitl;
        this.assistant = assistant;
        this.phone     = phone;
        this.evals     = evals;
    }

    /** Pipeline run overview, traces, and cron schedules. */
    public PipelinesAPI pipelines() { return pipelines; }

    /** Human-in-the-loop approval queue: list pending, approve, reject. */
    public HitlAPI hitl() { return hitl; }

    /** Assistant onboarding/persona profile (get + set). */
    public AssistantAPI assistant() { return assistant; }

    /** Phone device control: pair, list, screen-tree, apps. */
    public PhoneAPI phone() { return phone; }

    /** Pipeline evaluation results, comparisons, and memory graphs. */
    public EvalsAPI evals() { return evals; }
}
