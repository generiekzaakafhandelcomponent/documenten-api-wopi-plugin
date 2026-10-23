# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Valtimo/GZAC plugin ("Documenten API WOPI") that lets users open, edit and collaborate on documents
(disclosed via the "Documenten API" plugin) directly in the browser, using the WOPI protocol
(e.g. with Collabora Online or OnlyOffice as the editing suite). It has a Kotlin/Spring Boot backend
module and an Angular frontend module, published as plugin artifacts that are added to a Valtimo/GZAC
application.

Valtimo itself (the low-code business process automation / case management platform this plugin
extends — Spring Boot backend + Angular frontend, docs at https://docs.valtimo.nl) is checked out
separately at `/Users/maurits/sources/Baseflow/Customers/CommonGround/valtimo` and is registered as an
additional directory for this session (`.claude/settings.local.json` → `permissions.additionalDirectories`).
Consult it when a question concerns core Valtimo APIs this plugin depends on (e.g. `DocumentenApiPlugin`,
`AuthorizationService`, `PluginFactory`, `HttpSecurityConfigurer`) rather than plugin-specific code.

## Plugin hosting model: in-process (decided)

Valtimo supports two ways to ship a plugin: compiled directly into the backend/frontend classpath
("in-process", what this repo does), or as an externally-hosted package (TypeScript compiled to
WebAssembly, run in a sandboxed plugin host, see https://docs.valtimo.nl/configuration-guides/plugins/external-plugins).

This repo deliberately stays in-process, matching sibling GZAC plugins (see "Reference plugin
implementations" below). The external model was evaluated and rejected for this plugin:
- No existing GZAC plugin in this org uses it yet — it would be a first, not a port.
- This plugin's core logic can't be expressed under the external model's sandbox/permission
  constraints: it instantiates the Documenten API plugin in-process (`PluginService.createInstance`)
  to read its URL/credentials, and runs a ZGW `MODIFY` entity-authorization check via
  `AuthorizationService`. Neither has a public, grantable API equivalent today (checked
  `dev-docs/external-plugins/valtimo-api-and-events.md` in the Valtimo repo) — external plugins can
  only reach an explicitly granted REST endpoint allowlist, never another plugin's stored
  configuration/secrets or an internal Kotlin service.

Don't suggest converting this plugin to the external/WASM model unless Valtimo core first exposes
API endpoints covering those two needs.

## Repo layout

- `backend/app` — a sandbox Spring Boot application (`PluginApplication`) used only to run/test the
  plugin locally; it pulls in a preconfigured `docker-compose.yml` (Valtimo, Keycloak, the CG-DMF DRC
  implementing the Documenten API, and Collabora as the WOPI client).
- `backend/plugin` — the actual plugin module, published as `com.ritense.valtimoplugins:documenten-api-wopi-plugin`.
- `frontend` — an Angular workspace; `frontend/projects/plugin` is the publishable plugin library
  (`@valtimo-plugins/documenten-api-wopi-plugin`), the rest is a shell app for local development.
- `valtimo-configurator-metadata.json` — root-level metadata consumed by GZAC's plugin configurator;
  must stay in sync with the actual Gradle artifact id, the frontend package name, and the exported
  `PluginSpecification`/`NgModule` names (see "Plugin consistency" below).
- `documentation/` — `getting-started.md` (local dev setup), `plugin.md` (end-user plugin configuration
  docs), `release-notes.md`.

## Common commands

All commands run from the repo root unless noted.

```shell
# Start Docker dependencies (Valtimo, Keycloak, CG-DMF DRC, Collabora)
./gradlew :backend:app:composeUp

# Run the sandbox backend app
./gradlew :backend:app:bootRun

# Run the plugin module's test suite (this is what CI runs)
./gradlew :backend:plugin:test

# Run a single test class / method
./gradlew :backend:plugin:test --tests "com.ritense.valtimoplugins.documentenapiwopi.client.WopiClientTest"
./gradlew :backend:plugin:test --tests "com.ritense.valtimoplugins.documentenapiwopi.client.WopiClientTest.someMethod"

# Kotlin lint (ktlint) — check and autoformat
./gradlew :backend:plugin:ktlintCheck
./gradlew :backend:plugin:ktlintFormat
```

```shell
# Frontend — install, build all workspace libs, then serve
cd frontend
npm install
npm run libs-build-all
npm start            # serves against conf/proxy.conf.json, proxying to the local backend

# Frontend lint (what CI runs) and tests
npm run lint
npm test
```

Log in to the frontend with a preconfigured Keycloak user (`user`/`user`, `admin`/`admin`, or
`developer`/`developer` — see `documentation/getting-started.md`), open a case with a document, and use
the "Documenten API WOPI plugin" action to open it for editing in Collabora Online.

Test tasks spin up Postgres via docker-compose automatically (`gradle/test.gradle.kts` sets
`spring.profiles.include=inttest,postgresql`); port/DB name are pinned in `backend/plugin/build.gradle.kts`
(`testDbPort`, `testDbName`) and must match the literals in `docker-resources/docker-compose-base-test.yml`
and `backend/plugin/docker-compose-override.yml` if ever changed.

## Architecture

Request flow for opening a document for editing:

1. Frontend calls `GET /api/v1/documenten-api-wopi/{documentenApiConfigurationId}/case-document/{caseDocumentId}/wopi-host-page/{documentId}`
   (`DocumentenApiWopiResource`), secured by `DocumentenApiWopiHttpSecurityConfigurer` (authenticated only;
   fine-grained authorization happens deeper in the call stack, not at the HTTP layer).
2. `DocumentenApiWopiService` resolves the matching `DocumentenApiWopiPlugin` configuration via
   `PluginService`, matching on the referenced `documentenApiConfigurationId` (plugin configs don't have a
   direct foreign key to each other — matching is done by scanning plugin properties, see
   `DocumentenApiWopiPlugin.findConfigurationByDocumentenApiConfiguration`).
3. `DocumentenApiWopiPlugin.getWopiHostPageUrl`:
   - Loads the document's `informatieobject` via the linked `DocumentenApiPlugin` (read without
     authorization — it's only needed to populate the MODIFY permission check that follows).
   - Enforces `ZgwDocumentActionProvider.MODIFY` via `AuthorizationService` — this is the actual access
     control gate for "can this user edit this document" (there's a `TODO` noting this should switch to
     `DocumentenApiPlugin.requireModifyAccess()` once a newer `documenten-api` dependency is available).
   - Derives the file extension from the document's filename to pick the right WOPI "edit" action.
   - Via `WopiClient`: fetches (and caches, 24h TTL, per discovery URL, one lock per URL) the WOPI
     discovery document from the configured client, requests a WOPI access token from the Documenten API
     host for this document, and builds the WOPI host page URL.
   - **The resulting URL is returned to the browser for direct navigation — the backend must never fetch
     and relay that page's HTML itself**, since it's rendered by the WOPI host under its own origin.
4. The browser navigates to that URL directly, landing in the Collabora/OnlyOffice editing UI.

Key extension points:
- `DocumentenApiWopiAutoConfiguration` wires the beans (`WopiClient`, plugin factory, service, REST
  resource, security configurer) — all beans are `@ConditionalOnMissingBean` so host applications can
  override any of them.
- `DocumentenApiWopiPluginFactory` is how Valtimo's `PluginService` instantiates configured plugin
  instances from persisted configuration + injected beans.
- Frontend plugin registration follows the standard Valtimo plugin pattern: `*.specification.ts` exports a
  `PluginSpecification` (id, logo, i18n, configuration component), `*.module.ts` exports the `NgModule`,
  and `components/documenten-api-wopi-configuration/` implements `PluginConfigurationComponent` for the
  plugin-management configuration UI.

## Further reading

- Custom Plugin Definition reference (linked from `documentation/getting-started.md`, the in-process
  model this repo uses): https://docs.valtimo.nl/features/plugins/plugins/custom-plugin-definition
- External/WASM plugin model (considered and rejected — see "Plugin hosting model" above):
  https://docs.valtimo.nl/configuration-guides/plugins/external-plugins

### Reference plugin implementations

Other GZAC plugins following the same in-process Kotlin/Spring + Angular model as this repo (same
`backend/plugin/src/main/kotlin/com/ritense/valtimoplugins/...` layout and
`valtimo-configurator-metadata.json` shape) — useful for comparing conventions:

- https://github.com/generiekzaakafhandelcomponent/openklant-plugin
- https://github.com/generiekzaakafhandelcomponent/open-product-plugin

Both are self-contained (their own HTTP client + `@PluginProperty` settings) and don't demonstrate
this plugin's cross-plugin pattern (resolving another plugin's configuration via
`PluginService.createInstance` and running an `AuthorizationService` entity-permission check).

Note: both are self-contained (their own HTTP client + `@PluginProperty` settings) and don't
demonstrate this plugin's cross-plugin pattern (resolving another plugin's configuration via
`PluginService.createInstance` and running an `AuthorizationService` entity-permission check).

## Plugin consistency (backend ↔ frontend ↔ metadata)

Because this plugin ships as a separate artifact consumed by GZAC's plugin configurator, three places
must stay in lockstep — the `plugin-pr-reviewer` subagent (`.claude/agents/plugin-pr-reviewer.md`) checks
this automatically on PRs touching plugin code:
- `valtimo-configurator-metadata.json`: `backend[].import` must equal
  `com.ritense.valtimoplugins:documenten-api-wopi-plugin` (groupId fixed by `gradle/publishing.gradle`,
  artifactId from `backend/plugin/plugin.properties`); `frontend.import` must equal the `name` in
  `frontend/projects/plugin/package.json`; `frontend.codeAdditions[].spec`/`.module` must match the
  exported `documentenApiWopiPluginSpecification` / `DocumentenApiWopiPluginModule` names.
- Backend plugin properties (`@PluginProperty` fields on `DocumentenApiWopiPlugin`) must match the fields
  read/written in the frontend `DocumentenApiWopiConfig` model (`frontend/projects/plugin/src/lib/models/config.ts`)
  and the configuration component's form.
