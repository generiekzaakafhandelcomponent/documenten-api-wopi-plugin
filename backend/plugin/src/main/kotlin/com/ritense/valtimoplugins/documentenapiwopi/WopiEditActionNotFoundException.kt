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

import org.zalando.problem.AbstractThrowableProblem
import org.zalando.problem.Status

/**
 * Thrown when WOPI discovery has no edit action for the document's file extension. Maps to HTTP 404, since from a
 * client's perspective the requested edit capability does not exist - mirrors CaseDocumentResolutionException.
 */
class WopiEditActionNotFoundException(
    message: String,
) : AbstractThrowableProblem(
        DEFAULT_TYPE,
        message,
        Status.NOT_FOUND,
        null,
        null,
        null,
        null,
    ) {
    override fun getCause() = null
}
