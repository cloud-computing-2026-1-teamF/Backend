# Backend Engineering Guide

## Core Architecture

Always keep this flow:

`RestController -> Facade -> Service -> Repository (JPA or Querydsl) -> Entity`

## DTO and Mapping Policy

- Never expose entities outside the service layer.
- Service layer must convert entities to response/request DTOs via MapStruct.
- Facade layer should only consume/return DTOs, never entities.

## Engineering Quality Standards

- Maintain strict, production-grade code quality and readability.
- After any backend code change, `./gradlew build` must succeed before reporting completion.
- Enforce consistent API response envelope and error response schema.
- Keep exception handling centralized and predictable.
- Follow the API baseline contract first; implementation must not drift from spec.
- Use Liquibase for all schema changes and seed data.
- Keep Docker assets maintained for deploy-ready local/prod workflows.
- Write code with long-term maintainability standards expected from senior backend engineering.

## Code Convention Checklist

- Follow `.editorconfig` as the single source of formatting truth.
- Kotlin code uses 4-space indentation, no tabs, and LF line endings.
- Keep one model per file for request/response/dto classes.
- Keep API models split by package:
  - `controller/request/*`
  - `controller/response/*`
  - `dto/*` (internal transfer models)
- Use explicit generic response types in controllers (avoid wildcard like `ApiResponse<*>`).
- Controller layer must only handle HTTP contracts, and map to response models.
- Facade/Service layers must avoid framework-specific request/response objects.
- Keep endpoint naming consistent with resource semantics and kebab-case for subresources.
- Never hardcode error messages/codes in controller/facade/service layers.
- All business errors must be raised via centralized error catalog enums in `common/error`.

## Commit Policy

- Agent is allowed to create Git commits without additional permission.
