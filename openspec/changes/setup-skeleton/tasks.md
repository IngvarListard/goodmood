## 1. Project Setup

- [ ] 1.1 Create deps.edn with dependencies (ring, ring-jetty-adapter, reitit, integrant)
- [ ] 1.2 Create directory structure (src/app/)

## 2. Integrant System

- [ ] 2.1 Create app.system namespace with integrant system configuration
- [ ] 2.2 Implement :app.core/server component with init-key and halt-key!

## 3. HTTP Server

- [ ] 3.1 Create Ring handler with health-check endpoint (GET / → 200 OK)
- [ ] 3.2 Configure Jetty adapter with system component

## 4. Routing

- [ ] 4.1 Create app.routes namespace with reitit router
- [ ] 4.2 Add health-check route to router

## 5. Application Entry Point

- [ ] 5.1 Create app.core namespace with main function
- [ ] 5.2 Implement system initialization and halt logic

## 6. Verification

- [ ] 6.1 Start application and verify GET / returns 200
- [ ] 6.2 Test graceful shutdown via integrant.core/halt!
- [ ] 6.3 Update AGENTS.md with project run instructions
