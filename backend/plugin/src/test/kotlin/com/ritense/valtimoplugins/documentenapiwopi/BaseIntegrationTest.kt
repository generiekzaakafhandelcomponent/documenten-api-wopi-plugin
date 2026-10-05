/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.valtimoplugins.documentenapiwopi

import com.ritense.authorization.AuthorizationService
import com.ritense.catalogiapi.service.CatalogiService
import com.ritense.catalogiapi.service.ZaaktypeUrlProvider
import com.ritense.plugin.repository.PluginConfigurationRepository
import com.ritense.plugin.service.PluginService
import com.ritense.testutilscommon.junit.extension.LiquibaseRunnerExtension
import com.ritense.valtimo.contract.authentication.UserManagementService
import com.ritense.valtimo.contract.mail.MailSender
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.junit.jupiter.SpringExtension

@SpringBootTest
@ExtendWith(value = [SpringExtension::class, LiquibaseRunnerExtension::class])
@Tag("integration")
abstract class BaseIntegrationTest : BaseTest() {
    @MockitoBean
    lateinit var userManagementService: UserManagementService

    @MockitoBean
    lateinit var mailSender: MailSender

    @MockitoBean
    lateinit var authorizationService: AuthorizationService

    @MockitoBean
    lateinit var catalogiService: CatalogiService

    @MockitoBean
    lateinit var zaaktypeUrlProvider: ZaaktypeUrlProvider

    @MockitoSpyBean
    lateinit var pluginService: PluginService

    @MockitoSpyBean
    lateinit var pluginConfigurationRepository: PluginConfigurationRepository

    fun mockResponse(body: String): MockResponse =
        MockResponse()
            .addHeader("Content-Type", "application/json")
            .setBody(body)
}
