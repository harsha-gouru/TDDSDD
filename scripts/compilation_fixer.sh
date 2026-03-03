#!/bin/bash
# ============================================================================
# compilation_fixer.sh — Level 2: Dynamic Compilation Fixer
# ============================================================================
# Iteratively fixes compilation errors and test failures.
# Phase 1: mvn compile → fix → repeat (max iterations)
# Phase 2: mvn test → fix → repeat (max iterations)
# ============================================================================

source "$(dirname "$0")/common.sh"

MAX_COMPILE_ITERATIONS="${MAX_COMPILE_ITERATIONS:-5}"
MAX_TEST_ITERATIONS="${MAX_TEST_ITERATIONS:-5}"

log_step "═══════════════════════════════════════════════════════════"
log_step "  COMPILATION FIXER (Level 2)"
log_step "  Compile → fix errors → repeat until green"
log_step "  Max compile iterations: $MAX_COMPILE_ITERATIONS"
log_step "  Max test iterations: $MAX_TEST_ITERATIONS"
log_step "═══════════════════════════════════════════════════════════"

CONSTITUTION_TEXT=$(read_constitution)

# ── Gather all generated files for context ───────────────────────────────────
gather_generated_context() {
    local context=""
    while IFS= read -r file; do
        [ -z "$file" ] && continue
        context+="
// === FILE: $file ===
$(cat "$file")
"
    done < <(list_generated_files)
    echo "$context"
}

# ══════════════════════════════════════════════════════════════════════════════
# PHASE 1: Fix compilation errors
# ══════════════════════════════════════════════════════════════════════════════
log_step "Phase 1: Fixing compilation errors..."

for i in $(seq 1 "$MAX_COMPILE_ITERATIONS"); do
    log_info "Compilation attempt $i/$MAX_COMPILE_ITERATIONS"
    
    COMPILE_OUTPUT=$(check_compilation 2>&1)
    if [ $? -eq 0 ]; then
        log_success "Compilation passed on attempt $i!"
        break
    fi
    
    log_warn "Compilation failed — feeding errors to agent..."
    
    GENERATED_CONTEXT=$(gather_generated_context)
    CONTRACT_CONTEXT=$(read_contract_files)
    
    FIX_PROMPT="You are a Java Spring Boot expert. Fix the compilation errors below.

RULES:
$CONSTITUTION_TEXT

COMPILATION ERRORS:
$COMPILE_OUTPUT

CURRENT GENERATED FILES (these are the files YOU can fix):
$GENERATED_CONTEXT

CONTRACT FILES (these are READ-ONLY, do NOT modify):
$CONTRACT_CONTEXT

Instructions:
- Fix ONLY the generated files (*Impl, *Controller, GlobalExceptionHandler)
- Do NOT modify contract files (DTOs, interfaces, entities, repositories, mappers, enums, exceptions)
- Fix all compilation errors
- Maintain the existing logic while fixing type/import/signature issues

Output ONLY the corrected files in this EXACT format:
// FILE: src/main/java/com/tddsdd/<package>/<ClassName>.java
\`\`\`java
// ... corrected code ...
\`\`\`"

    FIX_OUTPUT=$(claude_prompt "$FIX_PROMPT")
    extract_and_write_java "$FIX_OUTPUT"
    echo "$FIX_OUTPUT" > "$RESULTS_DIR/compile_fix_${i}_output.txt"
    
    if [ "$i" -eq "$MAX_COMPILE_ITERATIONS" ]; then
        log_error "Max compile iterations reached ($MAX_COMPILE_ITERATIONS). Check errors manually."
    fi
done

# ══════════════════════════════════════════════════════════════════════════════
# PHASE 2: Fix test failures
# ══════════════════════════════════════════════════════════════════════════════
log_step "Phase 2: Fixing test failures..."

for i in $(seq 1 "$MAX_TEST_ITERATIONS"); do
    log_info "Test attempt $i/$MAX_TEST_ITERATIONS"
    
    TEST_OUTPUT=$(check_tests 2>&1)
    if [ $? -eq 0 ]; then
        log_success "All tests passed on attempt $i!"
        break
    fi
    
    log_warn "Tests failed — feeding failures to agent..."
    
    # Extract just the failure info (not the full Maven output)
    FAILURE_SUMMARY=$(echo "$TEST_OUTPUT" | grep -A5 -E "FAIL|ERROR|AssertionError|Expected|Unexpected|assert" | head -80)
    
    GENERATED_CONTEXT=$(gather_generated_context)
    
    # Get test files for context
    TEST_CONTEXT=$(read_test_files)
    CONTRACT_CONTEXT=$(read_contract_files)
    SPEC_CONTEXT=$(read_specs)
    
    FIX_PROMPT="You are a Java Spring Boot expert. Fix the test failures below.

RULES:
$CONSTITUTION_TEXT

TEST FAILURES:
$TEST_OUTPUT

FAILURE SUMMARY:
$FAILURE_SUMMARY

CURRENT IMPLEMENTATION FILES (fix these):
$GENERATED_CONTEXT

TEST FILES (these are READ-ONLY, your code must pass these):
$TEST_CONTEXT

CONTRACT FILES (READ-ONLY):
$CONTRACT_CONTEXT

SPECIFICATIONS:
$SPEC_CONTEXT

Instructions:
- Fix ONLY the implementation files (*Impl, *Controller, GlobalExceptionHandler)
- Do NOT modify test files or contract files
- Read the test expectations carefully — match exact method signatures, return types, exception types
- Focus on making ALL tests pass

Output ONLY the corrected files:
// FILE: src/main/java/com/tddsdd/<package>/<ClassName>.java
\`\`\`java
// ... corrected code ...
\`\`\`"

    FIX_OUTPUT=$(claude_prompt "$FIX_PROMPT")
    extract_and_write_java "$FIX_OUTPUT"
    echo "$FIX_OUTPUT" > "$RESULTS_DIR/test_fix_${i}_output.txt"
    
    if [ "$i" -eq "$MAX_TEST_ITERATIONS" ]; then
        log_error "Max test iterations reached ($MAX_TEST_ITERATIONS). Check failures manually."
    fi
done

log_step "Compilation fixer complete."
