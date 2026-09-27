package org.melaya

import org.json.JSONObject

/**
 * Memory API — edit and delete persisted cross-run agent crew memory entries.
 *
 * Crew memory persists across runs of the same pipeline/crew (see [EvalsAPI.crewMemory]
 * for reads). This class covers the two mutating endpoints, editor/owner-gated and
 * tenant-scoped.
 *
 * Paths:
 *   - `POST /api/v1/private/memory/crew/edit`   — edit one entry
 *   - `POST /api/v1/private/memory/crew/delete` — delete one entry
 *
 * @example
 * ```kotlin
 * melaya.memory.editEntry(
 *     pipeline = "daily-digest",
 *     entryId  = "mem_123",
 *     patch    = mapOf("topic" to "Preferred tone", "content" to "Formal, concise."),
 * )
 * melaya.memory.deleteEntry(pipeline = "daily-digest", entryId = "mem_123")
 * ```
 */
class MemoryAPI internal constructor(private val http: HttpClient) {

    /**
     * Edit one persisted crew-memory entry (editor/owner-gated, tenant-scoped).
     *
     * @param pipeline The pipeline/crew name that owns the memory entry.
     * @param entryId  The memory entry id to edit.
     * @param patch    Fields to update: `topic`, `content`, and/or `tags`.
     * @param project  Optional project to disambiguate the pipeline name.
     */
    fun editEntry(
        pipeline: String,
        entryId: String,
        patch: Map<String, Any?>,
        project: String? = null,
    ): JSONObject {
        val body = buildMap<String, Any?> {
            put("pipeline", pipeline)
            put("entryId", entryId)
            if (project != null) put("project", project)
            put("patch", patch)
        }
        return http.post("/api/v1/private/memory/crew/edit", body).asObject()
    }

    /**
     * Delete one persisted crew-memory entry (editor/owner-gated, tenant-scoped).
     *
     * @param pipeline The pipeline/crew name that owns the memory entry.
     * @param entryId  The memory entry id to delete.
     * @param project  Optional project to disambiguate the pipeline name.
     */
    fun deleteEntry(pipeline: String, entryId: String, project: String? = null): JSONObject {
        val body = buildMap<String, Any?> {
            put("pipeline", pipeline)
            put("entryId", entryId)
            if (project != null) put("project", project)
        }
        return http.post("/api/v1/private/memory/crew/delete", body).asObject()
    }
}
