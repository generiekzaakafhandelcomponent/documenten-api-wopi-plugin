/*
 * Copyright 2015-2026 Ritense BV, the Netherlands.
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

import okhttp3.mockwebserver.MockResponse

/** Shared by WopiClientTest and DocumentenApiWopiResourceIT so both exercise the same discovery fixture. */
internal fun discoveryResponse(): MockResponse =
    MockResponse()
        .addHeader("Content-Type", "application/xml")
        .setBody(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <wopi-discovery>
                <net-zone name="external-https">
                    <app name="Word" favIconUrl="https://example.com/word.ico">
                        <action name="edit" ext="docx" default="true" urlsrc="https://example.com/wopi/action"/>
                    </app>
                </net-zone>
            </wopi-discovery>
            """.trimIndent(),
        )

/**
 * MS-WOPI discovery documents may contain multiple sibling <net-zone> elements (e.g. an internal and an external
 * zone); used to verify Jackson deserializes all of them into WopiDiscovery.netZones rather than only the last one.
 */
internal fun multiZoneDiscoveryResponse(): MockResponse =
    MockResponse()
        .addHeader("Content-Type", "application/xml")
        .setBody(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <wopi-discovery>
                <net-zone name="internal-https">
                    <app name="Excel" favIconUrl="https://example.com/excel.ico">
                        <action name="edit" ext="xlsx" default="true" urlsrc="https://internal.example.com/wopi/action"/>
                    </app>
                </net-zone>
                <net-zone name="external-https">
                    <app name="Word" favIconUrl="https://example.com/word.ico">
                        <action name="edit" ext="docx" default="true" urlsrc="https://example.com/wopi/action"/>
                    </app>
                </net-zone>
            </wopi-discovery>
            """.trimIndent(),
        )
