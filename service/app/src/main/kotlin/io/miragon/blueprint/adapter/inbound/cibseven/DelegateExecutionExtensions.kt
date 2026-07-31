package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.domain.leasing.ApplicationId
import org.cibseven.bpm.engine.delegate.DelegateExecution

/**
 * Reads the leasing [ApplicationId] from the process business key. This service starts every instance
 * with the application id as its business key, so delegates never reach for a magic variable name.
 */
fun DelegateExecution.applicationId(): ApplicationId = ApplicationId.of(processBusinessKey)
