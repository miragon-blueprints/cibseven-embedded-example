package io.miragon.blueprint.process

import com.ninjasquad.springmockk.MockkBean
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.process.util.continueToNextWaitState
import io.miragon.blueprint.process.util.executeJobFor
import io.miragon.blueprint.process.util.findProcessInstance
import io.miragon.blueprint.process.util.fireTimer
import io.mockk.every
import io.mockk.verify
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.RuntimeService
import org.cibseven.bpm.engine.TaskService
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
class BikeLeasingProcessTest {

    @Autowired
    private lateinit var process: LeasingProcess

    @Autowired
    private lateinit var runtimeService: RuntimeService

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var processEngine: ProcessEngine

    @MockkBean(relaxed = true)
    private lateinit var validateApplicationUseCase: ValidateApplicationUseCase

    @MockkBean(relaxed = true)
    private lateinit var rejectApplicationUseCase: RejectApplicationUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendContractUseCase: SendContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var cancelContractUseCase: CancelContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var issueInsurancePolicyUseCase: IssueInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    private lateinit var cancelInsurancePolicyUseCase: CancelInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendSignatureReminderUseCase: SendSignatureReminderUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendCancellationConfirmationUseCase: SendCancellationConfirmationUseCase

    @MockkBean(relaxed = true)
    private lateinit var requestOrderCancellationUseCase: RequestOrderCancellationUseCase

    @MockkBean(relaxed = true)
    private lateinit var bookCancellationCostsUseCase: BookCancellationCostsUseCase

    @MockkBean(relaxed = true)
    private lateinit var orderBikeUseCase: OrderBikeUseCase

    @MockkBean(relaxed = true)
    private lateinit var activateLeasingUseCase: ActivateLeasingUseCase

    @BeforeEach
    fun setUp() {
        init(processEngine)
        every { orderBikeUseCase.orderBike(any()) } returns
            OrderBikeUseCase.Result(OrderId("ORDER-1"), bikeAvailable = true)
    }

    @Test
    fun `happy path - contract signed, bike available, leasing becomes active`() {
        val id = submit(age = 35, income = 3500.0)
        val instance = runtimeService.findProcessInstance(id)

        // async continuations up to the contract-signature wait state
        processEngine.executeJobFor(Elements.START_EVENT_LEASING_REQUEST_RECEIVED)
        processEngine.executeJobFor(Elements.SERVICE_TASK_VALIDATE_APPLICATION)
        processEngine.executeJobFor(Elements.SERVICE_TASK_SEND_CONTRACT)

        process.correlateContractSigned(id)
        processEngine.executeJobFor(Elements.EVENT_CONTRACT_SIGNED) // forks into insurance + bike order
        processEngine.executeJobFor(Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY)
        processEngine.executeJobFor(Elements.SERVICE_TASK_ORDER_BIKE) // joins -> handover wait state

        process.correlateHandoverReported(id)
        processEngine.executeJobFor(Elements.EVENT_HANDOVER_REPORTED) // -> withdrawal-period timer

        processEngine.fireTimer(Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED)

        assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                Elements.SERVICE_TASK_VALIDATE_APPLICATION.value,
                Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.value,
                Elements.SERVICE_TASK_SEND_CONTRACT.value,
                Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY.value,
                Elements.EVENT_HANDOVER_REPORTED.value,
                Elements.END_EVENT_LEASING_ACTIVE.value,
            )
            .hasNotPassed(
                Elements.END_EVENT_APPLICATION_REJECTED.value,
                Elements.END_EVENT_APPLICATION_CANCELLED.value,
                Elements.END_EVENT_CONTRACT_CANCELLED.value,
            )

        verify(exactly = 1) { sendContractUseCase.sendContract(id) }
        verify(exactly = 1) { issueInsurancePolicyUseCase.issuePolicy(id) }
        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `escalation - contract not signed in time is escalated and rejected`() {
        val id = submit(age = 35, income = 3500.0)
        val instance = runtimeService.findProcessInstance(id)

        // async continuations up to the contract-signature wait state
        processEngine.executeJobFor(Elements.START_EVENT_LEASING_REQUEST_RECEIVED)
        processEngine.executeJobFor(Elements.SERVICE_TASK_VALIDATE_APPLICATION)
        processEngine.executeJobFor(Elements.SERVICE_TASK_SEND_CONTRACT)

        processEngine.fireTimer(Elements.EVENT_SIGNATURE_DEADLINE) // deadline -> escalation -> rejection
        processEngine.executeJobFor(Elements.SERVICE_TASK_SEND_REJECTION)

        assertThat(instance)
            .isEnded
            .hasPassed(
                Elements.EVENT_SIGNATURE_DEADLINE.value,
                Elements.EVENT_CONTRACT_NOT_SIGNED.value,
                Elements.SERVICE_TASK_SEND_REJECTION.value,
                Elements.END_EVENT_APPLICATION_REJECTED.value,
            )
            .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.value)

        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
    }

    @Test
    fun `not solvent - the DMN routes the application straight to rejection`() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        val id = submit(age = 15, income = 3500.0)

        processEngine.executeJobFor(Elements.START_EVENT_LEASING_REQUEST_RECEIVED)
        processEngine.executeJobFor(Elements.SERVICE_TASK_VALIDATE_APPLICATION) // -> DMN -> not solvent -> rejection
        processEngine.executeJobFor(Elements.SERVICE_TASK_SEND_REJECTION)

        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
        verify(exactly = 0) { sendContractUseCase.sendContract(any()) }
    }

    @Test
    fun `abort - withdrawing the application compensates the completed steps`() {
        every { requestOrderCancellationUseCase.requestCancellation(any()) } returns true

        val id = submit(age = 35, income = 3500.0)
        val instance = runtimeService.findProcessInstance(id)

        // async continuations up to the handover wait state (contract signed, bike ordered, insured)
        processEngine.executeJobFor(Elements.START_EVENT_LEASING_REQUEST_RECEIVED)
        processEngine.executeJobFor(Elements.SERVICE_TASK_VALIDATE_APPLICATION)
        processEngine.executeJobFor(Elements.SERVICE_TASK_SEND_CONTRACT)
        process.correlateContractSigned(id)
        processEngine.executeJobFor(Elements.EVENT_CONTRACT_SIGNED)
        processEngine.executeJobFor(Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY)
        processEngine.executeJobFor(Elements.SERVICE_TASK_ORDER_BIKE)

        // Withdrawing triggers compensation. Its handlers run in an engine-defined order, so drive
        // the continuations generically until the cancelBikeOrder sub-process parks on its user task.
        process.correlateApplicationWithdrawn(id)
        processEngine.continueToNextWaitState()

        val task =
            taskService
                .createTaskQuery()
                .taskDefinitionKey(CancelBikeOrderProcessApi.Elements.USER_TASK_CLARIFY_RETURN.value)
                .singleResult()
        taskService.complete(task.id, mapOf("returnClarified" to true))
        processEngine.continueToNextWaitState()

        assertThat(instance)
            .isEnded
            .hasPassed(
                Elements.SERVICE_TASK_CANCEL_CONTRACT.value,
                Elements.SERVICE_TASK_CANCEL_POLICY.value,
                Elements.CALL_ACTIVITY_CANCEL_BIKE_ORDER.value,
                Elements.SERVICE_TASK_SEND_CANCELLATION_CONFIRMATION.value,
                Elements.END_EVENT_APPLICATION_CANCELLED.value,
            )
            .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.value)

        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
    }

    @Test
    fun `bike unavailable - clarifying an alternative re-orders and leasing becomes active`() {
        // the first order finds the requested bike unavailable, the re-order after the alternative succeeds
        every { orderBikeUseCase.orderBike(any()) } returnsMany
            listOf(
                OrderBikeUseCase.Result(orderId = null, bikeAvailable = false),
                OrderBikeUseCase.Result(OrderId("ORDER-2"), bikeAvailable = true),
            )

        val id = submit(age = 35, income = 3500.0)
        val instance = runtimeService.findProcessInstance(id)

        // async continuations up to the contract-signature wait state, then fork into insurance + bike order
        processEngine.executeJobFor(Elements.START_EVENT_LEASING_REQUEST_RECEIVED)
        processEngine.executeJobFor(Elements.SERVICE_TASK_VALIDATE_APPLICATION)
        processEngine.executeJobFor(Elements.SERVICE_TASK_SEND_CONTRACT)
        process.correlateContractSigned(id)
        processEngine.executeJobFor(Elements.EVENT_CONTRACT_SIGNED)
        processEngine.executeJobFor(Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY)
        processEngine.executeJobFor(Elements.SERVICE_TASK_ORDER_BIKE) // unavailable -> parks on the clarify-alternative user task

        // the alternative is clarified from the outside — the "external" completion of the user task
        process.completeAlternativeClarification(id, alternativeFound = true, bikeId = BikeId("BIKE-ALT"))
        processEngine.continueToNextWaitState() // re-order succeeds -> parallel join -> handover wait state

        process.correlateHandoverReported(id)
        processEngine.executeJobFor(Elements.EVENT_HANDOVER_REPORTED)
        processEngine.fireTimer(Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED)

        assertThat(instance)
            .isEnded
            .hasPassed(
                Elements.USER_TASK_CLARIFY_ALTERNATIVE.value,
                Elements.SERVICE_TASK_ORDER_BIKE.value,
                Elements.END_EVENT_LEASING_ACTIVE.value,
            )
            .hasNotPassed(
                Elements.END_EVENT_CONTRACT_CANCELLED.value,
                Elements.END_EVENT_APPLICATION_REJECTED.value,
            )

        verify(exactly = 2) { orderBikeUseCase.orderBike(id) }
        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }

    private fun submit(age: Int, income: Double, bikeId: String = "BIKE-TEST"): ApplicationId {
        val application =
            LeasingApplication(
                id = ApplicationId.new(),
                customerName = CustomerName("Test Customer"),
                email = Email("test@example.com"),
                age = age,
                monthlyNetIncome = income,
                bikeId = BikeId(bikeId),
                status = LeasingStatus.RECEIVED,
                createdAt = LocalDateTime.now(),
            )
        process.submitRequest(application)
        return application.id
    }
}
