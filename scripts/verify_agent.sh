#!/bin/bash
# ============================================================================
# verify_agent.sh — Level 3: Verification Agent
# ============================================================================
# A separate tool-enabled agent that:
# 1. Reads all generated implementation files
# 2. Runs mvn test to verify
# 3. Reviews code quality (patterns, naming, null checks)
# 4. Produces a quality report
# ============================================================================

source "$(dirname "$0")/common.sh"

log_step "═══════════════════════════════════════════════════════════"
log_step "  VERIFICATION AGENT (Level 3)"
log_step "  Code review + quality check"
log_step "═══════════════════════════════════════════════════════════"

QUALITY_REPORT="$RESULTS_DIR/quality_report.txt"

# ── System prompt for verifier ───────────────────────────────────────────────
VERIFY_SYSTEM="You are a Senior Java Code Reviewer for the project at $PROJECT_ROOT.
You have READ-ONLY access to the entire project. You CANNOT modify any files.
Your job is to evaluate the quality of generated code.

Read the constitution at $CONSTITUTION for project rules."

# ── Task prompt ──────────────────────────────────────────────────────────────
GENERATED_FILES_LIST=$(list_generated_files | tr '\n' ', ')

VERIFY_TASK="Review the generated implementation files and produce a quality report.

GENERATED FILES TO REVIEW:
$GENERATED_FILES_LIST

STEPS:
1. Read each generated file listed above
2. Read the corresponding test file for each
3. Read the specification files in $SPECS_DIR/
4. Run 'mvn test' to verify all tests pass
5. Check for these quality issues:
   - Missing null checks
   - Incorrect exception handling
   - Missing @Transactional where needed
   - Hardcoded values that should be constants
   - Missing input validation
   - Unused imports
   - Code duplication
   - Breaking the Single Responsibility Principle

OUTPUT FORMAT:
Produce a structured quality report in this exact format:

## Test Results
- Total tests: [number]
- Passed: [number] 
- Failed: [number]
- Build status: [SUCCESS/FAILURE]

## File Reviews

### [FileName.java]
- **Quality Score**: [1-10]
- **Issues Found**: [list each issue]
- **Strengths**: [what's done well]

## Overall Assessment
- **Overall Score**: [1-10]
- **Critical Issues**: [list any blockers]
- **Recommendations**: [improvements for next iteration]"

log_agent "Running verification agent..."
VERIFY_OUTPUT=$(claude_agent "$VERIFY_SYSTEM" "$VERIFY_TASK")
echo "$VERIFY_OUTPUT" > "$QUALITY_REPORT"

# ── Parse and display summary ────────────────────────────────────────────────
echo ""
log_step "Quality Report Summary:"
echo ""

# Extract key metrics from the report
if echo "$VERIFY_OUTPUT" | grep -q "Overall Score"; then
    OVERALL_SCORE=$(echo "$VERIFY_OUTPUT" | grep -oE 'Overall Score.*[0-9]+/10' | head -1)
    [ -n "$OVERALL_SCORE" ] && log_info "  $OVERALL_SCORE"
fi

if echo "$VERIFY_OUTPUT" | grep -q "Build status.*SUCCESS"; then
    log_success "Build: SUCCESS"
elif echo "$VERIFY_OUTPUT" | grep -q "Build status.*FAILURE"; then
    log_error "Build: FAILURE"
fi

# Count critical issues
CRITICAL_COUNT=$(echo "$VERIFY_OUTPUT" | grep -ci "critical" || echo "0")
if [ "$CRITICAL_COUNT" -gt 0 ]; then
    log_warn "Critical issues mentioned: $CRITICAL_COUNT"
else
    log_success "No critical issues found"
fi

echo ""
log_info "Full quality report: $QUALITY_REPORT"
log_step "Verification complete."
