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

import com.fasterxml.jackson.databind.JsonNode
import com.ritense.authorization.AuthorizationContext
import com.ritense.authorization.AuthorizationService
import com.ritense.authorization.request.EntityAuthorizationRequest
import com.ritense.catalogiapi.service.CatalogiService
import com.ritense.documentenapi.DocumentenApiPlugin
import com.ritense.documentenapi.authorization.ZgwDocument
import com.ritense.documentenapi.authorization.ZgwDocumentActionProvider
import com.ritense.plugin.annotation.Plugin
import com.ritense.plugin.annotation.PluginProperty
import com.ritense.plugin.service.PluginService
import com.ritense.valtimo.contract.validation.Url
import com.ritense.valtimoplugins.documentenapiwopi.DocumentenApiWopiPlugin.Companion.PLUGIN_KEY
import com.ritense.valtimoplugins.documentenapiwopi.client.WopiClient
import com.ritense.valtimoplugins.documentenapiwopi.domain.WopiAccessToken
import com.ritense.valtimoplugins.documentenapiwopi.domain.WopiDiscovery
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.util.UUID

private val logger = KotlinLogging.logger {}

@Plugin(
    key = PLUGIN_KEY,
    title = "Documenten API WOPI",
    description = "Enables users to view, edit and collaborate on documents disclosed through the Documenten API",
)
class DocumentenApiWopiPlugin(
    private val wopiClient: WopiClient,
    private val authorizationService: AuthorizationService,
    private val catalogiService: CatalogiService,
    private val pluginService: PluginService,
) {
    @Url
    @PluginProperty(key = WOPI_CLIENT_DISCOVERY_URL_PROPERTY, secret = false)
    lateinit var wopiClientDiscoveryUrl: URI

    @PluginProperty(key = DOCUMENTEN_API_CONFIGURATION_ID, secret = false)
    lateinit var documentenApiConfigurationId: String

    fun getWopiHostPageUrl(
        documentId: String,
        caseDocumentId: UUID?,
    ): URI {
        val documentenApiPlugin = getDocumentenApiPlugin()

        // Fetching the document is only needed here to populate the MODIFY check below, not to grant the caller
        // read access in its own right, so the incidental VIEW_LIST check inside getInformatieObject is suppressed.
        // Note: this does not suppress the "document viewed" outbox event getInformatieObject also publishes, since
        // that isn't gated by the authorization context - a denied caller still produces one audit entry.
        val documentInformatieObject =
            AuthorizationContext.runWithoutAuthorization {
                documentenApiPlugin.getInformatieObject(documentId, caseDocumentId)
            }

        // TODO: inlined because `documenten-api:13.41.0.RELEASE` (the version this plugin compiles against) doesn't
        // yet expose DocumentenApiPlugin.requireModifyAccess()/DocumentenApiClient.requireModifyPermission(). Once
        // the dependency is bumped past the release containing that method, replace this block with a direct call
        // to `documentenApiPlugin.requireModifyAccess(documentId, caseDocumentId)` to avoid drifting from upstream.
        authorizationService.requirePermission(
            EntityAuthorizationRequest(
                ZgwDocument::class.java,
                ZgwDocumentActionProvider.MODIFY,
                ZgwDocument(
                    caseDocumentId = caseDocumentId,
                    vertrouwelijkheidaanduiding = documentInformatieObject.vertrouwelijkheidaanduiding?.key,
                    status = documentInformatieObject.status?.key,
                    informatieobjecttypeUrl = documentInformatieObject.informatieobjecttype,
                    informatieobjecttypeOmschrijving =
                        resolveOmschrijving(
                            documentInformatieObject.informatieobjecttype,
                        ),
                ),
            ),
        )

        val extension = documentInformatieObject.bestandsnaam?.substringAfterLast('.', "")?.lowercase()

        val documentenApiAuthentication = documentenApiPlugin.authenticationPluginConfiguration
        val slatToken: WopiAccessToken =
            wopiClient.getWopiAccessToken(
                documentenApiPlugin.url,
                documentId,
                documentenApiAuthentication,
            )
        val wopiDiscovery: WopiDiscovery = wopiClient.getWopiDiscovery(wopiClientDiscoveryUrl)
        val wopiClientUrl: URI = wopiDiscovery.editActionUrl(extension)

        return wopiClient.buildWopiHostPageUrl(documentenApiPlugin.url, wopiClientUrl, documentId, slatToken)
    }

    private fun getDocumentenApiPlugin(): DocumentenApiPlugin =
        checkNotNull(pluginService.createInstance(documentenApiConfigurationId)) {
            "Could not create instance of ${DocumentenApiPlugin::class.simpleName} based on documenten API configuration ID: $documentenApiConfigurationId"
        }

    private fun resolveOmschrijving(url: String?): String? =
        url
            ?.takeIf { it.isNotBlank() }
            ?.let {
                // informatieobjecttypeOmschrijving only enriches the MODIFY authorization request's audit context;
                // it must never turn an otherwise-valid permission check into a hard failure. Covers both a
                // malformed informatieobjecttype string (not validated as a URI at the source) and a transient
                // Catalogi API failure (timeout, connection error, 5xx) - callers only get a less-detailed audit
                // entry, not a broken WOPI-open flow.
                try {
                    catalogiService.getInformatieobjecttype(URI(it))?.omschrijving
                } catch (e: Exception) {
                    logger.warn(e) { "Could not resolve informatieobjecttype omschrijving for '$it'" }
                    null
                }
            }

    companion object {
        const val PLUGIN_KEY = "documentenapiwopi"
        const val WOPI_CLIENT_DISCOVERY_URL_PROPERTY = "wopiClientDiscoveryUrl"
        const val DOCUMENTEN_API_CONFIGURATION_ID = "documentenApiConfigurationId"

        fun findConfigurationByDocumentenApiConfiguration(documentenApiConfigurationId: String) =
            { properties: JsonNode ->
                // a config missing this property should just not match, not blow up the check for every other config
                documentenApiConfigurationId == properties[DOCUMENTEN_API_CONFIGURATION_ID]?.textValue()
            }
    }
}

fun WopiDiscovery.editActionUrl(extension: String?): URI {
    // A null extension (e.g. a document with no bestandsnaam) must never match a discovery action that also has no
    // `ext` attribute - String?.equals(null, ignoreCase = true) would otherwise treat that as a match and silently
    // open the wrong editor.
    val actionUrl =
        extension?.let { ext ->
            netZone.apps
                .flatMap { it.actions.orEmpty() }
                .firstOrNull { it.name == "edit" && it.ext.equals(ext, ignoreCase = true) }
                ?.urlSrc
        }

    if (actionUrl.isNullOrBlank()) {
        throw WopiEditActionNotFoundException(
            "No WOPI 'edit' action found in discovery for file extension '$extension'",
        )
    }

    return URI(actionUrl)
}
