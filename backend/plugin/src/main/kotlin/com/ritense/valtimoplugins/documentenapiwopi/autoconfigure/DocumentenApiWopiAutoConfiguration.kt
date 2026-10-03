/*
 * Copyright 2015-2024 Ritense BV, the Netherlands.
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

package com.ritense.valtimoplugins.documentenapiwopi.autoconfigure

import com.ritense.authorization.AuthorizationService
import com.ritense.catalogiapi.service.CatalogiService
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.documentenapiwopi.DocumentenApiWopiPluginFactory
import com.ritense.valtimoplugins.documentenapiwopi.client.WopiClient
import com.ritense.valtimoplugins.documentenapiwopi.security.DocumentenApiWopiHttpSecurityConfigurer
import com.ritense.valtimoplugins.documentenapiwopi.service.DocumentenApiWopiService
import com.ritense.valtimoplugins.documentenapiwopi.web.rest.DocumentenApiWopiResource
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.web.client.RestClient

@AutoConfiguration
open class DocumentenApiWopiAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(WopiClient::class)
    fun wopiClient(restClientBuilder: RestClient.Builder) = WopiClient(restClientBuilder)

    @Bean
    @ConditionalOnMissingBean(DocumentenApiWopiPluginFactory::class)
    fun documentenApiWopiPluginFactory(
        wopiClient: WopiClient,
        authorizationService: AuthorizationService,
        catalogiService: CatalogiService,
        pluginService: PluginService,
    ): DocumentenApiWopiPluginFactory =
        DocumentenApiWopiPluginFactory(
            wopiClient,
            authorizationService,
            catalogiService,
            pluginService,
        )

    @Bean
    @ConditionalOnMissingBean(DocumentenApiWopiService::class)
    fun documentenWopiApiService(pluginService: PluginService): DocumentenApiWopiService =
        DocumentenApiWopiService(
            pluginService,
        )

    @Bean
    @ConditionalOnMissingBean(DocumentenApiWopiResource::class)
    fun documentenApiWopiResource(documentenApiWopiService: DocumentenApiWopiService): DocumentenApiWopiResource =
        DocumentenApiWopiResource(
            documentenApiWopiService,
        )

    @Order(380)
    @Bean
    fun documentenApiWopiHttpSecurityConfigurer(): DocumentenApiWopiHttpSecurityConfigurer =
        DocumentenApiWopiHttpSecurityConfigurer()
}
