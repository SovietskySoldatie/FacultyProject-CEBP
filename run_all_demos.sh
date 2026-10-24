#!/usr/bin/env bash

# Resolve root directory of the repository
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

LOGS_DIR="logs"
mkdir -p "$LOGS_DIR"

# Fixed demo settings: 100 clients per scenario and 600 seconds (10 minutes) timeout guard
CLIENT_COUNT=100
TIMEOUT_SECONDS=600

echo "================================================================="
echo " Starting Bureaucratic System Demos Execution"
echo " Citizens per scenario: $CLIENT_COUNT"
echo " Logs destination:      $SCRIPT_DIR/$LOGS_DIR"
echo " Timeout Guard:         Active for RACE_CONDITION_QUEUE and DEADLOCK (Timeout: ${TIMEOUT_SECONDS}s)"
echo "================================================================="

# Compile the latest Java bytecode before running
echo "[Build] Compiling project with Maven..."
mvn compile -q

run_demo() {
    local mode="$1"
    local guard="$2"
    local timeout="$3"
    local log_file="$LOGS_DIR/$4"

    echo ""
    echo "-----------------------------------------------------------------"
    echo ">>> Running Demo: $mode (Guard: $guard, Timeout: ${timeout:-N/A})"
    echo ">>> Output log:   $log_file"
    echo "-----------------------------------------------------------------"

    if [ "$guard" = "true" ]; then
        java -cp target/classes bureaucraticSystem.demo.Demo "$mode" "$CLIENT_COUNT" "$guard" "$timeout" 2>&1 | tee "$log_file"
    else
        java -cp target/classes bureaucraticSystem.demo.Demo "$mode" "$CLIENT_COUNT" "$guard" 2>&1 | tee "$log_file"
    fi

    echo ">>> Completed $mode. Log saved to $log_file"
    sleep 1
}

# 1. ALL_GOOD (Safe components, completes naturally in ~430s)
run_demo "ALL_GOOD" "false" "" "all_good.log"

# 2. RACE_CONDITION_SCHEDULER (Singleton fault, clients terminate immediately on missing queues)
run_demo "RACE_CONDITION_SCHEDULER" "false" "" "race_condition_scheduler.log"

# 3. RACE_CONDITION_QUEUE (Unsafe LinkedList queue: drops tickets causing clients to hang; requires timeout guard)
run_demo "RACE_CONDITION_QUEUE" "true" "$TIMEOUT_SECONDS" "race_condition_queue.log"

# 4. RACE_CONDITION_COUNTER (Non-atomic counter lost updates, safe stamp/queue, completes naturally)
run_demo "RACE_CONDITION_COUNTER" "false" "" "race_condition_counter.log"

# 5. DEADLOCK (UnsafeStamp + UnsafeCounter circular wait: deadlocks desks; requires timeout guard)
run_demo "DEADLOCK" "true" "$TIMEOUT_SECONDS" "deadlock.log"

echo ""
echo "================================================================="
echo " All 5 demos have finished executing!"
echo " Log files created in '$LOGS_DIR/':"
ls -lh "$LOGS_DIR"
echo "================================================================="
