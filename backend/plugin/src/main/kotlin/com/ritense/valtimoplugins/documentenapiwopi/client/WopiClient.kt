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

package com.ritense.valtimoplugins.documentenapiwopi.client

import com.ritense.documentenapi.DocumentenApiAuthentication
import com.ritense.valtimo.contract.annotation.SkipComponentScan
import com.ritense.valtimoplugins.documentenapiwopi.domain.WopiAccessToken
import com.ritense.valtimoplugins.documentenapiwopi.domain.WopiDiscovery
import com.ritense.zgw.ClientTools
import org.springframework.http.converter.ResourceHttpMessageConverter
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import org.springframework.web.util.UriBuilder
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@SkipComponentScan
@Component
class WopiClient(
    private val restClientBuilder: RestClient.Builder,
) {
    private val discoveryCache = ConcurrentHashMap<URI, CachedDiscovery>()

    // One lock per discovery URL, not a single shared lock: refreshing one WOPI host's discovery must not block a
    // concurrent request for an unrelated host's discovery. Only guards the cache-miss/refetch path below, so
    // concurrent cache hits (the hot path) never contend on it.
    private val discoveryLocks = ConcurrentHashMap<URI, Any>()

    fun getWopiDiscovery(wopiClientDiscoveryUrl: URI): WopiDiscovery {
        discoveryCache[wopiClientDiscoveryUrl]?.let { cached ->
            if (Instant.now().isBefore(cached.expiresAt)) {
                return cached.discovery
            }
        }

        val lock = discoveryLocks.computeIfAbsent(wopiClientDiscoveryUrl) { Any() }
        return synchronized(lock) {
            // Re-check: another thread may have already refreshed this entry while we were waiting for the lock.
            discoveryCache[wopiClientDiscoveryUrl]?.let { cached ->
                if (Instant.now().isBefore(cached.expiresAt)) {
                    return@synchronized cached.discovery
                }
            }

            val result =
                checkNotNull(
                    restClient()
                        .get()
                        .uri {
                            ClientTools
                                .baseUrlToBuilder(it, wopiClientDiscoveryUrl)
                                .build()
                        }.retrieve()
                        .body<WopiDiscovery>(),
                ) { "WOPI discovery response from '$wopiClientDiscoveryUrl' was empty" }

            discoveryCache[wopiClientDiscoveryUrl] = CachedDiscovery(result, Instant.now().plus(DISCOVERY_CACHE_TTL))

            result
        }
    }

    fun getWopiAccessToken(
        baseUrl: URI,
        documentId: String,
        documentenApiAuthentication: DocumentenApiAuthentication,
    ): WopiAccessToken =
        checkNotNull(
            restClient(documentenApiAuthentication)
                .post()
                .uri { wopiUriBuilder(it, baseUrl, "/wopi/api/v1/token/$documentId").build() }
                .retrieve()
                .body<WopiAccessToken>(),
        ) { "WOPI access token response for document '$documentId' was empty" }

    /**
     * Builds the browser-facing WOPI host page URL. This must NOT be fetched server-side and relayed to the
     * frontend: the resulting page is rendered by the WOPI host (e.g. cg-dmf), and the browser needs to navigate
     * there directly so any markup it returns executes under the WOPI host's own origin, not ours.
     */
    fun buildWopiHostPageUrl(
        baseUrl: URI,
        wopiClientUrl: URI,
        documentId: String,
        wopiAccessToken: WopiAccessToken,
    ): URI =
        wopiUriBuilder(UriComponentsBuilder.newInstance(), baseUrl, "/wopi/files/$documentId")
            .queryParam("access_token", wopiAccessToken.accessToken)
            .queryParam("wopiClient", wopiClientUrl.toString())
            .build()

    // replacePath drops baseUrl's own path (e.g. /documenten/); the WOPI extension is mounted at the host root.
    private fun wopiUriBuilder(
        builder: UriBuilder,
        baseUrl: URI,
        path: String,
    ): UriBuilder = ClientTools.baseUrlToBuilder(builder, baseUrl).replacePath(path)

    private fun restClient(authentication: DocumentenApiAuthentication? = null): RestClient =
        restClientBuilder
            .clone()
            .apply {
                authentication?.applyAuth(it)
            }.messageConverters {
                it + ResourceHttpMessageConverter(true)
            }.build()

    private data class CachedDiscovery(
        val discovery: WopiDiscovery,
        val expiresAt: Instant,
    )

    companion object {
        // WOPI discovery is near-static; office suites expect clients to cache it rather than refetch per document open
        private val DISCOVERY_CACHE_TTL: Duration = Duration.ofHours(24)
    }
}
