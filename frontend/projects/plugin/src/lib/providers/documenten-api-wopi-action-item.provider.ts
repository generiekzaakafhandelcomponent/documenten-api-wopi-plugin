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
import {Router} from '@angular/router';
import {ActionItem} from '@valtimo/components';
import {ConfigService} from '@valtimo/shared';
import {DocumentenApiRelatedFile} from '@valtimo/zgw';
import {combineLatest, Observable, of} from 'rxjs';
import {catchError, map, shareReplay, take} from 'rxjs/operators';
import {DocumentenApiWopiService} from '../services';

// The document list of a case (zaak) can contain documents from different Documenten APIs, so
// checking if WOPI editing is supported needs to be done per each file's pluginConfigurationId -
// this cache just makes sure that, for a given configId, the "is WOPI supported" HTTP check fires
// once and is shared by every row (and by both the disabled$ and hidden$ callbacks) instead of
// once per row per callback.
function createHasWopiSupport$(
  wopiService: DocumentenApiWopiService
): (pluginConfigurationId: string) => Observable<boolean> {
  const cache = new Map<string, Observable<boolean>>();

  return (pluginConfigurationId: string): Observable<boolean> => {
    if (!cache.has(pluginConfigurationId)) {
      cache.set(
        pluginConfigurationId,
        wopiService.checkWopiSupport(pluginConfigurationId).pipe(
          catchError(() => {
            // Don't cache failures as "unsupported" - evict so the next call retries the request.
            cache.delete(pluginConfigurationId);
            return of(false);
          }),
          shareReplay(1)
        )
      );
    }

    return cache.get(pluginConfigurationId);
  };
}

function isModifiable$(configService: ConfigService, file: DocumentenApiRelatedFile): Observable<boolean> {
  return configService
    .getFeatureToggleObservable('enablePbacDocumentenApiDocuments')
    .pipe(map(pbacEnabled => (pbacEnabled ? file.canModify !== false : true)));
}

// The documents component is route-bound, but this action item is built once at the root
// injector, so the case document's ID is read from the live router state (walking to the current
// leaf route) rather than through an injected ActivatedRoute, which would resolve to the root
// route instead of the active case route.
function getCurrentCaseDocumentId(router: Router): string | null {
  let route = router.routerState.snapshot.root;
  while (route.firstChild) {
    route = route.firstChild;
  }
  return route.paramMap.get('documentId');
}

export function createDocumentenApiWopiActionItem(
  wopiService: DocumentenApiWopiService,
  configService: ConfigService,
  router: Router
): ActionItem {
  const hasWopiSupport$ = createHasWopiSupport$(wopiService);

  return {
    label: 'documentenApiWopi.editContent',
    type: 'normal',
    hidden$: (file: DocumentenApiRelatedFile) =>
      hasWopiSupport$(file?.pluginConfigurationId).pipe(map(supported => !supported)),
    disabled$: (file: DocumentenApiRelatedFile) =>
      combineLatest([hasWopiSupport$(file?.pluginConfigurationId), isModifiable$(configService, file)]).pipe(
        map(([supported, modifiable]) => !supported || !modifiable)
      ),
    callback: (file: DocumentenApiRelatedFile) => {
      const caseDocumentId = getCurrentCaseDocumentId(router);
      if (!caseDocumentId) {
        return;
      }

      // Open the tab synchronously so the browser still associates it with this click's user
      // activation; the URL is filled in once the backend responds. Navigating there directly
      // (rather than fetching the WOPI host's HTML ourselves and rendering it via a blob: URL)
      // keeps that markup on the WOPI host's own origin instead of ours.
      const wopiTab = window.open('', '_blank');
      if (wopiTab) {
        // Prevent the WOPI host page (which may be on an external origin) from using
        // window.opener to navigate this application tab.
        wopiTab.opener = null;
      }

      wopiService
        .getWopiHostPage(file.pluginConfigurationId, caseDocumentId, file.fileId)
        .pipe(take(1))
        .subscribe({
          next: ({url}) => {
            if (wopiTab) {
              wopiTab.location.href = url;
            }
          },
          error: () => {
            wopiTab?.close();
          },
        });
    },
  };
}
