package io.miragon.blueprint.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.miragon.blueprint.application.port.inbound.SubmitLeasingRequestUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(SubmitLeasingRequestController.class)
class SubmitLeasingRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubmitLeasingRequestUseCase useCase;

    @Test
    void userSubmitsALeasingRequest() throws Exception {

        // given: valid input data & rest-operation
        ApplicationId applicationId = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        SubmitLeasingRequestUseCase.Command expectedCommand = new SubmitLeasingRequestUseCase.Command(
                new CustomerName("John Doe"),
                new Email("john.doe@test.com"),
                35,
                3500.0,
                new BikeId("BIKE-900"),
                "Gravel Explorer 900");
        when(useCase.submit(any())).thenReturn(applicationId);
        String body = """
                {"customerName":"John Doe","email":"john.doe@test.com","age":35,\
                "monthlyNetIncome":3500.0,"bikeId":"BIKE-900","bikeModel":"Gravel Explorer 900"}""";

        // when: the request is performed
        MvcResult response = mockMvc.perform(post("/api/bike-leasing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        // then: the use case is invoked with the mapped command and the id is returned
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        assertThat(response.getResponse().getContentAsString()).contains(applicationId.value().toString());
        verify(useCase).submit(expectedCommand);
        verifyNoMoreInteractions(useCase);
    }

    @ParameterizedTest
    @ValueSource(strings = {"customerName", "email", "bikeId", "bikeModel"})
    void aRequestWithoutARequiredTextFieldIsRejected(String missingField) throws Exception {

        // given: an otherwise valid request that lacks one required field
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("customerName", "John Doe");
        input.put("email", "john.doe@test.com");
        input.put("age", 35);
        input.put("monthlyNetIncome", 3500.0);
        input.put("bikeId", "BIKE-900");
        input.put("bikeModel", "Gravel Explorer 900");
        input.remove(missingField);

        // when: the request is performed
        MvcResult response = mockMvc.perform(post("/api/bike-leasing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(input)))
                .andReturn();

        // then: it is a bad request and never reaches the use case
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        verifyNoInteractions(useCase);
    }
}
