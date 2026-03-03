#!/bin/bash
# ============================================================================
# common.sh — Shared utilities for TDDSDD orchestration scripts (Level 2)
# ============================================================================

# NOTE: We intentionally do NOT use 'set -e' here because:
# 1. Background processes (&) with set -e cause unpredictable exits
# 2. Compilation/test checks are expected to fail and we handle errors manually
set -uo pipefail

# ── Java Configuration ───────────────────────────────────────────────────────
if [ -d "/opt/homebrew/opt/openjdk@17" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:/opt/homebrew/bin:$PATH"
elif [ -n "${JAVA_HOME:-}" ]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi

# ── Model Configuration ─────────────────────────────────────────────────────
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_MAX_TURNS="${CLAUDE_MAX_TURNS:-10}"

# ── Project Paths ────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_MAIN="$PROJECT_ROOT/src/main/java/com/tddsdd"
SRC_TEST="$PROJECT_ROOT/src/test/java/com/tddsdd"
SPECS_DIR="$PROJECT_ROOT/specs"
CONSTITUTION="$PROJECT_ROOT/constitution.md"
RESULTS_DIR="$PROJECT_ROOT/.agent-results"

mkdir -p "$RESULTS_DIR"

# ── Colors & Logging ────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[✅  ]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[❌  ]${NC} $1"; }
log_step()    { echo -e "${CYAN}[STEP]${NC} $1"; }
log_agent()   { echo -e "${YELLOW}[🤖  ]${NC} Agent: $1"; }

# ── Claude Wrapper ───────────────────────────────────────────────────────────
claude_prompt() {
    local prompt="$1"
    local output_file="${2:-}"

    log_agent "Calling claude ($CLAUDE_MODEL)..."

    local result=""
    local exit_code=0

    if [ -n "$output_file" ]; then
        result=$(claude -p "$prompt" --model "$CLAUDE_MODEL" --max-turns "$CLAUDE_MAX_TURNS" 2>&1) || exit_code=$?
        if [ $exit_code -ne 0 ]; then
            log_error "Claude call failed (exit code $exit_code):"
            echo "$result" | head -5 >&2
            echo "$result" > "$output_file"
            return $exit_code
        fi
        echo "$result" > "$output_file"
    else
        result=$(claude -p "$prompt" --model "$CLAUDE_MODEL" --max-turns "$CLAUDE_MAX_TURNS" 2>&1) || exit_code=$?
        if [ $exit_code -ne 0 ]; then
            log_error "Claude call failed (exit code $exit_code):"
            echo "$result" | head -5 >&2
            return $exit_code
        fi
        echo "$result"
    fi
}

# ── Tool-Enabled Agent (Level 3) ─────────────────────────────────────────────
# Uses --allowedTools so the agent can read files and run mvn itself
claude_agent() {
    local system_prompt="$1"
    local task_prompt="$2"
    local output_file="${3:-}"

    log_agent "Calling tool-enabled agent ($CLAUDE_MODEL)..."

    local result=""
    local exit_code=0
    local agent_args=(
        -p "$task_prompt"
        --model "$CLAUDE_MODEL"
        --max-turns "$CLAUDE_MAX_TURNS"
        --permission-mode bypassPermissions
        --allowedTools "Read,Bash(mvn:*),Bash(cat:*),Bash(find:*),Bash(grep:*)"
        --append-system-prompt "$system_prompt"
    )

    if [ -n "$output_file" ]; then
        result=$(claude "${agent_args[@]}" 2>&1) || exit_code=$?
        echo "$result" > "$output_file"
        if [ $exit_code -ne 0 ]; then
            log_error "Agent call failed (exit code $exit_code)"
            return $exit_code
        fi
    else
        result=$(claude "${agent_args[@]}" 2>&1) || exit_code=$?
        if [ $exit_code -ne 0 ]; then
            log_error "Agent call failed (exit code $exit_code)"
            echo "$result" | head -5 >&2
            return $exit_code
        fi
        echo "$result"
    fi
}

# ── Auth Check ───────────────────────────────────────────────────────────────
check_claude_auth() {
    log_info "Checking Claude authentication..."
    local result
    result=$(claude -p "say ok" --model "$CLAUDE_MODEL" --max-turns 1 2>&1) || {
        log_error "Claude authentication failed!"
        log_error "$result"
        log_error "Fix: Run 'claude' interactively to re-authenticate, then retry."
        return 1
    }
    log_success "Claude authenticated OK"
}

# ── Compilation Check ────────────────────────────────────────────────────────
check_compilation() {
    log_info "Running mvn compile..."
    local output
    output=$(cd "$PROJECT_ROOT" && mvn compile -q 2>&1) || {
        echo "$output"
        return 1
    }
    log_success "Compilation successful!"
    return 0
}

# ── Test Check (full or scoped) ──────────────────────────────────────────────
check_tests() {
    local test_class="${1:-}"
    if [ -n "$test_class" ]; then
        log_info "Running mvn test -Dtest=$test_class..."
        local output
        output=$(cd "$PROJECT_ROOT" && mvn test -Dtest="$test_class" 2>&1) || {
            echo "$output"
            return 1
        }
    else
        log_info "Running mvn test..."
        local output
        output=$(cd "$PROJECT_ROOT" && mvn test 2>&1) || {
            echo "$output"
            return 1
        }
    fi
    log_success "All tests pass!"
    return 0
}

# ── Dynamic File Readers ─────────────────────────────────────────────────────

# Read all test files
read_test_files() {
    local test_content=""
    while IFS= read -r -d '' file; do
        test_content+="
// === FILE: $file ===
$(cat "$file")
"
    done < <(find "$SRC_TEST" -name "*.java" -print0)
    echo "$test_content"
}

# Read all contract files (everything in src/main EXCEPT *Impl, *Controller, GlobalExceptionHandler)
read_contract_files() {
    local contract_content=""
    while IFS= read -r -d '' file; do
        local basename
        basename=$(basename "$file" .java)
        # Skip implementation files — they are generated, not contracts
        if [[ "$basename" == *Impl ]] || [[ "$basename" == *Controller ]] || [[ "$basename" == "GlobalExceptionHandler" ]]; then
            continue
        fi
        contract_content+="
// === FILE: $file ===
$(cat "$file")
"
    done < <(find "$SRC_MAIN" -name "*.java" -print0)
    echo "$contract_content"
}

# Read contracts relevant to a specific test file (smart context)
read_context_for_test() {
    local test_file="$1"
    local context=""
    
    # Extract all com.tddsdd imports from the test
    local imports
    imports=$(grep '^import com\.tddsdd\.' "$test_file" 2>/dev/null \
        | sed 's/import com\.tddsdd\.//' \
        | sed 's/;\s*$//' \
        | sort -u)
    
    while IFS= read -r import_line; do
        [ -z "$import_line" ] && continue
        local file_path
        file_path=$(echo "$import_line" | tr '.' '/')
        file_path="${SRC_MAIN}/${file_path}.java"
        
        if [ -f "$file_path" ]; then
            local basename
            basename=$(basename "$file_path" .java)
            # Skip implementation files in context
            if [[ "$basename" == *Impl ]] || [[ "$basename" == *Controller ]] || [[ "$basename" == "GlobalExceptionHandler" ]]; then
                continue
            fi
            context+="
// === FILE: $file_path ===
$(cat "$file_path")
"
        fi
    done <<< "$imports"
    
    echo "$context"
}

# Read spec files — all or filtered by feature name
read_specs() {
    local feature="${1:-}"
    local spec_content=""
    
    if [ -n "$feature" ]; then
        # Find matching spec
        local feature_lower
        feature_lower=$(echo "$feature" | tr '[:upper:]' '[:lower:]' | tr ' ' '-')
        for spec_file in "$SPECS_DIR"/*.md; do
            [ -f "$spec_file" ] || continue
            local basename
            basename=$(basename "$spec_file" .md)
            if [[ "$basename" == *"$feature_lower"* ]]; then
                spec_content+="
## Spec: $basename
$(cat "$spec_file")
"
            fi
        done
    fi
    
    # If nothing found, include all specs
    if [ -z "$spec_content" ]; then
        for spec_file in "$SPECS_DIR"/*.md; do
            [ -f "$spec_file" ] || continue
            spec_content+="
## Spec: $(basename "$spec_file" .md)
$(cat "$spec_file")
"
        done
    fi
    
    echo "$spec_content"
}

# Read constitution
read_constitution() {
    if [ -f "$CONSTITUTION" ]; then
        cat "$CONSTITUTION"
    else
        log_warn "Constitution file not found: $CONSTITUTION"
    fi
}

# ── List generated (non-contract) files ──────────────────────────────────────
list_generated_files() {
    find "$SRC_MAIN" -name "*.java" -type f | while read -r file; do
        local basename
        basename=$(basename "$file" .java)
        if [[ "$basename" == *Impl ]] || [[ "$basename" == *Controller ]] || [[ "$basename" == "GlobalExceptionHandler" ]]; then
            echo "$file"
        fi
    done
}

# ── File Writer ──────────────────────────────────────────────────────────────
extract_and_write_java() {
    local output="$1"
    local temp_file
    temp_file=$(mktemp)
    echo "$output" > "$temp_file"

    local current_file=""
    local in_code_block=false
    local code_content=""

    while IFS= read -r line; do
        if [[ "$line" =~ \/\/.*FILE:.*\.java ]] || [[ "$line" =~ ^###.*\.java ]] || [[ "$line" =~ src/main/java.*\.java ]]; then
            local extracted_path
            extracted_path=$(echo "$line" | grep -oE 'src/main/java/[^ ]*\.java' | head -1)
            if [ -n "$extracted_path" ]; then
                if [ -n "$current_file" ] && [ -n "$code_content" ]; then
                    local full_path="$PROJECT_ROOT/$current_file"
                    mkdir -p "$(dirname "$full_path")"
                    echo "$code_content" > "$full_path"
                    log_success "Wrote: $current_file"
                fi
                current_file="$extracted_path"
                code_content=""
            fi
        fi

        if [[ "$line" == '```java' ]]; then
            in_code_block=true
            continue
        elif [[ "$line" == '```' ]] && [ "$in_code_block" = true ]; then
            in_code_block=false
            continue
        fi

        if [ "$in_code_block" = true ]; then
            code_content+="$line
"
        fi
    done < "$temp_file"

    if [ -n "$current_file" ] && [ -n "$code_content" ]; then
        local full_path="$PROJECT_ROOT/$current_file"
        mkdir -p "$(dirname "$full_path")"
        echo "$code_content" > "$full_path"
        log_success "Wrote: $current_file"
    fi

    rm -f "$temp_file"
}

log_info "TDDSDD common utilities loaded (Level 2)"
log_info "Claude model: $CLAUDE_MODEL"
log_info "Project root: $PROJECT_ROOT"

# ── Export for subshells ─────────────────────────────────────────────────────
export CLAUDE_MODEL CLAUDE_MAX_TURNS PROJECT_ROOT SRC_MAIN SRC_TEST SPECS_DIR CONSTITUTION RESULTS_DIR
export RED GREEN YELLOW BLUE CYAN NC
export -f log_info log_success log_warn log_error log_step log_agent
export -f claude_prompt claude_agent extract_and_write_java
export -f check_compilation check_tests
export -f read_test_files read_contract_files read_context_for_test read_specs read_constitution
export -f list_generated_files
