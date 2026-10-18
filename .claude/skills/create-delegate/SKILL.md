---
name: create-delegate
argument-hint: "[element-id or task name] [DelegateClassName]"
description: "Create, update or test a CIB seven (Camunda 7) JavaDelegate or an execution/task listener for a BPMN element, wired as a Spring @Component and invoked by expression (#{beanName}), together with its unit test and the process test of its path. Use when the user asks to create a delegate, wire a service task to code, add a JavaDelegate or listener, or test an existing delegate. A delegate extends BaseDelegate, calls exactly one inbound port, and reads process variables through the typed ProcessApi."
allowed-tools: Read, Write, Edit, Glob, Grep, Bash, Skill
---

# Skill: create-delegate

One `@Component` per piece of work in `adapter/inbound/cibseven/` (package `io.miragon.blueprint`
under `service/app/src/main/`), plus its tests; one bean may serve several elements
(`sendCancellationConfirmationDelegate`). CIB seven runs **embedded**; service tasks call
**JavaDelegates by expression**, not external task workers. The rules are in `AGENTS.md` (Process
Glue, Architecture); this skill does not repeat them.

## Production pattern

Copy the shape of the existing classes instead of inventing one:

| Case | Reference |
|---|---|
| a delegate that calls a port and nothing else | `SendContractDelegate` |
| reads a variable, writes one back, maps a domain exception to a `BpmnError` | `OrderBikeDelegate` |
| a task listener | `ClarifyAlternativeTaskListener` |
| an execution listener | `BikeOrderAuditListener` |

- A delegate extends `BaseDelegate` and overrides `executeTask`; a listener implements
  `TaskListener` / `ExecutionListener` directly.
- The BPMN references the bean by expression, `#{beanName}` (class name with a lowercase first
  letter): `camunda:delegateExpression` on a service task or message event, `delegateExpression` inside
  `camunda:taskListener` / `camunda:executionListener`. The model test
  `ServiceTaskDelegateExpressionRule` only requires service tasks to use an expression; a wrong bean
  name surfaces in the process test.
- Suffix `Delegate`, `Worker` or `Listener`; at most one inbound port (the architecture tests fail
  above one). A delegate calls exactly one; a listener may call none.

## Test it twice

**1. A unit test** in the test tree's `adapter/inbound/cibseven/<Name>Test`, one test per outcome:
the happy path (the exact port call with the id from the business key, every variable written
back), each `BpmnError` the delegate throws (the code from the generated `Errors`, never a string
literal), and for a listener the effect it has. No Spring, no engine; verify the port call and that
nothing else was called; call `execute`, not `executeTask`, so `BaseDelegate` runs as in the engine.
Same style as the service tests (`OrderBikeServiceTest`): `underTest`, given/when/then comments.

No delegate test exists yet, so here is a sketch, compiled and run against `OrderBikeDelegate`
(imports left out; `FlowNodes` comes from `BikeLeasingProcessProcessApi`):

<!-- variant:kotlin-gradle -->
```kotlin
class OrderBikeDelegateTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val execution = mockk<DelegateExecution>()
    private val underTest = OrderBikeDelegate(useCase)

    private val applicationId = ApplicationId.new()
    private val bikeId = BikeId("BIKE-900")

    @BeforeEach
    fun givenTheApplicationAndItsBike() {
        every { execution.processBusinessKey } returns applicationId.value.toString()
        every { execution.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.value) } returns bikeId.value
        every { execution.currentActivityId } returns FlowNodes.ServiceTaskOrderBike.ELEMENT_ID
    }

    @Test
    fun `orders the bike of the application and keeps the order id`() {

        // given: the dealer accepts the order
        every { useCase.orderBike(applicationId, bikeId) } returns OrderId("ORDER-900")
        every { execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value, "ORDER-900") } just Runs

        // when: the engine executes the delegate
        underTest.execute(execution)

        // then: the order is placed for the application and its id lands in the process
        verify { useCase.orderBike(applicationId, bikeId) }
        verify { execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value, "ORDER-900") }
        confirmVerified(useCase)
    }

    @Test
    fun `turns an unavailable bike into the bikeUnavailable BPMN error`() {

        // given: the dealer cannot deliver the bike
        every { useCase.orderBike(applicationId, bikeId) } throws BikeUnavailableException(bikeId)

        // when / then: the delegate throws the BPMN error the boundary event catches
        assertThatThrownBy { underTest.execute(execution) }
            .isInstanceOf(BpmnError::class.java)
            .extracting("errorCode")
            .isEqualTo(Errors.BIKE_UNAVAILABLE.code)
        verify { useCase.orderBike(applicationId, bikeId) }
        confirmVerified(useCase)
    }
}
```

`BaseDelegate` logs `execution.currentActivityId` on every exception: stub it, or the strict mock
throws a `MockKException` that hides the `BpmnError`.
<!-- /variant:kotlin-gradle -->

<!-- variant:java-maven -->
```java
class OrderBikeDelegateTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);
    private final DelegateExecution execution = mock(DelegateExecution.class);
    private final OrderBikeDelegate underTest = new OrderBikeDelegate(useCase);

    private final ApplicationId applicationId = ApplicationId.newId();
    private final BikeId bikeId = new BikeId("BIKE-900");

    @BeforeEach
    void givenTheApplicationAndItsBike() {
        when(execution.getProcessBusinessKey()).thenReturn(applicationId.value().toString());
        when(execution.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.getValue())).thenReturn(bikeId.value());
    }

    @Test
    void ordersTheBikeOfTheApplicationAndKeepsTheOrderId() throws Exception {

        // given: the dealer accepts the order
        when(useCase.orderBike(applicationId, bikeId)).thenReturn(new OrderId("ORDER-900"));

        // when: the engine executes the delegate
        underTest.execute(execution);

        // then: the order is placed for the application and its id lands in the process
        verify(useCase).orderBike(applicationId, bikeId);
        verify(execution).setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), "ORDER-900");
        verifyNoMoreInteractions(useCase);
    }

    @Test
    void turnsAnUnavailableBikeIntoTheBikeUnavailableBpmnError() {

        // given: the dealer cannot deliver the bike
        when(useCase.orderBike(applicationId, bikeId)).thenThrow(new BikeUnavailableException(bikeId));

        // when / then: the delegate throws the BPMN error the boundary event catches
        assertThatThrownBy(() -> underTest.execute(execution))
                .isInstanceOf(BpmnError.class)
                .extracting("errorCode")
                .isEqualTo(Errors.BIKE_UNAVAILABLE.getCode());
        verify(useCase).orderBike(applicationId, bikeId);
        verifyNoMoreInteractions(useCase);
    }
}
```

Mockito's `mock()` is lenient; the strictness comes from `verifyNoMoreInteractions`.
<!-- /variant:java-maven -->

**2. A process test** for the path through the engine in the test tree's `adapter/process/` (see
`BikeLeasingProcessTest`): it walks the path the element sits on and verifies the port call there.
The unit test proves the mapping, the process test proves the wiring (bean name, variables, boundary
events). Delegates are not in the mutation gate yet (`excludedClasses` of the pitest config), so the
unit test is the only isolated check of the mapping.

## Instructions

1. **Load** the model in `service/app/src/main/resources/bpmn/` and the generated
   `BikeLeasingProcessProcessApi` (or `CancelBikeOrderProcessApi`). The BPMN is the source of truth for
   the element, its `delegateExpression`, its variables and its boundary events.
2. **Identify the element** from `$ARGUMENTS`, else list the service tasks and listeners and ask.
3. **Find the inbound port** in `application/port/inbound/` (`*UseCase`/`*Query`). If it is missing,
   create port and service first, or run skill `automate-process` when the model changes too.
4. **Write the unit test first**, then the delegate; for an existing class add only what is missing.
5. **Add or extend the process test** for the path.
6. **Run** both (`AGENTS.md` → Verify After Each Task): the unit test by name, then the
   `io.miragon.blueprint.adapter.process` tests.
<!-- variant:blueprint -->
7. **Both variants:** the same delegate and the same tests in `kotlin-gradle/` and `java-maven/`.
<!-- /variant:blueprint -->
