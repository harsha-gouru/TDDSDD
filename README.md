# TDDSDD — Multi-Agent TDD/SDD Scaffold

Rebuild Spring Boot application code from **specs + tests** using **multiple headless Claude agents** with context isolation.

## How It Works

```
spec.md + tests (pre-written)
         │
         ▼
┌─ rebuild_from_spec.sh ────────────────────────┐
│                                                │
│  Step 1: skeleton_generator.sh (Approach 1)    │
│  Parse tests → generate compilable stubs       │
│                                                │
│  Step 2: layered_builder.sh (Approach 4)       │
│  Fill implementations layer by layer           │
│  (Service → Controller) with parallel agents   │
│                                                │
│  Step 3: compilation_fixer.sh (Approach 2)     │
│  Compile → feed errors → fix → repeat          │
│                                                │
└────────────────────────────────────────────────┘
         │
         ▼
   All tests pass ✅
```

## Prerequisites

- **Java 17+**: `java -version`
- **Maven 3.8+**: `mvn -version`
- **Claude Code CLI**: `npm install -g @anthropic-ai/claude-code`
- **Authenticated**: Run `claude` once to authenticate

## Quick Start

```bash
# Default (uses Sonnet)
./scripts/rebuild_from_spec.sh

# Use Haiku (cheaper/faster)
CLAUDE_MODEL=haiku ./scripts/rebuild_from_spec.sh

# Use Opus (best quality)
CLAUDE_MODEL=opus ./scripts/rebuild_from_spec.sh
```

## Run Individual Steps

```bash
# Just generate skeletons
./scripts/skeleton_generator.sh

# Just build layers (after skeletons exist)
./scripts/layered_builder.sh

# Just fix compilation/test errors
./scripts/compilation_fixer.sh
```

## Project Structure

```
TDDSDD/
├── constitution.md              ← Rules AI agents must follow
├── specs/
│   └── user-management.md       ← Feature specification
├── scripts/
│   ├── common.sh                ← Shared utils, model config
│   ├── skeleton_generator.sh    ← Approach 1: tests → stubs
│   ├── layered_builder.sh       ← Approach 4: layer-by-layer
│   ├── compilation_fixer.sh     ← Approach 2: fix loop
│   └── rebuild_from_spec.sh     ← Master orchestrator
├── src/main/java/com/tddsdd/
│   ├── dto/                     ← CONTRACT (kept)
│   ├── entity/                  ← CONTRACT (kept)
│   ├── repository/              ← CONTRACT (kept)
│   ├── service/UserService.java ← CONTRACT (interface)
│   ├── service/UserServiceImpl  ← 🤖 AI GENERATES
│   ├── controller/              ← 🤖 AI GENERATES
│   ├── exception/               ← CONTRACT + 🤖 GlobalExceptionHandler
│   ├── mapper/                  ← CONTRACT (kept)
│   └── enums/                   ← CONTRACT (kept)
└── src/test/java/com/tddsdd/
    ├── service/UserServiceTest  ← PRE-WRITTEN (14 assertions)
    └── controller/UserControllerTest ← PRE-WRITTEN (7 endpoints)
```

## Key Concepts

| Concept | Description |
|---------|-------------|
| **Contract files** | Types that define the API shape (DTOs, interfaces, entities). Never regenerated. |
| **Implementation files** | Business logic that AI generates (`*Impl`, `*Controller`). |
| **Context isolation** | Each agent gets ONLY the files relevant to its task. |
| **Layered build** | Build bottom-up: DTOs → Repos → Services → Controllers. |
| **Feedback loop** | Compile errors fed back to AI iteratively until fixed. |

## Adapting for Your Projects

1. **Replace the spec**: Write your own `specs/your-feature.md`
2. **Replace the tests**: Add your pre-written tests to `src/test/`
3. **Keep your contracts**: DTOs, interfaces, entities stay — they're the contract
4. **Remove implementations**: Delete the `*Impl` and `*Controller` files
5. **Run**: `./scripts/rebuild_from_spec.sh`

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CLAUDE_MODEL` | `sonnet` | Model to use (`haiku`, `sonnet`, `opus`) |
| `MAX_COMPILE_ITERATIONS` | `5` | Max attempts to fix compilation |
| `MAX_TEST_ITERATIONS` | `5` | Max attempts to fix test failures |
| `CLAUDE_MAX_TURNS` | `10` | Max turns per Claude call |
