# Plugin-documentatie

<!-- Gebruik deze pagina om je plugin te documenteren. Hieronder staat een voorgestelde structuur. -->

## Overzicht

Dit is een voorbeeldplugin die een API-call-actie demonstreert. De plugin haalt gegevens op bij een endpoint van een
tijd-API.

## Afhankelijkheden

### Backend

```kotlin
dependencies {
    implementation("com.ritense.valtimoplugins:sample-plugin:0.0.1")
}
```

### Frontend

```json
{
  "dependencies": {
    "@valtimo-plugins/sample-plugin": "0.0.1"
  }
}
```

In je `app.module.ts`:

```typescript
import {
    SamplePluginModule, samplePluginSpecification,
} from '@valtimo-plugins/sample-plugin';

@NgModule({
    imports: [
        SamplePluginModule,
    ],
    providers: [
        {
            provide: PLUGIN_TOKEN,
            useValue: [
                samplePluginSpecification,
            ]
        }
    ]
})
```

## Configuratie

Beschrijf hier de configuratie-eigenschappen van de plugin en hoe je deze instelt.

| Eigenschap | Type   | Verplicht | Omschrijving                              |
|------------|--------|-----------|-------------------------------------------|
| apiUrl     | string | Ja        | De URL van de tijd-API die wordt aangeroepen |

## Acties

### Testactie tijd-API

Stuurt een GET-request naar de geconfigureerde API-URL en geeft de tijdzone-response terug.

| Parameter | Type | Verplicht | Omschrijving |
|-----------|------|-----------|--------------|
|           |      |           |              |

## Gebruik

Leg uit hoe de plugin in een proces gebruikt wordt, met voorbeelden indien van toepassing.
