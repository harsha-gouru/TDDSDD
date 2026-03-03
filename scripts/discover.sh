#!/bin/bash
# ============================================================================
# discover.sh — Auto-discover what needs to be generated
# ============================================================================
# Scans test files to find:
#   - Missing *Impl classes (referenced in tests but not in src/main)
#   - Missing *Controller classes
#   - Missing GlobalExceptionHandler
#   - Service dependency order (which services depend on which)
#   - Feature ↔ spec mapping
#
# Output: writes a manifest file to .agent-results/manifest.txt
# Format: key=value pairs, one per line
#
# Usage:
#   source discover.sh
#   discover_all                    # Discover all missing impls
#   discover_for_spec "order.md"    # Discover missing impls for a spec
#   discover_for_feature "Order"    # Discover missing impls for a feature
# ============================================================================

source "$(dirname "$0")/common.sh"

MANIFEST_FILE="$RESULTS_DIR/manifest.txt"

# ── Find all test files ──────────────────────────────────────────────────────
find_test_files() {
    find "$SRC_TEST" -name "*Test.java" -type f 2>/dev/null | sort
}

# ── Extract Impl class names from a test file ────────────────────────────────
# Finds *Impl classes referenced in the test (the implementation to generate)
# Only matches PascalCase class names ending in "Impl"
find_target_impl_from_test() {
    local test_file="$1"
    # Match PascalCase words ending in Impl (e.g., UserServiceImpl, OrderServiceImpl)
    grep -oE '\b[A-Z][a-zA-Z]*Impl\b' "$test_file" 2>/dev/null | sort -u
}

# ── Extract controller references from a test file ────────────────────────────
find_target_controller_from_test() {
    local test_file="$1"
    # Look for @WebMvcTest(SomeController.class) — most reliable
    local from_annotation
    from_annotation=$(grep -oE '@WebMvcTest\(\s*[A-Z][a-zA-Z]*Controller\.class\s*\)' "$test_file" 2>/dev/null \
        | grep -oE '[A-Z][a-zA-Z]*Controller')
    
    if [ -n "$from_annotation" ]; then
        echo "$from_annotation" | sort -u
    else
        # Fallback: PascalCase words ending in Controller
        grep -oE '\b[A-Z][a-zA-Z]*Controller\b' "$test_file" 2>/dev/null | sort -u
    fi
}

# ── Check if a Java class file exists in src/main ─────────────────────────────
class_exists() {
    local class_name="$1"
    find "$SRC_MAIN" -name "${class_name}.java" -type f 2>/dev/null | head -1
}

# ── Find the service interface for an Impl ────────────────────────────────────
find_interface_for_impl() {
    local impl_name="$1"
    # UserServiceImpl → UserService
    local interface_name="${impl_name%Impl}"
    local found
    found=$(find "$SRC_MAIN" -name "${interface_name}.java" -type f 2>/dev/null | head -1)
    echo "$found"
}

# ── Find the test file for a given Impl or Controller ─────────────────────────
find_test_for_class() {
    local class_name="$1"
    find "$SRC_TEST" -name "*.java" -type f -exec grep -l "$class_name" {} \; 2>/dev/null | head -1
}

# ── Extract @Mock dependencies from a test file ──────────────────────────────
# Returns the service/repo names that the test mocks (= dependencies)
find_mock_dependencies() {
    local test_file="$1"
    # Match lines like: @Mock private UserRepository userRepository;
    grep -A1 '@Mock' "$test_file" 2>/dev/null \
        | grep -oE '[A-Z][a-zA-Z]*(Repository|Service|Mapper)' \
        | sort -u
}

# ── Build dependency graph ────────────────────────────────────────────────────
# For each service impl, find what other services it depends on
# Returns: ImplName:Dep1,Dep2
build_dependency_order() {
    local impl_list="$1"  # newline-separated list of impl names
    local result=""
    
    while IFS= read -r impl; do
        [ -z "$impl" ] && continue
        local test_file
        test_file=$(find_test_for_class "$impl")
        if [ -n "$test_file" ]; then
            local deps
            deps=$(find_mock_dependencies "$test_file" | grep -v "Repository\|Mapper" | tr '\n' ',')
            deps="${deps%,}"  # trim trailing comma
            result+="${impl}:${deps}"$'\n'
        else
            result+="${impl}:"$'\n'
        fi
    done <<< "$impl_list"
    
    echo "$result"
}

# ── Topological sort for dependencies ─────────────────────────────────────────
# Simple: items with no deps first, then items whose deps are already resolved
topo_sort() {
    local dep_graph="$1"  # format: Impl:Dep1,Dep2\n per line
    local resolved=""
    local remaining="$dep_graph"
    local max_iterations=10
    local iteration=0
    
    while [ -n "$remaining" ] && [ $iteration -lt $max_iterations ]; do
        iteration=$((iteration + 1))
        local new_remaining=""
        
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            local impl="${line%%:*}"
            local deps="${line#*:}"
            
            # Check if all deps are resolved or are not impls (repositories, etc.)
            local all_resolved=true
            if [ -n "$deps" ]; then
                IFS=',' read -ra dep_arr <<< "$deps"
                for dep in "${dep_arr[@]}"; do
                    # Check if this dep is an Impl that hasn't been resolved yet
                    if echo "$dep_graph" | grep -q "^${dep}Impl:" 2>/dev/null; then
                        if ! echo "$resolved" | grep -q "$dep" 2>/dev/null; then
                            all_resolved=false
                            break
                        fi
                    fi
                done
            fi
            
            if [ "$all_resolved" = true ]; then
                resolved+="$impl"$'\n'
            else
                new_remaining+="$line"$'\n'
            fi
        done <<< "$remaining"
        
        remaining="$new_remaining"
    done
    
    # Append any remaining (circular deps)
    if [ -n "$remaining" ]; then
        while IFS= read -r line; do
            [ -z "$line" ] && continue
            resolved+="${line%%:*}"$'\n'
        done <<< "$remaining"
    fi
    
    echo "$resolved"
}

# ── Get context files for a given test ────────────────────────────────────────
# Scans imports in the test file and returns only the contract files it references
get_context_for_test() {
    local test_file="$1"
    local context=""
    
    # Extract all import statements from the test
    local imports
    imports=$(grep '^import com\.tddsdd\.' "$test_file" 2>/dev/null \
        | sed 's/import com\.tddsdd\.//' \
        | sed 's/;\s*$//' \
        | sort -u)
    
    # Map imports to actual files
    while IFS= read -r import_line; do
        [ -z "$import_line" ] && continue
        # Convert dot notation to path: dto.UserDTO → dto/UserDTO.java
        local file_path
        file_path=$(echo "$import_line" | tr '.' '/')
        file_path="${SRC_MAIN}/${file_path}.java"
        
        if [ -f "$file_path" ]; then
            context+="
// === FILE: $file_path ===
$(cat "$file_path")
"
        fi
    done <<< "$imports"
    
    echo "$context"
}

# ── Find matching spec for a feature ──────────────────────────────────────────
find_spec_for_feature() {
    local feature_lower
    feature_lower=$(echo "$1" | tr '[:upper:]' '[:lower:]' | tr ' ' '-')
    
    # Try exact match first
    for spec_file in "$SPECS_DIR"/*.md; do
        [ -f "$spec_file" ] || continue
        local basename
        basename=$(basename "$spec_file" .md)
        if [[ "$basename" == *"$feature_lower"* ]]; then
            echo "$spec_file"
            return 0
        fi
    done
    
    # Return all specs as fallback
    find "$SPECS_DIR" -name "*.md" -type f 2>/dev/null | sort
}

# ── Check if tests for a class already pass ──────────────────────────────────
check_tests_pass_for() {
    local test_class="$1"  # e.g., com.tddsdd.service.UserServiceTest
    local output
    output=$(cd "$PROJECT_ROOT" && mvn test -Dtest="$test_class" -q 2>&1)
    local exit_code=$?
    if [ $exit_code -eq 0 ]; then
        return 0  # tests pass
    else
        return 1  # tests fail
    fi
}

# ── Main discovery function ──────────────────────────────────────────────────
# Discovers everything and writes manifest
discover_all() {
    local filter_feature="${1:-}"  # optional: only discover for this feature
    
    log_step "═══════════════════════════════════════════════════════════"
    log_step "  AUTO-DISCOVERY"
    log_step "  Scanning tests to find missing implementations..."
    log_step "═══════════════════════════════════════════════════════════"
    
    # Initialize manifest
    echo "# TDDSDD Discovery Manifest" > "$MANIFEST_FILE"
    echo "# Generated: $(date)" >> "$MANIFEST_FILE"
    echo "" >> "$MANIFEST_FILE"
    
    local missing_impls=""
    local missing_controllers=""
    local all_impls=""
    local need_exception_handler=false
    
    # Scan all test files
    while IFS= read -r test_file; do
        [ -z "$test_file" ] && continue
        local test_basename
        test_basename=$(basename "$test_file" .java)
        
        # Determine feature name from test file path/name
        # e.g., UserServiceTest → User, OrderControllerTest → Order
        local feature_name
        feature_name=$(echo "$test_basename" | sed -E 's/(Service|Controller)Test$//')
        
        # Filter by feature if specified
        if [ -n "$filter_feature" ] && [ "$feature_name" != "$filter_feature" ]; then
            continue
        fi
        
        log_info "Scanning: $test_basename"
        
        # Check for Impl references
        local impls
        impls=$(find_target_impl_from_test "$test_file")
        for impl in $impls; do
            if [ -z "$(class_exists "$impl")" ]; then
                missing_impls+="$impl"$'\n'
                log_warn "  Missing: $impl"
            else
                log_info "  Found:   $impl"
            fi
            all_impls+="$impl"$'\n'
        done
        
        # Check for Controller references
        local controllers
        controllers=$(find_target_controller_from_test "$test_file")
        for ctrl in $controllers; do
            if [ -z "$(class_exists "$ctrl")" ]; then
                missing_controllers+="$ctrl"$'\n'
                log_warn "  Missing: $ctrl"
            else
                log_info "  Found:   $ctrl"
            fi
        done
        
        # Check if GlobalExceptionHandler is needed
        if grep -q 'GlobalExceptionHandler\|@RestControllerAdvice\|ResourceNotFoundException\|InvalidOrderStateException' "$test_file" 2>/dev/null; then
            if [ -z "$(class_exists "GlobalExceptionHandler")" ]; then
                need_exception_handler=true
            fi
        fi
        
    done < <(find_test_files)
    
    # Deduplicate
    missing_impls=$(echo "$missing_impls" | sort -u | grep -v '^$')
    missing_controllers=$(echo "$missing_controllers" | sort -u | grep -v '^$')
    all_impls=$(echo "$all_impls" | sort -u | grep -v '^$')
    
    # Build dependency graph and sort
    local dep_graph
    dep_graph=$(build_dependency_order "$all_impls")
    local sorted_impls
    sorted_impls=$(topo_sort "$dep_graph" | grep -v '^$')
    
    # Filter sorted list to only missing impls
    local sorted_missing=""
    while IFS= read -r impl; do
        [ -z "$impl" ] && continue
        if echo "$missing_impls" | grep -q "^${impl}$"; then
            sorted_missing+="$impl"$'\n'
        fi
    done <<< "$sorted_impls"
    
    # Write manifest
    echo "MISSING_IMPLS=$(echo "$sorted_missing" | grep -v '^$' | tr '\n' ',' | sed 's/,$//')" >> "$MANIFEST_FILE"
    echo "MISSING_CONTROLLERS=$(echo "$missing_controllers" | grep -v '^$' | tr '\n' ',' | sed 's/,$//')" >> "$MANIFEST_FILE"
    echo "NEED_EXCEPTION_HANDLER=$need_exception_handler" >> "$MANIFEST_FILE"
    echo "ALL_IMPLS=$(echo "$sorted_impls" | grep -v '^$' | tr '\n' ',' | sed 's/,$//')" >> "$MANIFEST_FILE"
    echo "DEP_GRAPH=$(echo "$dep_graph" | grep -v '^$' | tr '\n' '|' | sed 's/|$//')" >> "$MANIFEST_FILE"
    
    # Summary
    local impl_count
    impl_count=$(echo "$sorted_missing" | grep -v '^$' | wc -l | tr -d ' ')
    local ctrl_count
    ctrl_count=$(echo "$missing_controllers" | grep -v '^$' | wc -l | tr -d ' ')
    
    echo "" 
    log_step "Discovery Summary:"
    log_info "Missing service impls: $impl_count ($(echo "$sorted_missing" | grep -v '^$' | tr '\n' ',' | sed 's/,$//'))"
    log_info "Missing controllers:   $ctrl_count ($(echo "$missing_controllers" | grep -v '^$' | tr '\n' ',' | sed 's/,$//'))"
    log_info "Need exception handler: $need_exception_handler"
    log_info "Build order:           $(echo "$sorted_impls" | grep -v '^$' | tr '\n' ' ')"
    log_info "Manifest saved to:     $MANIFEST_FILE"
    
    # Check if there's anything to do
    local total=$((impl_count + ctrl_count))
    if [ "$need_exception_handler" = true ]; then
        total=$((total + 1))
    fi
    
    if [ "$total" -eq 0 ]; then
        log_success "Nothing to generate — all implementations exist!"
        echo "NOTHING_TO_DO=true" >> "$MANIFEST_FILE"
        return 0
    fi
    
    echo "NOTHING_TO_DO=false" >> "$MANIFEST_FILE"
    return 0
}

# ── Read manifest values ─────────────────────────────────────────────────────
read_manifest() {
    local key="$1"
    grep "^${key}=" "$MANIFEST_FILE" 2>/dev/null | head -1 | cut -d'=' -f2-
}

# Export for subshells
export -f find_test_files find_target_impl_from_test find_target_controller_from_test
export -f class_exists find_interface_for_impl find_test_for_class
export -f find_mock_dependencies get_context_for_test find_spec_for_feature
export -f read_manifest discover_all
export MANIFEST_FILE
