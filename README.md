# GZAC Documenten API WOPI plugin

De Documenten API WOPI plugin kan in samenwerking met de Documenten API plugin worden gebruikt voor het openen en 
bewerken van documenten in de web-browser.

## Running the application

This repository includes a preconfigured development environment (Valtimo, the CG-DMF DRC and Collabora Online as the
WOPI client) so the plugin can be tested without any manual setup.

Prerequisites: Java 21, Node.js >= 20, Docker & Docker Compose.

```shell
./gradlew :backend:app:composeUp
./gradlew :backend:app:bootRun
```

Then, in a separate terminal, start the frontend:

```shell
cd frontend
npm install
npm run libs-build-all
npm start
```

Log in at the frontend with one of the preconfigured test users (e.g. `user` / `user`), open a case with a document,
and use the "Documenten API WOPI plugin" action to open it for editing in Collabora Online.

See [documentation/getting-started.md](documentation/getting-started.md) and
[documentation/plugin.md](documentation/plugin.md) for more details.

## Contact

-- Maurits van Beusekom ([Baseflow](https://baseflow.com))
