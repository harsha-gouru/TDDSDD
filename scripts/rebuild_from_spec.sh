#!/bin/bash
# ============================================================================
# rebuild_from_spec.sh — Level 3 Master Orchestrator
# ============================================================================
# 5-step pipeline with tool-enabled agents:
#   1. Auto-Discovery
#   2. Skeleton Generator
#   3. Layered Builder (tool-enabled agents)
#   4. Compilation Fixer
#   5. Verification Agent (code quality review)
#
# Usage:
#   ./scripts/rebuild_from_spec.sh                           # Auto-discover
#   ./scripts/rebuild_from_spec.sh --spec order-management.md # Target spec
#   ./scripts/rebuild_from_spec.sh --feature Order            # Target feature
#   ./scripts/rebuild_from_spec.sh --all                      # Regenerate all
#   ./scripts/rebuild_from_spec.sh --no-verify                # Skip verification
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"
source "$SCRIPT_DIR/discover.sh"

# ── Parse CLI arguments ──────────────────────────────────────────────────────
MODE="auto"
TARGET_SPEC=""
TARGET_FEATURE=""
SKIP_VERIFY=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --spec)
            MODE="spec"
            TARGET_SPEC="$2"
            shift 2
            ;;
        --feature)
            MODE="feature"
            TARGET_FEATURE="$2"
            shift 2
            ;;
        --all)
            MODE="all"
            shift
            ;;
        --no-verify)
            SKIP_VERIFY=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [--spec <spec.md>] [--feature <Name>] [--all] [--no-verify]"
            echo ""
            echo "Modes:"
            echo "  (default)         Auto-discover missing implementations"
            echo "  --spec <file>     Target one spec file"
            echo "  --feature <name>  Target one feature by name"
            echo "  --all             Force-regenerate ALL implementations"
            echo ""
            echo "Options:"
            echo "  --no-verify       Skip the verification agent (faster)"
            echo ""
            echo "Environment:"
            echo "  CLAUDE_MODEL=haiku    Use cheaper model"
            echo "  CLAUDE_MODEL=opus     Use best quality model"
            exit 0
            ;;
        *)
            log_error "Unknown argument: $1"
            exit 1
            ;;
    esac
done

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║      TDDSDD — Level 3 Tool-Enabled Pipeline                ║"
echo "║      Agents Read Files + Auto-Discovery + Verification     ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
log_info "Model:        $CLAUDE_MODEL"
log_info "Mode:         $MODE"
log_info "Verify:       $([ "$SKIP_VERIFY" = true ] && echo "SKIP" || echo "YES")"
log_info "Project:      $PROJECT_ROOT"
log_info "Results dir:  $RESULTS_DIR"
echo ""

START_TIME=$(date +%s)

# ── Step 0: Prerequisites ────────────────────────────────────────────────────
log_step "Step 0: Checking prerequisites..."

if ! command -v claude &> /dev/null; then
    log_error "Claude Code CLI not found. Install: npm install -g @anthropic-ai/claude-code"
    exit 1
fi

check_claude_auth || exit 1

if ! command -v mvn &> /dev/null; then
    log_error "Maven not found. Install: brew install maven"
    exit 1
fi

if [ ! -d "$SRC_TEST" ]; then
    log_error "No test directory found at $SRC_TEST"
    exit 1
fi

log_success "Prerequisites OK"
echo ""

# ── Step 1: Discovery ────────────────────────────────────────────────────────
TOTAL_STEPS=$( [ "$SKIP_VERIFY" = true ] && echo "4" || echo "5" )

log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_step "  STEP 1/${TOTAL_STEPS}: AUTO-DISCOVERY"
log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

case $MODE in
    "all")
        log_warn "Mode: --all → Deleting all generated implementations..."
        while IFS= read -r file; do
            [ -n "$file" ] && rm -f "$file" && log_info "  Removed: $(basename "$file")"
        done < <(list_generated_files)
        discover_all
        ;;
    "feature")
        log_info "Mode: --feature $TARGET_FEATURE"
        discover_all "$TARGET_FEATURE"
        ;;
    "spec")
        log_info "Mode: --spec $TARGET_SPEC"
        local_feature=$(basename "$TARGET_SPEC" .md | cut -d'-' -f1)
        local_feature="$(tr '[:lower:]' '[:upper:]' <<< "${local_feature:0:1}")${local_feature:1}"
        discover_all "$local_feature"
        ;;
    "auto")
        log_info "Mode: auto-discovery"
        discover_all
        ;;
esac

echo ""

if [ "$(read_manifest NOTHING_TO_DO)" = "true" ]; then
    log_success "All implementations exist and accounted for!"
    log_info "Run with --all to force-regenerate everything."
    exit 0
fi

# ── Step 2: Skeleton Generator ───────────────────────────────────────────────
log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_step "  STEP 2/${TOTAL_STEPS}: SKELETON GENERATOR"
log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
bash "$SCRIPT_DIR/skeleton_generator.sh"
echo ""

# ── Step 3: Layered Builder ──────────────────────────────────────────────────
log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_step "  STEP 3/${TOTAL_STEPS}: LAYERED BUILDER (Tool-Enabled)"
log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
bash "$SCRIPT_DIR/layered_builder.sh"
echo ""

# ── Step 4: Compilation Fixer ────────────────────────────────────────────────
log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log_step "  STEP 4/${TOTAL_STEPS}: COMPILATION FIXER"
log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
bash "$SCRIPT_DIR/compilation_fixer.sh"
echo ""

# ── Step 5: Verification Agent ───────────────────────────────────────────────
if [ "$SKIP_VERIFY" = false ]; then
    log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    log_step "  STEP 5/${TOTAL_STEPS}: VERIFICATION AGENT"
    log_step "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo ""
    bash "$SCRIPT_DIR/verify_agent.sh"
    echo ""
fi

# ── Final Report ─────────────────────────────────────────────────────────────
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║                 LEVEL 3 REBUILD COMPLETE                    ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
log_info "Duration: ${MINUTES}m ${SECONDS}s"
log_info "Model used: $CLAUDE_MODEL"
log_info "Mode: $MODE"
log_info "Verification: $([ "$SKIP_VERIFY" = true ] && echo "SKIPPED" || echo "DONE")"
echo ""

log_info "Generated files:"
while IFS= read -r f; do
    [ -n "$f" ] && log_success "  ✅ $(basename "$f")"
done < <(list_generated_files)

echo ""
log_info "Agent outputs saved in: $RESULTS_DIR/"
[ -f "$RESULTS_DIR/quality_report.txt" ] && log_info "Quality report: $RESULTS_DIR/quality_report.txt"
echo ""

log_step "Final verification: mvn test"
cd "$PROJECT_ROOT" && mvn test 2>&1 | tail -20
