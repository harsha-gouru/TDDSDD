#!/bin/bash
# ============================================================================
# layered_builder.sh — Level 4: Parallel Fan-Out Layered Builder
# ============================================================================
# Tool-enabled agents with smart parallelism:
#   Layer 3A: Independent service impls (parallel) — e.g., User + Product
#   Layer 3B: Dependent service impls (sequential) — e.g., Order (depends on User)
#   Layer 4:  All controllers + GlobalExceptionHandler (parallel)
# ============================================================================

source "$(dirname "$0")/common.sh"
source "$(dirname "$0")/discover.sh"

log_step "═══════════════════════════════════════════════════════════"
log_step "  LAYERED BUILDER (Level 4 — Parallel Fan-Out)"
log_step "  Independent services parallel, dependent sequential"
log_step "═══════════════════════════════════════════════════════════"

# ── System prompt for all agents ─────────────────────────────────────────────
SYSTEM_PROMPT="You are a Spring Boot Java expert working on the project at $PROJECT_ROOT.

ABSOLUTE RULES:
- Read $CONSTITUTION for the full constitution (code rules)
- Read spec files in $SPECS_DIR/ for feature specifications
- You can ONLY generate *Impl, *Controller, and GlobalExceptionHandler classes
- You CANNOT modify contracts (DTOs, entities, interfaces, repositories, mappers, enums, exceptions)
- You CANNOT modify test files
- You CAN run 'mvn compile -q' to verify your code compiles
- Constructor injection ONLY (no @Autowired)
- Follow the test expectations EXACTLY

OUTPUT FORMAT:
Output ONLY the complete file(s) in this format:
// FILE: src/main/java/com/tddsdd/<package>/<ClassName>.java
\`\`\`java
package com.tddsdd.<package>;
// ... full implementation ...
\`\`\`"

# ── Read manifest ────────────────────────────────────────────────────────────
MISSING_IMPLS=$(read_manifest MISSING_IMPLS)
MISSING_CONTROLLERS=$(read_manifest MISSING_CONTROLLERS)
NEED_HANDLER=$(read_manifest NEED_EXCEPTION_HANDLER)
DEP_GRAPH=$(read_manifest DEP_GRAPH)

# ── Classify services as independent vs dependent ────────────────────────────
classify_service() {
    local impl_name="$1"
    local deps=""
    
    # Extract deps from the dep graph
    while IFS= read -r entry; do
        [ -z "$entry" ] && continue
        local entry_impl="${entry%%:*}"
        if [ "$entry_impl" = "$impl_name" ]; then
            deps="${entry#*:}"
            break
        fi
    done <<< "$(echo "$DEP_GRAPH" | tr '|' '\n')"
    
    # Check if any dependency is also a missing impl (i.e., needs to be built first)
    if [ -n "$deps" ]; then
        IFS=',' read -ra dep_arr <<< "$deps"
        for dep in "${dep_arr[@]}"; do
            dep=$(echo "$dep" | tr -d ' ')
            [ -z "$dep" ] && continue
            # Check if this dependency's Impl is in the missing list
            if echo "$MISSING_IMPLS" | grep -q "${dep}Impl" 2>/dev/null; then
                echo "dependent"
                return
            fi
        done
    fi
    
    echo "independent"
}

# ── Generate a service impl (used by both parallel and sequential paths) ─────
generate_service_impl() {
    local impl_name="$1"
    local local_interface_name="${impl_name%Impl}"
    local local_test_file
    local_test_file=$(find "$SRC_TEST" -name "*.java" -type f -exec grep -l "$impl_name" {} \; 2>/dev/null | head -1)
    local local_interface_file
    local_interface_file=$(find "$SRC_MAIN" -name "${local_interface_name}.java" -path "*/service/*" -type f 2>/dev/null | head -1)
    local local_feature_name
    local_feature_name=$(echo "$impl_name" | sed -E 's/(Service|Controller)Impl$//')
    
    if [ -z "$local_test_file" ]; then
        log_warn "No test file found for $impl_name — skipping"
        return 1
    fi
    
    local IMPL_TASK="Implement $impl_name class for the Spring Boot project at $PROJECT_ROOT.

YOUR TASK:
1. Read the test file: $local_test_file
2. Read the service interface: $local_interface_file
3. Read the spec: $(find_spec_for_feature "$local_feature_name" | head -1)
4. Read any contract files imported by the test (DTOs, entities, repos, mappers, exceptions)
5. Read any service interfaces this impl depends on (check @Mock fields in test)
6. Generate a complete implementation that passes ALL tests

CLASS REQUIREMENTS:
- Package: com.tddsdd.service
- Annotate with @Service  
- Implement $local_interface_name interface
- Use constructor injection for all dependencies (match @Mock fields in the test)
- Run 'mvn compile -q' to verify your code compiles

Output the complete file:
// FILE: src/main/java/com/tddsdd/service/${impl_name}.java
\`\`\`java
// ... full implementation ...
\`\`\`"

    local output
    output=$(claude_agent "$SYSTEM_PROMPT" "$IMPL_TASK")
    extract_and_write_java "$output"
    echo "$output" > "$RESULTS_DIR/layer3_${impl_name}_output.txt"
}

# Export for subshells
export -f generate_service_impl classify_service
export SYSTEM_PROMPT DEP_GRAPH

# ══════════════════════════════════════════════════════════════════════════════
# LAYER 3: Service Implementations (with parallel fan-out)
# ══════════════════════════════════════════════════════════════════════════════
if [ -n "$MISSING_IMPLS" ]; then
    log_step "Layer 3: Service Implementations"
    
    IFS=',' read -ra IMPL_LIST <<< "$MISSING_IMPLS"
    
    # Split into independent and dependent
    INDEPENDENT_IMPLS=()
    DEPENDENT_IMPLS=()
    
    for impl_name in "${IMPL_LIST[@]}"; do
        impl_name=$(echo "$impl_name" | tr -d ' ')
        [ -z "$impl_name" ] && continue
        
        local_class=$(classify_service "$impl_name")
        if [ "$local_class" = "independent" ]; then
            INDEPENDENT_IMPLS+=("$impl_name")
        else
            DEPENDENT_IMPLS+=("$impl_name")
        fi
    done
    
    log_info "Independent (parallel): ${INDEPENDENT_IMPLS[*]:-none}"
    log_info "Dependent (sequential): ${DEPENDENT_IMPLS[*]:-none}"
    
    # ── Layer 3A: Independent services in PARALLEL ────────────────────────────
    if [ ${#INDEPENDENT_IMPLS[@]} -gt 0 ]; then
        log_step "Layer 3A: Independent services (PARALLEL — ${#INDEPENDENT_IMPLS[@]} agents)"
        
        IMPL_PIDS=()
        IMPL_PID_NAMES=()
        
        for impl_name in "${INDEPENDENT_IMPLS[@]}"; do
            log_info "Generating $impl_name (parallel, tool-enabled)..."
            
            # Run in background subshell
            (
                generate_service_impl "$impl_name"
            ) &
            IMPL_PIDS+=($!)
            IMPL_PID_NAMES+=("$impl_name")
        done
        
        log_info "Waiting for ${#IMPL_PIDS[@]} parallel service agents..."
        for i in "${!IMPL_PIDS[@]}"; do
            wait "${IMPL_PIDS[$i]}" || log_warn "${IMPL_PID_NAMES[$i]} agent had issues"
            log_success "${IMPL_PID_NAMES[$i]} complete"
        done
        
        # Extract output from saved files
        for impl_name in "${INDEPENDENT_IMPLS[@]}"; do
            local_output_file="$RESULTS_DIR/layer3_${impl_name}_output.txt"
            if [ -f "$local_output_file" ]; then
                extract_and_write_java "$(cat "$local_output_file")"
            fi
        done
        
        log_info "Verifying Layer 3A compilation..."
        check_compilation 2>&1 || log_warn "Layer 3A compilation issues — fixer will handle"
    fi
    
    # ── Layer 3B: Dependent services SEQUENTIALLY ─────────────────────────────
    if [ ${#DEPENDENT_IMPLS[@]} -gt 0 ]; then
        log_step "Layer 3B: Dependent services (SEQUENTIAL)"
        
        for impl_name in "${DEPENDENT_IMPLS[@]}"; do
            log_info "Generating $impl_name (sequential, tool-enabled)..."
            generate_service_impl "$impl_name"
        done
        
        log_info "Verifying Layer 3B compilation..."
        check_compilation 2>&1 || log_warn "Layer 3B compilation issues — fixer will handle"
    fi
else
    log_info "Layer 3: No service implementations to generate"
fi

# ══════════════════════════════════════════════════════════════════════════════
# LAYER 4: Controllers + Exception Handler (all parallel)
# ══════════════════════════════════════════════════════════════════════════════
log_step "Layer 4: Controllers + Exception Handler (all parallel)"

PIDS=()
PID_NAMES=()

if [ -n "$MISSING_CONTROLLERS" ]; then
    IFS=',' read -ra CTRL_LIST <<< "$MISSING_CONTROLLERS"
    for ctrl_name in "${CTRL_LIST[@]}"; do
        ctrl_name=$(echo "$ctrl_name" | tr -d ' ')
        [ -z "$ctrl_name" ] && continue
        
        log_info "Generating $ctrl_name (parallel, tool-enabled)..."
        
        local_test_file=$(find "$SRC_TEST" -name "*.java" -type f -exec grep -l "$ctrl_name" {} \; 2>/dev/null | head -1)
        local_feature_name=$(echo "$ctrl_name" | sed -E 's/Controller$//')
        local_service_file=$(find "$SRC_MAIN" -name "${local_feature_name}Service.java" -path "*/service/*" -type f 2>/dev/null | head -1)
        local_base_path="/api/$(echo "$local_feature_name" | tr '[:upper:]' '[:lower:]')s"
        
        CTRL_TASK="Implement $ctrl_name class for the Spring Boot project at $PROJECT_ROOT.

YOUR TASK:
1. Read the test file: $local_test_file
2. Read the service interface: $local_service_file
3. Read any DTOs/exceptions imported by the test
4. Generate a REST controller that passes ALL tests

CLASS REQUIREMENTS:
- Package: com.tddsdd.controller
- @RestController + @RequestMapping(\"$local_base_path\")
- Constructor injection for ${local_feature_name}Service
- Use @Valid for request body validation
- Match exact HTTP status codes from tests (201, 200, 204, etc.)

Output the complete file:
// FILE: src/main/java/com/tddsdd/controller/${ctrl_name}.java
\`\`\`java
// ... full implementation ...
\`\`\`"

        claude_agent "$SYSTEM_PROMPT" "$CTRL_TASK" "$RESULTS_DIR/layer4_${ctrl_name}_output.txt" &
        PIDS+=($!)
        PID_NAMES+=("$ctrl_name")
    done
fi

# ── GlobalExceptionHandler ───────────────────────────────────────────────────
if [ "$NEED_HANDLER" = "true" ]; then
    log_info "Generating GlobalExceptionHandler (parallel, tool-enabled)..."
    
    HANDLER_TASK="Implement GlobalExceptionHandler for the Spring Boot project at $PROJECT_ROOT.

YOUR TASK:
1. Read ALL controller test files in $SRC_TEST/controller/ to see which exceptions are tested
2. Read ALL exception classes in $SRC_MAIN/exception/
3. Generate a handler that maps each exception to the correct HTTP status code

CLASS REQUIREMENTS:
- Package: com.tddsdd.exception
- @RestControllerAdvice
- Handle ResourceNotFoundException → 404 Not Found
- Handle DuplicateResourceException → 409 Conflict
- Handle InvalidOrderStateException → 400 Bad Request
- Handle InsufficientStockException → 400 Bad Request
- Handle MethodArgumentNotValidException → 400 Bad Request
- Return response body with 'message' key (Map<String, String>)

Output the complete file:
// FILE: src/main/java/com/tddsdd/exception/GlobalExceptionHandler.java
\`\`\`java
// ... full implementation ...
\`\`\`"

    claude_agent "$SYSTEM_PROMPT" "$HANDLER_TASK" "$RESULTS_DIR/layer4_handler_output.txt" &
    PIDS+=($!)
    PID_NAMES+=("GlobalExceptionHandler")
fi

# ── Wait for all parallel agents ─────────────────────────────────────────────
if [ ${#PIDS[@]} -gt 0 ]; then
    log_info "Waiting for ${#PIDS[@]} parallel agents to complete..."
    for i in "${!PIDS[@]}"; do
        wait "${PIDS[$i]}" || log_warn "${PID_NAMES[$i]} agent had issues"
        log_success "${PID_NAMES[$i]} complete"
    done
    
    for output_file in "$RESULTS_DIR"/layer4_*_output.txt; do
        [ -f "$output_file" ] || continue
        extract_and_write_java "$(cat "$output_file")"
    done
else
    log_info "Layer 4: No controllers/handlers to generate"
fi

log_info "Verifying Layer 4 compilation..."
check_compilation 2>&1 || log_warn "Layer 4 compilation issues — fixer will handle"

log_step "Layered build complete."
