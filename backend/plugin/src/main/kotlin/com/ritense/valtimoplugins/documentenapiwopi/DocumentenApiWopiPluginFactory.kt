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

package com.ritense.valtimoplugins.documentenapiwopi

import com.ritense.authorization.AuthorizationService
import com.ritense.catalogiapi.service.CatalogiService
import com.ritense.plugin.PluginFactory
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.documentenapiwopi.client.WopiClient
import org.springframework.stereotype.Component

@Component
class DocumentenApiWopiPluginFactory(
    private val wopiClient: WopiClient,
    private val authorizationService: AuthorizationService,
    private val catalogiService: CatalogiService,
    pluginService: PluginService,
) : PluginFactory<DocumentenApiWopiPlugin>(pluginService) {
    override fun create(): DocumentenApiWopiPlugin =
        DocumentenApiWopiPlugin(
            wopiClient,
            authorizationService,
            catalogiService,
            pluginService,
        )
}
