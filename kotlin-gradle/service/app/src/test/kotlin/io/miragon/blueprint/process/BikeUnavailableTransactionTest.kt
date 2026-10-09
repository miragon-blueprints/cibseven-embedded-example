package io.miragon.blueprint.process

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.process.util.continueToNextWaitState
import io.miragon.blueprint.process.util.executeJobFor
import io.miragon.blueprint.process.util.findProcessInstance
import org.assertj.core.api.Assertions
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.RuntimeService
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * Runs the out-of-stock order with the **real** use cases instead of mocks. The embedded engine and
 * the `@Transactional` services share one transaction, so the `BikeUnavailableException` must not
 * mark it rollback-only — otherwise the `bikeUnavailable` BPMN error could never be committed and
 * the order job would fail into an incident. [BikeLeasingProcessTest] mocks the use cases and
 * therefore cannot see this.
 */
@SpringBootTest
@ActiveProfiles("test")
class BikeUnavailableTransactionTest {

    @Autowired
    private lateinit var process: LeasingProcess

    @Autowired
    private lateinit var repository: LeasingApplicationRepository

    @Autowired
    private lateinit var runtimeService: RuntimeService

    @Autowired
    private lateinit var processEngine: ProcessEngine

    @BeforeEach
    fun setUp() {
        init(processEngine)
    }

    @Test
    fun `bike unavailable - the BPMN error raised by the real order service is committed`() {
        // BIKE-OOS is on the simulated dealer's out-of-stock list
        val application =
            LeasingApplication.receive(
                id = ApplicationId.new(),
                customerName = CustomerName("Test Customer"),
                email = Email("test@example.com"),
                age = 35,
                monthlyNetIncome = 3500.0,
                bikeId = BikeId("BIKE-OOS"),
                createdAt = LocalDateTime.now(),
            )
        repository.save(application)
        process.submitRequest(application)
        val instance = runtimeService.findProcessInstance(application.id)

        processEngine.executeJobFor(FlowNodes.StartEventLeasingRequestReceived)
        processEngine.executeJobFor(FlowNodes.ServiceTaskSendContract)
        process.correlateContractSigned(application.id)
        processEngine.executeJobFor(FlowNodes.EventContractSigned)
        processEngine.executeJobFor(FlowNodes.ServiceTaskIssueInsurancePolicy)
        processEngine.executeJobFor(FlowNodes.ServiceTaskOrderBike) // unavailable -> BPMN error -> clarify alternative

        assertThat(instance)
            .isWaitingAt(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
            .hasPassed(FlowNodes.EventBikeUnavailable.ELEMENT_ID)
    }

    @Test
    fun `bike unavailable - the bike the process carries is stored although the order service throws`() {
        // the stored application names another bike than the out-of-stock one the process carries
        val application =
            LeasingApplication.receive(
                id = ApplicationId.new(),
                customerName = CustomerName("Test Customer"),
                email = Email("test@example.com"),
                age = 35,
                monthlyNetIncome = 3500.0,
                bikeId = BikeId("BIKE-900"),
                createdAt = LocalDateTime.now(),
            )
        repository.save(application)
        process.submitRequest(application.selectAlternative(BikeId("BIKE-OOS")))

        processEngine.executeJobFor(FlowNodes.StartEventLeasingRequestReceived)
        processEngine.executeJobFor(FlowNodes.ServiceTaskSendContract)
        process.correlateContractSigned(application.id)
        processEngine.executeJobFor(FlowNodes.EventContractSigned)
        processEngine.executeJobFor(FlowNodes.ServiceTaskIssueInsurancePolicy)
        processEngine.executeJobFor(FlowNodes.ServiceTaskOrderBike) // unavailable -> BPMN error -> clarify alternative

        Assertions.assertThat(repository.findById(application.id)?.bikeId).isEqualTo(BikeId("BIKE-OOS"))

        // the alternative reaches the application through the process variable alone
        process.completeAlternativeClarification(application.id, alternativeFound = true, bikeId = BikeId("BIKE-ALT"))
        processEngine.continueToNextWaitState()

        val reordered = repository.findById(application.id)
        Assertions.assertThat(reordered?.bikeId).isEqualTo(BikeId("BIKE-ALT"))
        Assertions.assertThat(reordered?.status).isEqualTo(LeasingStatus.ORDERED)
    }
}
