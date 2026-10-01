package io.miragon.blueprint.process

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi
import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery
import io.miragon.blueprint.application.port.inbound.GetPendingClarificationsQuery
import io.miragon.blueprint.application.port.inbound.ReportHandoverUseCase
import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase
import io.miragon.blueprint.application.port.inbound.SignContractUseCase
import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase
import io.miragon.blueprint.application.port.inbound.WithdrawApplicationUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.process.util.continueToNextWaitState
import io.miragon.blueprint.process.util.executeJobFor
import io.miragon.blueprint.process.util.findProcessInstance
import io.miragon.blueprint.process.util.fireTimer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.cibseven.bpm.engine.ProcessEngine
import org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * Drives the deployed process through the real use cases, delegates, listeners, DMN table and JPA
 * adapters — nothing is mocked, in contrast to [BikeLeasingProcessTest], which isolates the model.
 * That makes it runnable inside a GraalVM native image, where mocking libraries cannot generate
 * classes: tagged `native`, it is the suite `./gradlew -Pnative nativeTest` compiles and executes.
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("native")
class BikeLeasingEndToEndTest {

    @Autowired
    private lateinit var submitLeasingRequest: SubmitLeasingRequestUseCase

    @Autowired
    private lateinit var signContract: SignContractUseCase

    @Autowired
    private lateinit var reportHandover: ReportHandoverUseCase

    @Autowired
    private lateinit var withdrawApplication: WithdrawApplicationUseCase

    @Autowired
    private lateinit var selectAlternative: SelectAlternativeUseCase

    @Autowired
    private lateinit var leasingApplications: GetLeasingApplicationQuery

    @Autowired
    private lateinit var pendingClarifications: GetPendingClarificationsQuery

    @Autowired
    private lateinit var processEngine: ProcessEngine

    @BeforeEach
    fun setUp() {
        BpmnAwareTests.init(processEngine)
    }

    @AfterEach
    fun removeUnfinishedInstances() {
        processEngine.runtimeService
            .createProcessInstanceQuery()
            .rootProcessInstances()
            .list()
            .forEach { processEngine.runtimeService.deleteProcessInstance(it.id, "scenario finished") }
    }

    @Test
    fun `happy path - a solvent customer signs, gets the bike handed over and the leasing becomes active`() {

        // given: a submitted request that has been validated, rated and answered with a contract
        val id = submit()
        val instance = processEngine.runtimeService.findProcessInstance(id)
        processEngine.continueToNextWaitState()
        assertThat(application(id).contractId).isNotNull()

        // when: the customer signs, the bike is handed over and the withdrawal period elapses
        signContract.signContract(id)
        processEngine.continueToNextWaitState()
        assertThat(application(id).status).isEqualTo(LeasingStatus.ORDERED)
        assertThat(application(id).orderId).isNotNull()
        reportHandover.reportHandover(id)
        processEngine.continueToNextWaitState()
        processEngine.fireTimer(Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED)

        // then: the leasing is active and the instance ended on the success path
        assertThat(application(id).status).isEqualTo(LeasingStatus.ACTIVE)
        BpmnAwareTests.assertThat(instance)
            .isEnded
            .hasPassedInOrder(
                Elements.SERVICE_TASK_VALIDATE_APPLICATION.value,
                Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.value,
                Elements.SERVICE_TASK_SEND_CONTRACT.value,
                Elements.EVENT_CONTRACT_SIGNED.value,
                Elements.EVENT_HANDOVER_REPORTED.value,
                Elements.END_EVENT_LEASING_ACTIVE.value,
            )
            .hasPassed(Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY.value, Elements.SERVICE_TASK_ORDER_BIKE.value)
            .hasNotPassed(Elements.END_EVENT_APPLICATION_REJECTED.value, Elements.END_EVENT_APPLICATION_CANCELLED.value)
    }

    @Test
    fun `not solvent - the DMN table rejects a minor before any contract is sent`() {

        // given: a request from a 15-year-old
        val id = submit(age = 15)
        val instance = processEngine.runtimeService.findProcessInstance(id)

        // when: the process runs to its end
        processEngine.continueToNextWaitState()

        // then: the application is rejected by the decision and no contract was sent
        assertThat(application(id).status).isEqualTo(LeasingStatus.REJECTED)
        assertThat(application(id).contractId).isNull()
        BpmnAwareTests.assertThat(instance)
            .isEnded
            .hasPassed(Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.value, Elements.END_EVENT_APPLICATION_REJECTED.value)
            .hasNotPassed(Elements.SERVICE_TASK_SEND_CONTRACT.value)
    }

    @Test
    fun `invalid application - the BPMN error raised by the delegate is caught and leads to a rejection`() {

        // given: a request without income, which the domain refuses as invalid
        val id = submit(monthlyNetIncome = 0.0)
        val instance = processEngine.runtimeService.findProcessInstance(id)

        // when: the process runs to its end
        processEngine.continueToNextWaitState()

        // then: the error boundary event routed it to the rejection, bypassing the credit check
        assertThat(application(id).status).isEqualTo(LeasingStatus.REJECTED)
        BpmnAwareTests.assertThat(instance)
            .isEnded
            .hasPassed(Elements.EVENT_APPLICATION_INVALID.value, Elements.END_EVENT_APPLICATION_REJECTED.value)
            .hasNotPassed(Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING.value)
    }

    @Test
    fun `escalation - the customer is reminded after 7 days and rejected after the 14-day deadline`() {

        // given: a request waiting for the signature
        val id = submit()
        val instance = processEngine.runtimeService.findProcessInstance(id)
        processEngine.continueToNextWaitState()

        // when: the non-interrupting reminder timer fires
        processEngine.fireTimer(Elements.EVENT_SIGNATURE_REMINDER)
        processEngine.continueToNextWaitState()

        // then: the reminder is sent while the instance keeps waiting for the signature
        BpmnAwareTests.assertThat(instance)
            .isActive
            .hasPassed(Elements.SERVICE_TASK_SEND_REMINDER_MAIL.value, Elements.END_EVENT_PROSPECT_REMINDED.value)

        // when: the signature deadline elapses
        processEngine.fireTimer(Elements.EVENT_SIGNATURE_DEADLINE)
        processEngine.continueToNextWaitState()

        // then: the escalation leaves the sub-process and the application is rejected
        assertThat(application(id).status).isEqualTo(LeasingStatus.REJECTED)
        BpmnAwareTests.assertThat(instance)
            .isEnded
            .hasPassed(Elements.EVENT_CONTRACT_NOT_SIGNED.value, Elements.END_EVENT_APPLICATION_REJECTED.value)
            .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.value)
    }

    @Test
    fun `withdrawal - compensation cancels contract, policy and order through the called process`() {

        // given: a signed, insured and ordered application waiting for the handover
        val id = submit()
        val instance = processEngine.runtimeService.findProcessInstance(id)
        processEngine.continueToNextWaitState()
        signContract.signContract(id)
        processEngine.continueToNextWaitState()

        // when: the customer withdraws and the return is clarified in the called cancel-bike-order process
        withdrawApplication.withdraw(id)
        processEngine.continueToNextWaitState()
        assertThat(application(id).status).isEqualTo(LeasingStatus.WITHDRAWN)
        val clarifyReturn =
            processEngine.taskService
                .createTaskQuery()
                .taskDefinitionKey(CancelBikeOrderProcessApi.Elements.USER_TASK_CLARIFY_RETURN.value)
                .singleResult()
        processEngine.taskService.complete(clarifyReturn.id, mapOf("returnClarified" to true))
        processEngine.continueToNextWaitState()

        // then: every completed step was compensated and the application is cancelled
        assertThat(application(id).status).isEqualTo(LeasingStatus.CANCELLED)
        BpmnAwareTests.assertThat(instance)
            .isEnded
            .hasPassed(
                Elements.SERVICE_TASK_CANCEL_CONTRACT.value,
                Elements.SERVICE_TASK_CANCEL_POLICY.value,
                Elements.CALL_ACTIVITY_CANCEL_BIKE_ORDER.value,
                Elements.SERVICE_TASK_SEND_CANCELLATION_CONFIRMATION.value,
                Elements.END_EVENT_APPLICATION_CANCELLED.value,
            )
            .hasNotPassed(Elements.END_EVENT_LEASING_ACTIVE.value)
    }

    @Test
    fun `bike unavailable - the clarification shows up in the inbox and an alternative leads to an active leasing`() {

        // given: a signed application for the dealer's out-of-stock bike
        val id = submit(bikeId = "BIKE-OOS", bikeModel = "Mountain Trail 600")
        val instance = processEngine.runtimeService.findProcessInstance(id)
        processEngine.continueToNextWaitState()
        signContract.signContract(id)
        processEngine.continueToNextWaitState()

        // when: the parked user task is resolved with an alternative bike
        assertThat(pendingClarifications.pending().map { it.applicationId }).contains(id)
        selectAlternative.selectAlternative(
            SelectAlternativeUseCase.Command(id, alternativeFound = true, BikeId("BIKE-900"), "Gravel Explorer 900"),
        )
        processEngine.continueToNextWaitState()

        // then: the re-order succeeds for the alternative and the leasing can be activated
        assertThat(pendingClarifications.pending().map { it.applicationId }).doesNotContain(id)
        assertThat(application(id).bikeId).isEqualTo(BikeId("BIKE-900"))
        assertThat(application(id).status).isEqualTo(LeasingStatus.ORDERED)
        reportHandover.reportHandover(id)
        processEngine.continueToNextWaitState()
        processEngine.fireTimer(Elements.EVENT_WITHDRAWAL_PERIOD_ELAPSED)
        assertThat(application(id).status).isEqualTo(LeasingStatus.ACTIVE)
        BpmnAwareTests.assertThat(instance)
            .isEnded
            .hasPassed(Elements.USER_TASK_CLARIFY_ALTERNATIVE.value, Elements.END_EVENT_LEASING_ACTIVE.value)
    }

    @Test
    fun `dealer outage - the failing order job exhausts its retries and raises an incident`() {

        // given: a signed application for the bike whose dealer is down
        val id = submit(bikeId = "BIKE-FAIL", bikeModel = "Dealer Outage Demo")
        val instance = processEngine.runtimeService.findProcessInstance(id)
        processEngine.continueToNextWaitState()
        signContract.signContract(id)
        processEngine.executeJobFor(Elements.EVENT_CONTRACT_SIGNED)
        processEngine.executeJobFor(Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY)

        // when: the order job fails once per configured retry
        repeat(ORDER_JOB_RETRIES) {
            assertThatThrownBy { processEngine.executeJobFor(Elements.SERVICE_TASK_ORDER_BIKE) }
                .hasMessageContaining("Bike dealer API unavailable")
        }

        // then: the job is out of retries and an incident marks the order task
        val incident = processEngine.runtimeService.createIncidentQuery().processInstanceId(instance.id).singleResult()
        assertThat(incident.incidentType).isEqualTo("failedJob")
        assertThat(incident.activityId).isEqualTo(Elements.SERVICE_TASK_ORDER_BIKE.value)
        BpmnAwareTests.assertThat(instance).isActive
    }

    private fun submit(
        age: Int = 35,
        monthlyNetIncome: Double = 3500.0,
        bikeId: String = "BIKE-900",
        bikeModel: String = "Gravel Explorer 900",
    ): ApplicationId =
        submitLeasingRequest.submit(
            SubmitLeasingRequestUseCase.Command(
                customerName = CustomerName("Nora Native"),
                email = Email("nora@example.com"),
                age = age,
                monthlyNetIncome = monthlyNetIncome,
                bikeId = BikeId(bikeId),
                bikeModel = bikeModel,
            ),
        )

    private fun application(id: ApplicationId): LeasingApplication = checkNotNull(leasingApplications.byId(id)).application

    private companion object {
        const val ORDER_JOB_RETRIES = 3
    }
}
