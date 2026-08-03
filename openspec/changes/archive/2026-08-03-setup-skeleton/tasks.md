## 1. Project Setup

- [x] 1.1 Create deps.edn with dependencies (ring, ring-jetty-adapter, reitit, integrant)
- [x] 1.2 Create directory structure (src/app/)

## 2. Integrant System

- [x] 2.1 Create app.system namespace with integrant system configuration
- [x] 2.2 Implement :app.core/server component with init-key and halt-key!

## 3. HTTP Server

- [x] 3.1 Create Ring handler with health-check endpoint (GET / → 200 OK)
- [x] 3.2 Configure Jetty adapter with system component

## 4. Routing

- [x] 4.1 Create app.routes namespace with reitit router
- [x] 4.2 Add health-check route to router

## 5. Application Entry Point

- [x] 5.1 Create app.core namespace with main function
- [x] 5.2 Implement system initialization and halt logic

## 6. Verification

- [x] 6.1 Start application and verify GET / returns 200
- [x] 6.2 Test graceful shutdown via integrant.core/halt!
- [x] 6.3 Update AGENTS.md with project run instructions

## 7. Fixes after verification

fix warning and apply suggestion bellow below:
WARNING (1):
- [x] 1. Scenario "Server starts within time limit" — no explicit timing verification. Proposal requires ≤3 seconds startup. Recommendation: add timing check in verification task or accept manual verification.
SUGGESTION (2):
- [x] 1. deps.edn has outdated versions (you asked about this earlier) — reitit 0.10.1, integrant 1.0.1, ring 1.15.5 are available
- [x] 2. routes.clj:15-16 — app function wraps router; could use router directly since it's already a Ring handler