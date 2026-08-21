# Voorbeeldapplicatie

Dit project bevat ook een werkende voorbeeldapplicatie die bedoeld is om de plugin te demonstreren.

## De voorbeeldapplicatie draaien

Alle onderstaande commando's moeten worden uitgevoerd vanuit de **root van het project**.

### Vereisten

- Java 21
- [Docker (Desktop)](https://www.docker.com/products/docker-desktop/)

### Docker starten

Zorg ervoor dat Docker draait.

Starten met het gradle-script:

```shell
./gradlew :backend:app:composeUp
```

### Backend starten

Met het gradle-script:

```shell
./gradlew :backend:app:bootRun
```

### Frontend starten

```shell
nvm use 20
npm run clean
npm install
npm run build
npm start
```

### Keycloak-gebruikers

De voorbeeldapplicatie heeft een aantal testgebruikers die al zijn geconfigureerd.

| Naam         | Rol            | Gebruikersnaam | Wachtwoord |
|--------------|----------------|----------------|------------|
| James Vance  | ROLE_USER      | user           | user       |
| Asha Miller  | ROLE_ADMIN     | admin          | admin      |
| Morgan Finch | ROLE_DEVELOPER | developer      | developer  |

## Broncode

De broncode is opgesplitst in twee modules:

1. [Frontend](/frontend)
2. [Backend](/backend)
