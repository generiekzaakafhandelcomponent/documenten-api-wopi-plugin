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

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.authorization.AuthorizationService
import com.ritense.authorization.request.EntityAuthorizationRequest
import com.ritense.catalogiapi.service.CatalogiService
import com.ritense.documentenapi.DocumentenApiAuthentication
import com.ritense.documentenapi.DocumentenApiPlugin
import com.ritense.documentenapi.authorization.ZgwDocument
import com.ritense.documentenapi.authorization.ZgwDocumentActionProvider
import com.ritense.documentenapi.client.DocumentInformatieObject
import com.ritense.plugin.service.PluginService
import com.ritense.valtimoplugins.documentenapiwopi.client.WopiClient
import com.ritense.valtimoplugins.documentenapiwopi.domain.Action
import com.ritense.valtimoplugins.documentenapiwopi.domain.App
import com.ritense.valtimoplugins.documentenapiwopi.domain.NetZone
import com.ritense.valtimoplugins.documentenapiwopi.domain.WopiAccessToken
import com.ritense.valtimoplugins.documentenapiwopi.domain.WopiDiscovery
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
import java.net.URI
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DocumentenApiWopiPluginTest {
    private lateinit var authorizationService: AuthorizationService
    private lateinit var catalogiService: CatalogiService
    private lateinit var documentenApiPlugin: DocumentenApiPlugin
    private lateinit var pluginService: PluginService
    private lateinit var wopiClient: WopiClient
    private lateinit var caseDocumentId: UUID

    @BeforeEach
    fun before() {
        authorizationService = mock<AuthorizationService>()
        catalogiService = mock<CatalogiService>()
        documentenApiPlugin = mock<DocumentenApiPlugin>()
        pluginService = mock<PluginService>()
        wopiClient = mock<WopiClient>()
        caseDocumentId = UUID.randomUUID()

        whenever(
            pluginService.createInstance<DocumentenApiPlugin>(DOCUMENTEN_API_CONFIGURATION_ID),
        ).thenReturn(documentenApiPlugin)
    }

    /** A configured plugin whose documentenApiPlugin.getInformatieObject(DOCUMENT_ID, caseDocumentId) is stubbed. */
    private fun pluginWithDocument(
        bestandsnaam: String?,
        informatieobjecttype: String? = null,
    ): DocumentenApiWopiPlugin {
        val documentInformatieObject = mock<DocumentInformatieObject>()
        whenever(documentInformatieObject.bestandsnaam).thenReturn(bestandsnaam)
        whenever(documentInformatieObject.informatieobjecttype).thenReturn(informatieobjecttype)
        whenever(
            documentenApiPlugin.getInformatieObject(DOCUMENT_ID, caseDocumentId),
        ).thenReturn(documentInformatieObject)

        return DocumentenApiWopiPlugin(wopiClient, authorizationService, catalogiService, pluginService).apply {
            documentenApiConfigurationId = DOCUMENTEN_API_CONFIGURATION_ID
            wopiClientDiscoveryUrl = URI("http://localhost:8080")
        }
    }

    /** Stubs the calls made once MODIFY permission is granted: fetching a token and the WOPI discovery document. */
    private fun stubGrantedWopiFlow(discovery: WopiDiscovery = wopiDiscovery) {
        whenever(documentenApiPlugin.authenticationPluginConfiguration).thenReturn(mock<DocumentenApiAuthentication>())
        whenever(documentenApiPlugin.url).thenReturn(wopiHostBaseUrl)
        whenever(wopiClient.getWopiAccessToken(any(), any(), any())).thenReturn(wopiAccessToken)
        whenever(wopiClient.getWopiDiscovery(any())).thenReturn(discovery)
    }

    @Test
    fun `should select the edit action matching the document's file extension`() {
        val plugin = pluginWithDocument(bestandsnaam = "report.docx")
        stubGrantedWopiFlow()
        // Only stub the exact edit/docx URL: if the plugin picks the wrong app, extension or action, this returns null and the test fails.
        whenever(wopiClient.buildWopiHostPageUrl(wopiHostBaseUrl, URI(WORD_EDIT_URL), DOCUMENT_ID, wopiAccessToken))
            .thenReturn(URI(EXPECTED_HOST_PAGE_URL))

        val url: URI = plugin.getWopiHostPageUrl(DOCUMENT_ID, caseDocumentId)

        assertEquals(URI(EXPECTED_HOST_PAGE_URL), url)
        // the WOPI token may only be minted after the caller's MODIFY permission on the document has been verified
        verify(authorizationService).requirePermission(
            check<EntityAuthorizationRequest<ZgwDocument>> { request ->
                assertEquals(ZgwDocument::class.java, request.resourceType)
                assertEquals(ZgwDocumentActionProvider.MODIFY, request.action)
                assertEquals(caseDocumentId, request.entities.single().caseDocumentId)
            },
        )
    }

    @Test
    fun `should still grant access when the informatieobjecttype lookup fails`() {
        val plugin =
            pluginWithDocument(
                bestandsnaam = "report.docx",
                informatieobjecttype = "https://example.com/catalogi/type/1",
            )
        stubGrantedWopiFlow()
        whenever(wopiClient.buildWopiHostPageUrl(wopiHostBaseUrl, URI(WORD_EDIT_URL), DOCUMENT_ID, wopiAccessToken))
            .thenReturn(URI(EXPECTED_HOST_PAGE_URL))
        // A transient Catalogi API failure only enriches the audit context of the MODIFY check; it must not break
        // an otherwise-valid WOPI-open flow.
        whenever(catalogiService.getInformatieobjecttype(any())).thenThrow(RuntimeException("Catalogi API unavailable"))

        val url: URI = plugin.getWopiHostPageUrl(DOCUMENT_ID, caseDocumentId)

        assertEquals(URI(EXPECTED_HOST_PAGE_URL), url)
        verify(authorizationService).requirePermission(
            check<EntityAuthorizationRequest<ZgwDocument>> { request ->
                assertEquals(null, request.entities.single().informatieobjecttypeOmschrijving)
            },
        )
    }

    @Test
    fun `should fail when discovery has no edit action for the document's file extension`() {
        val plugin = pluginWithDocument(bestandsnaam = "presentation.pptx")
        stubGrantedWopiFlow()

        assertFailsWith<WopiEditActionNotFoundException> {
            plugin.getWopiHostPageUrl(DOCUMENT_ID, caseDocumentId)
        }
    }

    @Test
    fun `should fail when the document has no file extension even if discovery has an extensionless edit action`() {
        val plugin = pluginWithDocument(bestandsnaam = null)
        // An extensionless "edit" action (ext == null) must never match a document with no extension either.
        stubGrantedWopiFlow(
            discovery =
                WopiDiscovery(
                    listOf(
                        NetZone(
                            name = "https",
                            apps =
                                listOf(
                                    App(
                                        name = "Generic",
                                        actions =
                                            listOf(
                                                Action(
                                                    name = "edit",
                                                    urlSrc = "https://generic.example.com/edit",
                                                    ext = null,
                                                ),
                                            ),
                                        favIconUrl = null,
                                    ),
                                ),
                        ),
                    ),
                ),
        )

        assertFailsWith<WopiEditActionNotFoundException> {
            plugin.getWopiHostPageUrl(DOCUMENT_ID, caseDocumentId)
        }
    }

    @Test
    fun `should fail when the document's filename has no dot even if discovery has an extensionless edit action`() {
        // Unlike a null bestandsnaam (covered above), a dotless filename like "README" makes substringAfterLast
        // return "" rather than null - this must still be treated as "no extension", not matched against an
        // extensionless discovery action.
        val plugin = pluginWithDocument(bestandsnaam = "README")
        stubGrantedWopiFlow(
            discovery =
                WopiDiscovery(
                    listOf(
                        NetZone(
                            name = "https",
                            apps =
                                listOf(
                                    App(
                                        name = "Generic",
                                        actions =
                                            listOf(
                                                Action(
                                                    name = "edit",
                                                    urlSrc = "https://generic.example.com/edit",
                                                    ext = null,
                                                ),
                                            ),
                                        favIconUrl = null,
                                    ),
                                ),
                        ),
                    ),
                ),
        )

        assertFailsWith<WopiEditActionNotFoundException> {
            plugin.getWopiHostPageUrl(DOCUMENT_ID, caseDocumentId)
        }
    }

    @Test
    fun `should select the edit action from the second net zone when the first has no match`() {
        val plugin = pluginWithDocument(bestandsnaam = "report.docx")
        stubGrantedWopiFlow(
            discovery =
                WopiDiscovery(
                    listOf(
                        NetZone(
                            name = "internal-https",
                            apps =
                                listOf(
                                    App(
                                        name = "Excel",
                                        actions =
                                            listOf(
                                                Action(
                                                    name = "edit",
                                                    urlSrc = "https://internal.example.com/edit",
                                                    ext = "xlsx",
                                                ),
                                            ),
                                        favIconUrl = null,
                                    ),
                                ),
                        ),
                        NetZone(
                            name = "external-https",
                            apps =
                                listOf(
                                    App(
                                        name = "Word",
                                        actions =
                                            listOf(
                                                Action(name = "edit", urlSrc = WORD_EDIT_URL, ext = "docx"),
                                            ),
                                        favIconUrl = null,
                                    ),
                                ),
                        ),
                    ),
                ),
        )
        whenever(wopiClient.buildWopiHostPageUrl(wopiHostBaseUrl, URI(WORD_EDIT_URL), DOCUMENT_ID, wopiAccessToken))
            .thenReturn(URI(EXPECTED_HOST_PAGE_URL))

        val url: URI = plugin.getWopiHostPageUrl(DOCUMENT_ID, caseDocumentId)

        assertEquals(URI(EXPECTED_HOST_PAGE_URL), url)
    }

    @Test
    fun `should deny access and never mint a WOPI token when the caller lacks MODIFY permission`() {
        val plugin = pluginWithDocument(bestandsnaam = "report.docx")
        whenever(
            authorizationService.requirePermission(any<EntityAuthorizationRequest<ZgwDocument>>()),
        ).thenThrow(AccessDeniedException("Unauthorized"))

        assertFailsWith<AccessDeniedException> {
            plugin.getWopiHostPageUrl(DOCUMENT_ID, caseDocumentId)
        }

        // a denied caller must never receive a WOPI token for the document
        verifyNoInteractions(wopiClient)
    }

    @Test
    fun `should check MODIFY permission before minting a WOPI token`() {
        val plugin = pluginWithDocument(bestandsnaam = "report.docx")
        stubGrantedWopiFlow()
        whenever(wopiClient.buildWopiHostPageUrl(wopiHostBaseUrl, URI(WORD_EDIT_URL), DOCUMENT_ID, wopiAccessToken))
            .thenReturn(URI(EXPECTED_HOST_PAGE_URL))

        plugin.getWopiHostPageUrl(DOCUMENT_ID, caseDocumentId)

        inOrder(authorizationService, wopiClient) {
            verify(authorizationService).requirePermission(any<EntityAuthorizationRequest<ZgwDocument>>())
            verify(wopiClient).getWopiAccessToken(any(), any(), any())
        }
    }

    @Test
    fun `should not match a stored config that is missing the documentenApiConfigurationId property`() {
        val propertiesWithoutConfigId =
            ObjectMapper().readTree(
                """{"wopiClientDiscoveryUrl": "http://localhost:8080"}""",
            )

        val matches =
            DocumentenApiWopiPlugin
                .findConfigurationByDocumentenApiConfiguration(DOCUMENTEN_API_CONFIGURATION_ID)
                .invoke(propertiesWithoutConfigId)

        // must not throw and must not match - one malformed stored config should never break the check for every other config
        assertFalse(matches)
    }

    companion object {
        private const val DOCUMENT_ID = "123"
        private const val DOCUMENTEN_API_CONFIGURATION_ID = "documentenApiConfigurationId"
        private const val WORD_EDIT_URL = "https://word.example.com/edit"
        private const val EXPECTED_HOST_PAGE_URL = "https://wopihost.example.com/wopi/files/123?access_token=test"

        private val wopiHostBaseUrl: URI = URI("https://wopihost.example.com")

        private val wopiAccessToken: WopiAccessToken = WopiAccessToken("test", 3600)

        // Two apps, each with both a view and an edit action for a distinct extension - mirrors OnlyOffice/Office
        // Online discovery documents, where different apps/extensions have their own urlSrc (unlike Collabora,
        // where a single browser URL is shared across all actions).
        private val wopiDiscovery: WopiDiscovery =
            WopiDiscovery(
                listOf(
                    NetZone(
                        name = "https",
                        apps =
                            listOf(
                                App(
                                    name = "Word",
                                    actions =
                                        listOf(
                                            Action(
                                                name = "view",
                                                urlSrc = "https://word.example.com/view",
                                                default = true,
                                                ext = "docx",
                                            ),
                                            Action(name = "edit", urlSrc = WORD_EDIT_URL, default = true, ext = "docx"),
                                        ),
                                    favIconUrl = null,
                                ),
                                App(
                                    name = "Excel",
                                    actions =
                                        listOf(
                                            Action(
                                                name = "view",
                                                urlSrc = "https://excel.example.com/view",
                                                default = true,
                                                ext = "xlsx",
                                            ),
                                            Action(
                                                name = "edit",
                                                urlSrc = "https://excel.example.com/edit",
                                                default = true,
                                                ext = "xlsx",
                                            ),
                                        ),
                                    favIconUrl = null,
                                ),
                            ),
                    ),
                ),
            )
    }
}
