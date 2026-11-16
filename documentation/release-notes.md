# Release notes

Overzicht van wijzigingen per versie van de GZAC plugin-template.

## 1.1.2

- Het extensiepunt voor document-acties wordt nu aangesproken via `DOCUMENT_ACTION_ITEM_TOKEN`
  uit `@valtimo/zgw` (voorheen `DOCUMENT_ACTION_ITEM`), waardoor de frontend de
  "Inhoud bewerken" actie correct wordt geregistreerd.
- De `@valtimo/*` afhankelijkheden zijn opgehoogd van 13.48.0 naar 13.53.0 — de versie waarin het
  generieke extensiepunt voor document-acties en `ActionItem.hidden$` beschikbaar zijn.

## 1.1.0

- De "Inhoud bewerken" actie wordt nu aangeboden via Valtimo's generieke extensiepunt voor
  document-acties, in plaats van hardcoded in de Valtimo-kern.
- De controle of WOPI-bewerken beschikbaar is, is robuuster gemaakt: een tijdelijke storing wordt
  niet meer verward met "niet geconfigureerd", en de actie blijft bruikbaar zodat de gebruiker het
  opnieuw kan proberen.

## 1.0.0

Eerste opzet
