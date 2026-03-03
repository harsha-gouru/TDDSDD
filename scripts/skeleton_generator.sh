#!/bin/bash
# ============================================================================
# skeleton_generator.sh — Level 2: Dynamic Skeleton Generator
# ============================================================================
# Reads the discovery manifest to determine which classes need skeletons.
# Generates compilable stubs for all missing *Impl, *Controller, and
# GlobalExceptionHandler classes.
# ============================================================================

source "$(dirname "$0")/common.sh"
source "$(dirname "$0")/discover.sh"

log_step "═══════════════════════════════════════════════════════════"
log_step "  SKELETON GENERATOR (Level 2)"
log_step "  Parsing tests → generating compilable stubs"
log_step "═══════════════════════════════════════════════════════════"

# ── Read manifest ────────────────────────────────────────────────────────────
MISSING_IMPLS=$(read_manifest MISSING_IMPLS)
MISSING_CONTROLLERS=$(read_manifest MISSING_CONTROLLERS)
NEED_HANDLER=$(read_manifest NEED_EXCEPTION_HANDLER)

# Build list of classes to generate
CLASSES_TO_GENERATE=""
if [ -n "$MISSING_IMPLS" ]; then
    CLASSES_TO_GENERATE+="Service implementations: $MISSING_IMPLS"$'\n'
fi
if [ -n "$MISSING_CONTROLLERS" ]; then
    CLASSES_TO_GENERATE+="Controllers: $MISSING_CONTROLLERS"$'\n'
fi
if [ "$NEED_HANDLER" = "true" ]; then
    CLASSES_TO_GENERATE+="Exception handler: GlobalExceptionHandler"$'\n'
fi

if [ -z "$CLASSES_TO_GENERATE" ]; then
    log_success "No skeletons needed — all classes exist."
    exit 0
fi

log_info "Need to generate skeletons for:"
echo "$CLASSES_TO_GENERATE" | while IFS= read -r line; do
    [ -n "$line" ] && log_info "  $line"
done

# ── Gather context ───────────────────────────────────────────────────────────
TEST_FILES=$(read_test_files)
CONTRACT_FILES=$(read_contract_files)
CONSTITUTION_TEXT=$(read_constitution)

# ── Call Claude to generate skeletons ────────────────────────────────────────
log_agent "Generating skeletons from test files..."

SKELETON_PROMPT="You are a Java Spring Boot expert. Generate MINIMAL SKELETON files that will COMPILE but contain no real logic.

RULES:
$CONSTITUTION_TEXT

You need to generate skeleton Java files for these classes:
$CLASSES_TO_GENERATE

Here are the TEST FILES that reference these classes:
$TEST_FILES

Here are the EXISTING CONTRACT FILES (use these types, do NOT recreate them):
$CONTRACT_FILES

For each missing class, generate a MINIMAL compilable skeleton:
- For *Impl classes: implement the interface, inject dependencies via constructor, return null/empty/0/false from methods
- For *Controller classes: annotate with @RestController, inject service, create endpoint methods that return null
- For GlobalExceptionHandler: annotate with @RestControllerAdvice, add @ExceptionHandler methods that return ResponseEntity

CRITICAL:
- Every method signature MUST match what the tests expect
- Constructor parameters MUST match @Mock fields in the test
- Annotations MUST be present (@Service, @RestController, @RestControllerAdvice, etc.)
- Return types MUST match interface definitions
- The goal is COMPILATION, not correctness

Output each file in this EXACT format (one per class):
// FILE: src/main/java/com/tddsdd/<package>/<ClassName>.java
\`\`\`java
package com.tddsdd.<package>;
// ... skeleton code ...
\`\`\`"

SKELETON_OUTPUT=$(claude_prompt "$SKELETON_PROMPT")
log_info "Extracting and writing skeleton files..."
extract_and_write_java "$SKELETON_OUTPUT"
echo "$SKELETON_OUTPUT" > "$RESULTS_DIR/skeleton_output.txt"

# ── Verify compilation ───────────────────────────────────────────────────────
log_step "Verifying skeleton compilation..."
COMPILE_ERRORS=$(check_compilation 2>&1) || {
    log_warn "Skeleton compilation had issues — will attempt to fix"
    echo "$COMPILE_ERRORS" > "$RESULTS_DIR/skeleton_compile_errors.txt"
    
    # One retry with error feedback
    log_agent "Fixing skeleton compilation errors..."
    FIX_PROMPT="Fix these Java compilation errors. The files MUST compile.

ERRORS:
$COMPILE_ERRORS

EXISTING CONTRACT FILES:
$CONTRACT_FILES

Fix the skeleton files and output ONLY the corrected files in this format:
// FILE: src/main/java/com/tddsdd/<package>/<ClassName>.java
\`\`\`java
// ... corrected code ...
\`\`\`"
    
    FIX_OUTPUT=$(claude_prompt "$FIX_PROMPT")
    extract_and_write_java "$FIX_OUTPUT"
    
    check_compilation 2>&1 || {
        log_warn "Skeletons still have issues — compilation_fixer will handle it"
    }
}

log_success "Skeletons compile successfully!"
log_info "Tests will FAIL (expected — skeletons have no logic yet)"
log_step "Skeleton generation complete."
