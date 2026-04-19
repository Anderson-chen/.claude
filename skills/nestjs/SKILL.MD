---
name: nestjs
description: "Use this skill when building NestJS APIs, modules, controllers, services, guards, pipes, interceptors, or any NestJS-related features."
---

# NestJS Development Guide

Help the user build NestJS applications following official best practices and patterns.

## Standard Architecture Flow

Every API feature starts by generating a resource:

1. 執行CLI指令
```bash
nest g resource [name]
```
2. 執行ESLINT發現問題後修正,需判斷是否為業務尚未決定的警告,最多重試2次，
```bash
npm run lint
```

3. 執行formatter
```bash
npm run format
```
請確保1~3都有執行

## File Naming Conventions

| Type        | File Name                  | Class Name        |
|-------------|----------------------------|-------------------|
| Module      | `cats.module.ts`           | `CatsModule`      |
| Controller  | `cats.controller.ts`       | `CatsController`  |
| Service     | `cats.service.ts`          | `CatsService`     |
| DTO         | `dto/create-cat.dto.ts`    | `CreateCatDto`    |
| Entity      | `entities/cat.entity.ts`   | `Cat`             |

## CLI Commands

```bash
# Generate a full resource (module + controller + service + dto + entity)
nest g resource cats

# Generate individually
nest g module cats
nest g controller cats
nest g service cats
```

## Key Decorators Reference

| Decorator                            | Usage                         |
|--------------------------------------|-------------------------------|
| `@Module()`                          | Define a module               |
| `@Controller()`                      | Define controller with path   |
| `@Injectable()`                      | Mark as injectable provider   |
| `@Get/Post/Put/Delete/Patch()`       | HTTP method routes            |
| `@Body()`                            | Extract request body          |
| `@Param()`                           | Extract route parameter       |
| `@Query()`                           | Extract query string          |

## Checklist for New Feature

1. [ ] Create DTO(s) for request/response shape
2. [ ] Create Service with business logic
3. [ ] Create Controller with route handlers
4. [ ] Create Module that declares controller + service
5. [ ] Import new Module into `AppModule`

## Common Patterns

**Register module in AppModule:**
```typescript
@Module({
  imports: [CatsModule],
})
export class AppModule {}
```

**Enable validation globally (main.ts):**
```typescript
app.useGlobalPipes(new ValidationPipe());
```
