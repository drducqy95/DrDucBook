package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.AiAgentAudit
import io.legado.app.data.entities.AiAgentProposal
import io.legado.app.data.entities.AiAgentRun
import io.legado.app.data.entities.AiAgentTrace
import kotlinx.coroutines.flow.Flow

@Dao
interface AiAgentDao {

    @Query("SELECT * FROM ai_agent_runs ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentRuns(limit: Int): Flow<List<AiAgentRun>>

    @Query("SELECT * FROM ai_agent_trace WHERE runId = :runId ORDER BY stepIndex ASC")
    fun observeTrace(runId: String): Flow<List<AiAgentTrace>>

    @Query("SELECT * FROM ai_agent_proposals WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun observePendingProposals(): Flow<List<AiAgentProposal>>

    @Query("SELECT * FROM ai_agent_proposals ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentProposals(limit: Int): Flow<List<AiAgentProposal>>

    @Query("SELECT * FROM ai_agent_audits ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentAudits(limit: Int): Flow<List<AiAgentAudit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: AiAgentRun)

    @Query("DELETE FROM ai_agent_trace WHERE runId = :runId")
    suspend fun deleteTraceForRun(runId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrace(trace: List<AiAgentTrace>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProposal(proposal: AiAgentProposal)

    @Query("UPDATE ai_agent_proposals SET status = :status, resolvedAt = :resolvedAt WHERE id = :proposalId")
    suspend fun updateProposalStatus(proposalId: String, status: String, resolvedAt: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: AiAgentAudit)
}
