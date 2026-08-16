package io.legado.app.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.domain.agent.AgentPermissionBroker
import io.legado.app.domain.model.AiToolCall
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiAgentIntegrationTest {

    @Test
    fun mutationProposalApprovalAndReplayProtectionWorkEndToEnd() {
        val broker = AgentPermissionBroker()
        val call = AiToolCall("call-1", "add_book_to_bookshelf", "{\"bookUrl\":\"book://one\"}")
        val proposal = broker.createProposal("conversation-1", listOf(call))
        val approval = broker.approve(proposal)

        broker.requireCanExecute(call, approval)
        assertThrows(RuntimeException::class.java) {
            broker.requireCanExecute(call, approval)
        }
    }
}
