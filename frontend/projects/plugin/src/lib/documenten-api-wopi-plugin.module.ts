/*
 * Copyright 2015-2025 Ritense BV, the Netherlands.
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

import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {DocumentenApiWopiConfigurationComponent} from './components/documenten-api-wopi-configuration/documenten-api-wopi-configuration.component';
import {PluginTranslatePipeModule} from '@valtimo/plugin';
import {FormModule, InputModule, SelectModule} from '@valtimo/components';
import {ConfigService} from '@valtimo/shared';
import {DOCUMENT_ACTION_ITEM} from '@valtimo/zgw';
import {createDocumentenApiWopiActionItem} from './providers/documenten-api-wopi-action-item.provider';
import {DocumentenApiWopiService} from './services';

@NgModule({
  declarations: [DocumentenApiWopiConfigurationComponent],
  imports: [CommonModule, PluginTranslatePipeModule, FormModule, InputModule, SelectModule],
  exports: [DocumentenApiWopiConfigurationComponent],
  providers: [
    {
      provide: DOCUMENT_ACTION_ITEM,
      useFactory: createDocumentenApiWopiActionItem,
      deps: [DocumentenApiWopiService, ConfigService, Router],
      multi: true,
    },
  ],
})
export class DocumentenApiWopiPluginModule {
  constructor(private readonly translateService: TranslateService) {
    // The case document list renders this plugin's action item through the standard `translate`
    // pipe, so its label needs to live in the global catalog rather than this plugin's own
    // pluginTranslations (which only back the plugin-configuration screen).
    this.translateService.setTranslation('en', {documentenApiWopi: {editContent: 'Edit content'}}, true);
    this.translateService.setTranslation(
      'nl',
      {documentenApiWopi: {editContent: 'Inhoud bewerken'}},
      true
    );
  }
}
