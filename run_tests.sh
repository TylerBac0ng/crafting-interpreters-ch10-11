#!/bin/bash
# Compiles jlox and runs every test in tests/, comparing stdout (and stderr
# where a .expected_err file exists) against the expected output.
set -u
cd "$(dirname "$0")"
mkdir -p build
javac -d build $(find src -name '*.java') || exit 1

pass=0; fail=0
check() {  # name, actual_out, actual_err
  local name=$1 ok=1
  diff <(printf '%s' "$2") <(cat "tests/$name.expected") >/dev/null || ok=0
  if [ -f "tests/$name.expected_err" ]; then
    diff <(printf '%s\n' "$3") <(cat "tests/$name.expected_err") >/dev/null || ok=0
  fi
  if [ $ok = 1 ]; then echo "PASS $name"; pass=$((pass+1));
  else echo "FAIL $name"; echo "--- stdout:"; printf '%s\n' "$2"; echo "--- stderr:"; printf '%s\n' "$3"; fail=$((fail+1)); fi
}

for f in tests/*.lox; do
  name=$(basename "$f" .lox)
  out=$(java -cp build com.craftinginterpreters.lox.Lox "$f" 2>build/err; printf x); out=${out%x}
  check "$name" "$out" "$(cat build/err)"
done

out=$(java -cp build com.craftinginterpreters.lox.Lox < tests/repl.input 2>build/err; printf x); out=${out%x}
check repl "$out" "$(cat build/err)"

echo "$pass passed, $fail failed"
[ $fail = 0 ]
