# Skill: Memory System Design

## Overview

The Neuron memory system is the L4 MEMORY layer. It enables the AI to learn from past interactions,
recall user preferences, reuse successful workflows, and retrieve similar task traces via vector search.
Three distinct memory types serve different purposes with different storage backends and lifetimes.

---

## Memory Layer Summary

| Layer | Type | Storage | Lifetime | Access Speed | Use Case |
|-------|------|---------|----------|--------------|----------|
| Working | In-memory + SharedPreferences | RAM | Per-task (cleared on completion) | <1ms | Current goal, step index, action history, screen state hash |
| Long-term | Structured | Room DB (SQLite) | Persistent across sessions | <10ms | User preferences, app workflows, contacts, settings |
| Episodic | Vector | sqlite-vec | Persistent across sessions | <50ms | Full task traces with embeddings for RAG retrieval |

### When to Use Each Layer

```
Working Memory:
  - "What step am I on?"
  - "What did I just do?"
  - "Has the screen changed since my last action?"

Long-term Memory:
  - "Which music app does this user prefer?"
  - "What's the fastest way to send a WhatsApp message?"
  - "How many times has this workflow succeeded?"

Episodic Memory:
  - "Have I done something similar to this task before?"
  - "What went wrong last time I tried to book an Uber?"
  - "Show me traces of successful multi-app tasks"
```

---

## Room DB Schema

### Database Definition

```kotlin
package ai.neuron.memory

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserPreference::class,
        AppWorkflow::class,
        TaskTrace::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NeuronDatabase : RoomDatabase() {
    abstract fun userPreferenceDao(): UserPreferenceDao
    abstract fun appWorkflowDao(): AppWorkflowDao
    abstract fun taskTraceDao(): TaskTraceDao
}
```

### Hilt Module for Database Injection

```kotlin
package ai.neuron.memory

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NeuronDatabase {
        return Room.databaseBuilder(
            context,
            NeuronDatabase::class.java,
            "neuron_memory.db",
        )
            .fallbackToDestructiveMigration()  // OK for MVP; use proper migrations later
            .build()
    }

    @Provides fun provideUserPreferenceDao(db: NeuronDatabase) = db.userPreferenceDao()
    @Provides fun provideAppWorkflowDao(db: NeuronDatabase) = db.appWorkflowDao()
    @Provides fun provideTaskTraceDao(db: NeuronDatabase) = db.taskTraceDao()
}
```

### UserPreference Entity

Stores learned user preferences with confidence scores that decay over time.

```kotlin
package ai.neuron.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_preferences",
    indices = [
        Index(value = ["category", "key"], unique = true),
    ],
)
data class UserPreference(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Category groups preferences: "app", "contact", "style", "shortcut", "setting" */
    @ColumnInfo(name = "category")
    val category: String,

    /** The preference key, e.g. "preferred_music_app", "default_browser" */
    @ColumnInfo(name = "key")
    val key: String,

    /** The preference value, e.g. "com.spotify.music", "chrome" */
    @ColumnInfo(name = "value")
    val value: String,

    /** Confidence score 0.0-1.0. Increases with repeated use, decays over time. */
    @ColumnInfo(name = "confidence")
    val confidence: Float = 0.5f,

    /** Last time this preference was confirmed or used. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
```

### AppWorkflow Entity

Stores successful action sequences per app and task type. Enables Neuron to replay
known-good workflows instead of re-planning from scratch.

```kotlin
package ai.neuron.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_workflows",
    indices = [
        Index(value = ["package_name", "task_type"]),
    ],
)
data class AppWorkflow(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Target app package name, e.g. "com.whatsapp" */
    @ColumnInfo(name = "package_name")
    val packageName: String,

    /** Task classification, e.g. "send_message", "search", "navigate", "settings_toggle" */
    @ColumnInfo(name = "task_type")
    val taskType: String,

    /**
     * JSON array of action steps. Example:
     * [{"action":"tap","target":"search_bar"},{"action":"type","text":"hello"},{"action":"tap","target":"send_btn"}]
     */
    @ColumnInfo(name = "action_sequence_json")
    val actionSequenceJson: String,

    /** Number of times this workflow completed successfully. Higher = more reliable. */
    @ColumnInfo(name = "success_count")
    val successCount: Int = 0,

    /** Number of times this workflow failed. Used to calculate reliability score. */
    @ColumnInfo(name = "failure_count")
    val failureCount: Int = 0,

    /** Last successful execution timestamp. */
    @ColumnInfo(name = "last_used")
    val lastUsed: Long = System.currentTimeMillis(),
) {
    /** Reliability score: success_count / (success_count + failure_count). */
    val reliability: Float
        get() {
            val total = successCount + failureCount
            return if (total == 0) 0f else successCount.toFloat() / total
        }
}
```

### TaskTrace Entity

Stores complete task execution traces with optional embeddings for vector similarity search.

```kotlin
package ai.neuron.memory

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_traces")
data class TaskTrace(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The user's original goal text, e.g. "send hi to Mom on WhatsApp" */
    @ColumnInfo(name = "goal")
    val goal: String,

    /**
     * JSON array of executed steps with results. Example:
     * [{"step":1,"action":"launch","pkg":"com.whatsapp","result":"ok"},
     *  {"step":2,"action":"tap","target":"Mom","result":"ok"},
     *  {"step":3,"action":"type","text":"hi","result":"ok"},
     *  {"step":4,"action":"tap","target":"send","result":"ok"}]
     */
    @ColumnInfo(name = "steps_json")
    val stepsJson: String,

    /** Outcome: "success", "failure", "partial", "cancelled" */
    @ColumnInfo(name = "outcome")
    val outcome: String,

    /** Total task duration from start to completion in milliseconds. */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    /**
     * Embedding vector as ByteArray (serialized float array).
     * Generated by EmbeddingGemma on-device. Used for sqlite-vec similarity search.
     * Nullable because embedding generation is async and may fail.
     */
    @ColumnInfo(name = "embedding")
    val embedding: ByteArray? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TaskTrace) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
```

---

## DAO Interfaces

```kotlin
package ai.neuron.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserPreferenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(preference: UserPreference)

    @Query("SELECT * FROM user_preferences WHERE category = :category ORDER BY confidence DESC")
    suspend fun getByCategory(category: String): List<UserPreference>

    @Query("SELECT * FROM user_preferences WHERE key = :key ORDER BY confidence DESC LIMIT 1")
    suspend fun getByKey(key: String): UserPreference?

    @Query("""
        SELECT * FROM user_preferences
        WHERE category IN (:categories)
        AND confidence >= :minConfidence
        ORDER BY confidence DESC
    """)
    suspend fun getRelevant(categories: List<String>, minConfidence: Float = 0.3f): List<UserPreference>

    @Query("DELETE FROM user_preferences WHERE updated_at < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("""
        UPDATE user_preferences
        SET confidence = MAX(0.0, confidence - :decayAmount),
            updated_at = :now
        WHERE updated_at < :olderThan
    """)
    suspend fun decayConfidence(
        decayAmount: Float = 0.05f,
        olderThan: Long = System.currentTimeMillis() - 7 * 86_400_000L,  // 7 days
        now: Long = System.currentTimeMillis(),
    )
}

@Dao
interface AppWorkflowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(workflow: AppWorkflow)

    @Query("""
        SELECT * FROM app_workflows
        WHERE package_name = :packageName AND task_type = :taskType
        ORDER BY success_count DESC
        LIMIT 1
    """)
    suspend fun getBestWorkflow(packageName: String, taskType: String): AppWorkflow?

    @Query("""
        SELECT * FROM app_workflows
        WHERE package_name = :packageName
        ORDER BY last_used DESC
        LIMIT :limit
    """)
    suspend fun getByPackage(packageName: String, limit: Int = 10): List<AppWorkflow>

    @Query("""
        SELECT * FROM app_workflows
        ORDER BY success_count DESC
        LIMIT :limit
    """)
    suspend fun getMostUsed(limit: Int = 20): List<AppWorkflow>

    @Query("""
        UPDATE app_workflows
        SET success_count = success_count + 1, last_used = :now
        WHERE id = :id
    """)
    suspend fun incrementSuccess(id: Long, now: Long = System.currentTimeMillis())

    @Query("""
        UPDATE app_workflows
        SET failure_count = failure_count + 1
        WHERE id = :id
    """)
    suspend fun incrementFailure(id: Long)
}

@Dao
interface TaskTraceDao {

    @Insert
    suspend fun insert(trace: TaskTrace): Long

    @Query("SELECT * FROM task_traces ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<TaskTrace>

    @Query("SELECT * FROM task_traces WHERE goal LIKE '%' || :query || '%' ORDER BY created_at DESC LIMIT :limit")
    suspend fun searchByGoal(query: String, limit: Int = 5): List<TaskTrace>

    @Query("SELECT * FROM task_traces WHERE outcome = :outcome ORDER BY created_at DESC LIMIT :limit")
    suspend fun getByOutcome(outcome: String, limit: Int = 10): List<TaskTrace>

    @Query("SELECT COUNT(*) FROM task_traces")
    suspend fun count(): Int

    @Query("DELETE FROM task_traces WHERE created_at < :before")
    suspend fun deleteOlderThan(before: Long)
}
```

---

## Working Memory Implementation

Working memory holds the current task state in RAM with SharedPreferences backup
for process death recovery.

```kotlin
package ai.neuron.memory

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class TaskState(
    val goal: String,
    val stepIndex: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val screenStateHash: String? = null,
    val currentPackage: String? = null,
)

@Serializable
data class ActionRecord(
    val actionType: String,     // "tap", "type", "swipe", "scroll", "launch", "back", "home"
    val target: String,         // resource ID, coordinates, or package name
    val timestamp: Long,
    val success: Boolean,
    val screenshotHash: String? = null,
)

@Singleton
class WorkingMemory @Inject constructor(
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val KEY_TASK_STATE = "wm_task_state"
        private const val KEY_ACTION_HISTORY = "wm_action_history"
        private const val MAX_HISTORY_SIZE = 10
    }

    private var currentTask: TaskState? = null
    private val actionHistory = mutableListOf<ActionRecord>()

    // --- Task lifecycle ---

    fun startTask(goal: String) {
        currentTask = TaskState(goal = goal)
        actionHistory.clear()
        persist()
    }

    fun isActive(): Boolean = currentTask != null

    fun getCurrentGoal(): String? = currentTask?.goal

    fun recordAction(actionType: String, target: String, success: Boolean) {
        val record = ActionRecord(
            actionType = actionType,
            target = target,
            timestamp = System.currentTimeMillis(),
            success = success,
        )
        actionHistory.add(record)

        // Keep only the last N actions to bound memory usage
        while (actionHistory.size > MAX_HISTORY_SIZE) {
            actionHistory.removeFirst()
        }

        currentTask = currentTask?.copy(
            stepIndex = (currentTask?.stepIndex ?: 0) + 1,
        )
        persist()
    }

    fun updateScreenState(hash: String, packageName: String?) {
        currentTask = currentTask?.copy(
            screenStateHash = hash,
            currentPackage = packageName,
        )
        persist()
    }

    fun clear() {
        currentTask = null
        actionHistory.clear()
        prefs.edit()
            .remove(KEY_TASK_STATE)
            .remove(KEY_ACTION_HISTORY)
            .apply()
    }

    // --- Context for LLM prompts ---

    /**
     * Generate a compact text summary of current working memory state.
     * This is injected into LLM prompts to give the model task context.
     */
    fun getContext(): String {
        val task = currentTask ?: return "No active task."

        return buildString {
            appendLine("## Current Task")
            appendLine("Goal: ${task.goal}")
            appendLine("Step: ${task.stepIndex}")
            appendLine("Duration: ${System.currentTimeMillis() - task.startTime}ms")
            task.currentPackage?.let { appendLine("Current app: $it") }

            if (actionHistory.isNotEmpty()) {
                appendLine()
                appendLine("## Recent Actions (last ${actionHistory.size})")
                actionHistory.takeLast(5).forEachIndexed { i, action ->
                    val status = if (action.success) "OK" else "FAILED"
                    appendLine("  ${i + 1}. ${action.actionType}(${action.target}) -> $status")
                }
            }

            // Flag if we seem stuck (same action repeated with failures)
            val recentFailures = actionHistory.takeLast(3).count { !it.success }
            if (recentFailures >= 2) {
                appendLine()
                appendLine("WARNING: Multiple recent failures detected. Consider replanning.")
            }
        }
    }

    // --- Process death recovery ---

    /**
     * Persist to SharedPreferences so we can recover after Android kills our process.
     */
    private fun persist() {
        val json = Json { ignoreUnknownKeys = true }
        prefs.edit()
            .putString(KEY_TASK_STATE, currentTask?.let { json.encodeToString(it) })
            .putString(KEY_ACTION_HISTORY, json.encodeToString(actionHistory.toList()))
            .apply()
    }

    /**
     * Restore working memory after process restart.
     * Call this in Service.onCreate() or Application.onCreate().
     */
    fun restore() {
        val json = Json { ignoreUnknownKeys = true }
        try {
            currentTask = prefs.getString(KEY_TASK_STATE, null)
                ?.let { json.decodeFromString<TaskState>(it) }
            prefs.getString(KEY_ACTION_HISTORY, null)
                ?.let { json.decodeFromString<List<ActionRecord>>(it) }
                ?.let {
                    actionHistory.clear()
                    actionHistory.addAll(it)
                }
        } catch (e: Exception) {
            // Corrupted state, start fresh
            clear()
        }
    }
}
```

---

## Vector Store Operations with sqlite-vec

Episodic memory uses sqlite-vec for embedding storage and similarity search. Embeddings
are generated on-device using EmbeddingGemma via MediaPipe.

### Embedding Generation

```kotlin
package ai.neuron.memory

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmbeddingService @Inject constructor() {
    // MediaPipe LLM Inference API for on-device embedding generation
    // Model: EmbeddingGemma (quantized, ~50MB)

    companion object {
        const val EMBEDDING_DIM = 256  // EmbeddingGemma output dimension
    }

    /**
     * Generate an embedding vector for a text string.
     * Runs on-device via MediaPipe. Typical latency: 20-50ms.
     *
     * Returns a FloatArray of size EMBEDDING_DIM, or null if generation fails.
     */
    suspend fun embed(text: String): FloatArray? {
        // TODO: Implement with MediaPipe LLM Inference SDK
        // val result = embeddingModel.generateEmbedding(text)
        // return result.floatArray
        return null // placeholder
    }

    /**
     * Convert FloatArray to ByteArray for Room storage.
     * sqlite-vec expects raw float bytes in little-endian format.
     */
    fun toByteArray(embedding: FloatArray): ByteArray {
        val buffer = java.nio.ByteBuffer.allocate(embedding.size * 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /**
     * Convert ByteArray back to FloatArray for similarity computation.
     */
    fun fromByteArray(bytes: ByteArray): FloatArray {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buffer.getFloat() }
    }
}
```

### Episodic Memory with Vector Search

```kotlin
package ai.neuron.memory

import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodicMemory @Inject constructor(
    private val taskTraceDao: TaskTraceDao,
    private val embeddingService: EmbeddingService,
    private val database: NeuronDatabase,
) {
    /**
     * Record a completed task trace with its embedding.
     * Embedding generation is async and non-blocking.
     */
    suspend fun recordTrace(goal: String, stepsJson: String, outcome: String, durationMs: Long) {
        // Generate embedding for the goal text (on-device, ~30ms)
        val embedding = embeddingService.embed(goal)
        val embeddingBytes = embedding?.let { embeddingService.toByteArray(it) }

        val trace = TaskTrace(
            goal = goal,
            stepsJson = stepsJson,
            outcome = outcome,
            durationMs = durationMs,
            embedding = embeddingBytes,
        )
        taskTraceDao.insert(trace)
    }

    /**
     * Find task traces similar to the given goal using vector similarity search.
     *
     * Uses sqlite-vec extension for efficient nearest-neighbor search.
     * Falls back to text-based search if embeddings are unavailable.
     *
     * SQL with sqlite-vec:
     *   SELECT *, vec_distance_cosine(embedding, ?) AS distance
     *   FROM task_traces
     *   WHERE embedding IS NOT NULL
     *   ORDER BY distance ASC
     *   LIMIT ?
     */
    suspend fun findSimilar(currentGoal: String, topK: Int = 3): List<TaskTrace> {
        val queryEmbedding = embeddingService.embed(currentGoal)

        if (queryEmbedding != null) {
            return vectorSearch(queryEmbedding, topK)
        }

        // Fallback: text-based search
        return taskTraceDao.searchByGoal(currentGoal, topK)
    }

    /**
     * Vector similarity search using sqlite-vec.
     *
     * sqlite-vec provides the vec_distance_cosine() function for cosine distance.
     * Lower distance = more similar.
     */
    private suspend fun vectorSearch(queryEmbedding: FloatArray, topK: Int): List<TaskTrace> {
        val queryBytes = embeddingService.toByteArray(queryEmbedding)

        // Use raw query with sqlite-vec extension
        val db: SupportSQLiteDatabase = database.openHelper.readableDatabase

        val cursor = db.query(
            """
            SELECT id, goal, steps_json, outcome, duration_ms, embedding, created_at,
                   vec_distance_cosine(embedding, ?) AS distance
            FROM task_traces
            WHERE embedding IS NOT NULL
            ORDER BY distance ASC
            LIMIT ?
            """,
            arrayOf(queryBytes, topK),
        )

        val results = mutableListOf<TaskTrace>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    TaskTrace(
                        id = it.getLong(0),
                        goal = it.getString(1),
                        stepsJson = it.getString(2),
                        outcome = it.getString(3),
                        durationMs = it.getLong(4),
                        embedding = it.getBlob(5),
                        createdAt = it.getLong(6),
                    )
                )
            }
        }
        return results
    }

    /**
     * Get recent successful traces for a specific app.
     * Useful for building workflow suggestions.
     */
    suspend fun getSuccessfulTraces(limit: Int = 10): List<TaskTrace> {
        return taskTraceDao.getByOutcome("success", limit)
    }
}
```

---

## Memory Injection into LLM Prompts

The memory system feeds context into every LLM call. This is how the AI "remembers"
user preferences and past experiences.

### Prompt Context Builder

```kotlin
package ai.neuron.brain

import ai.neuron.memory.EpisodicMemory
import ai.neuron.memory.AppWorkflowDao
import ai.neuron.memory.UserPreferenceDao
import ai.neuron.memory.WorkingMemory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptContextBuilder @Inject constructor(
    private val workingMemory: WorkingMemory,
    private val preferenceDao: UserPreferenceDao,
    private val workflowDao: AppWorkflowDao,
    private val episodicMemory: EpisodicMemory,
) {
    /**
     * Build the full context block that gets injected into the LLM system prompt.
     *
     * Output format:
     * ```
     * ## User Preferences
     * - preferred_music_app: com.spotify.music (confidence: 0.92)
     * - preferred_browser: com.android.chrome (confidence: 0.85)
     *
     * ## Known Workflows for com.whatsapp
     * - send_message: [tap search, type name, tap contact, type message, tap send] (95% reliable)
     *
     * ## Similar Past Tasks
     * - "send hello to Dad on WhatsApp" -> success (12s, 4 steps)
     * - "message Mom on WhatsApp" -> success (9s, 4 steps)
     *
     * ## Current Task Context
     * Goal: send hi to Mom on WhatsApp
     * Step: 2
     * Recent actions:
     *   1. launch(com.whatsapp) -> OK
     *   2. tap(search_bar) -> OK
     * ```
     */
    suspend fun build(
        userGoal: String,
        currentPackage: String? = null,
    ): String = buildString {
        // 1. User preferences (relevant to the current task)
        val relevantCategories = inferCategories(userGoal)
        val preferences = preferenceDao.getRelevant(relevantCategories, minConfidence = 0.3f)
        if (preferences.isNotEmpty()) {
            appendLine("## User Preferences")
            preferences.forEach { pref ->
                appendLine("- ${pref.key}: ${pref.value} (confidence: ${"%.2f".format(pref.confidence)})")
            }
            appendLine()
        }

        // 2. Known workflows for the current app
        if (currentPackage != null) {
            val workflows = workflowDao.getByPackage(currentPackage, limit = 3)
            if (workflows.isNotEmpty()) {
                appendLine("## Known Workflows for $currentPackage")
                workflows.forEach { wf ->
                    appendLine("- ${wf.taskType}: ${wf.actionSequenceJson.take(100)} " +
                        "(${(wf.reliability * 100).toInt()}% reliable, used ${wf.successCount}x)")
                }
                appendLine()
            }
        }

        // 3. Similar past task traces (RAG retrieval)
        val similarTasks = episodicMemory.findSimilar(userGoal, topK = 3)
        if (similarTasks.isNotEmpty()) {
            appendLine("## Similar Past Tasks")
            similarTasks.forEach { trace ->
                val durationSec = trace.durationMs / 1000
                appendLine("- \"${trace.goal}\" -> ${trace.outcome} (${durationSec}s)")
            }
            appendLine()
        }

        // 4. Current working memory state
        appendLine(workingMemory.getContext())
    }

    /**
     * Infer which preference categories are relevant for the given goal.
     * Simple keyword matching; can be enhanced with LLM classification later.
     */
    private fun inferCategories(goal: String): List<String> {
        val categories = mutableListOf("app", "shortcut")  // always relevant

        val goalLower = goal.lowercase()
        if ("music" in goalLower || "play" in goalLower || "song" in goalLower) {
            categories.add("music")
        }
        if ("message" in goalLower || "text" in goalLower || "send" in goalLower) {
            categories.add("contact")
            categories.add("messaging")
        }
        if ("search" in goalLower || "browse" in goalLower || "look up" in goalLower) {
            categories.add("browser")
        }
        if ("call" in goalLower || "dial" in goalLower || "phone" in goalLower) {
            categories.add("contact")
            categories.add("calling")
        }

        return categories
    }
}
```

### System Prompt Template

```kotlin
/**
 * The full system prompt sent to the LLM for action planning.
 * Memory context is injected dynamically.
 */
fun buildSystemPrompt(memoryContext: String, screenJson: String): String = """
You are Neuron, an AI assistant that controls an Android phone through accessibility actions.
You can tap, type, swipe, scroll, go back, go home, and launch apps.

RULES:
- Always verify actions succeeded by checking the next screen state.
- Never perform irreversible actions (send, pay, delete) without user confirmation.
- If you are stuck after 3 attempts, ask the user for help.
- Respond with a JSON action plan.

$memoryContext

## Current Screen State
$screenJson

Respond with a JSON action plan:
{
  "reasoning": "brief explanation of what you see and plan to do",
  "actions": [
    {"action": "tap", "target": "resource_id_or_description"},
    {"action": "type", "target": "field_id", "text": "content to type"}
  ],
  "needs_confirmation": false
}
""".trimIndent()
```

---

## Preference Extraction Pattern

After a task completes successfully, Neuron extracts implicit preferences from the
execution trace and stores them for future use.

```kotlin
package ai.neuron.memory

import ai.neuron.brain.IntentClassifier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferenceExtractor @Inject constructor(
    private val preferenceDao: UserPreferenceDao,
    private val workflowDao: AppWorkflowDao,
) {
    /**
     * Extract and store preferences from a completed task.
     *
     * Called after every successful task completion.
     * Preferences are stored with initial confidence of 0.6, which
     * increases each time the same preference is confirmed.
     */
    suspend fun extractFromTrace(trace: TaskTrace) {
        if (trace.outcome != "success") return

        // 1. Extract app preference for this task type
        extractAppPreference(trace)

        // 2. Store the successful workflow
        storeWorkflow(trace)

        // 3. Extract contact preferences if applicable
        extractContactPreference(trace)
    }

    private suspend fun extractAppPreference(trace: TaskTrace) {
        // Parse the first step to get the app package
        val steps = parseSteps(trace.stepsJson)
        val mainPackage = steps.firstOrNull { it.action == "launch" }?.packageName ?: return

        val taskType = classifyTaskType(trace.goal)
        val prefKey = "preferred_${taskType}_app"

        // Check if preference already exists
        val existing = preferenceDao.getByKey(prefKey)
        if (existing != null && existing.value == mainPackage) {
            // Same app used again; increase confidence
            preferenceDao.upsert(
                existing.copy(
                    confidence = minOf(existing.confidence + 0.1f, 1.0f),
                    updatedAt = System.currentTimeMillis(),
                )
            )
        } else {
            // New preference or different app
            preferenceDao.upsert(
                UserPreference(
                    category = "app",
                    key = prefKey,
                    value = mainPackage,
                    confidence = 0.6f,
                )
            )
        }
    }

    private suspend fun storeWorkflow(trace: TaskTrace) {
        val steps = parseSteps(trace.stepsJson)
        val mainPackage = steps.firstOrNull { it.action == "launch" }?.packageName ?: return
        val taskType = classifyTaskType(trace.goal)

        val existing = workflowDao.getBestWorkflow(mainPackage, taskType)
        if (existing != null) {
            workflowDao.incrementSuccess(existing.id)
        } else {
            workflowDao.upsert(
                AppWorkflow(
                    packageName = mainPackage,
                    taskType = taskType,
                    actionSequenceJson = trace.stepsJson,
                    successCount = 1,
                )
            )
        }
    }

    private suspend fun extractContactPreference(trace: TaskTrace) {
        // Simple heuristic: if the goal mentions a name and uses a messaging app,
        // store the contact-app association
        val namePattern = Regex("(?i)(to|message|call|text) (\\w+)")
        val match = namePattern.find(trace.goal) ?: return
        val contactName = match.groupValues[2]

        val steps = parseSteps(trace.stepsJson)
        val appPackage = steps.firstOrNull { it.action == "launch" }?.packageName ?: return

        preferenceDao.upsert(
            UserPreference(
                category = "contact",
                key = "contact_${contactName.lowercase()}_app",
                value = appPackage,
                confidence = 0.5f,
            )
        )
    }

    private fun classifyTaskType(goal: String): String {
        val goalLower = goal.lowercase()
        return when {
            "message" in goalLower || "text" in goalLower || "send" in goalLower -> "messaging"
            "call" in goalLower || "dial" in goalLower || "ring" in goalLower -> "calling"
            "play" in goalLower || "music" in goalLower || "song" in goalLower -> "music"
            "search" in goalLower || "find" in goalLower || "look" in goalLower -> "search"
            "navigate" in goalLower || "directions" in goalLower || "maps" in goalLower -> "navigation"
            "photo" in goalLower || "picture" in goalLower || "camera" in goalLower -> "camera"
            "setting" in goalLower || "toggle" in goalLower || "turn on" in goalLower -> "settings"
            else -> "general"
        }
    }

    private data class StepInfo(val action: String, val packageName: String? = null)

    private fun parseSteps(stepsJson: String): List<StepInfo> {
        return try {
            // Parse JSON array of step objects
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            json.decodeFromString<List<Map<String, String>>>(stepsJson)
                .map { step ->
                    StepInfo(
                        action = step["action"] ?: "",
                        packageName = step["pkg"] ?: step["packageName"],
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
```

---

## Memory Maintenance

### Confidence Decay

Preferences that have not been used recently should have their confidence scores decayed.
Run this periodically (e.g., daily via WorkManager).

```kotlin
package ai.neuron.memory

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MemoryMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferenceDao: UserPreferenceDao,
    private val taskTraceDao: TaskTraceDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Decay confidence on preferences not used in 7+ days
        preferenceDao.decayConfidence(
            decayAmount = 0.05f,
            olderThan = System.currentTimeMillis() - 7 * 86_400_000L,
        )

        // Delete very old traces (90+ days) to bound storage
        taskTraceDao.deleteOlderThan(
            before = System.currentTimeMillis() - 90 * 86_400_000L,
        )

        // Delete preferences with near-zero confidence
        preferenceDao.deleteOlderThan(
            before = System.currentTimeMillis() - 30 * 86_400_000L,
        )

        return Result.success()
    }
}
```

---

## Common Pitfalls

- **Always use suspend functions for DAO calls.** Room does not allow database access on the main thread.
- **SharedPreferences persist is async** (`apply()`), not synchronous (`commit()`). Use `apply()` to avoid blocking.
- **Embedding generation can fail.** Always handle null embeddings and fall back to text search.
- **sqlite-vec must be loaded as an extension.** Ensure it is initialized before any vector queries.
- **Confidence scores must be bounded.** Never let confidence exceed 1.0 or go below 0.0.
- **Process death recovery is critical.** Android kills background processes aggressively. Always call `restore()` in `onCreate()`.
- **JSON serialization of steps must handle malformed data.** LLM-generated JSON can be invalid; always wrap in try-catch.
- **Preference extraction must be idempotent.** Running it twice on the same trace should not create duplicate preferences (use REPLACE conflict strategy).
