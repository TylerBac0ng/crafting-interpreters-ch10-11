# Crafting Interpreters — Chapters 10 & 11 Challenges

Solutions to the end-of-chapter challenges from [_Crafting Interpreters_](https://craftinginterpreters.com/) by Robert Nystrom, chapters **10 (Functions)** and **11 (Resolving and Binding)**. This is the complete jlox tree-walk interpreter through chapter 11, so it compiles and runs on its own. It builds on my chapter 8 & 9 solutions, so the REPL expressions, uninitialized-variable error and `break` statement are still here.

## Build and run

```sh
javac -d build $(find src -name '*.java')
java -cp build com.craftinginterpreters.lox.Lox            # REPL
java -cp build com.craftinginterpreters.lox.Lox file.lox   # run a script
./run_tests.sh                                            # run the tests
```

## Chapter 10 — Functions

1. **Why Smalltalk doesn't check arity at runtime.** Written answer only
   (see `answers.pdf`).
2. **Anonymous functions.** `fun (a) { print a; }` is an expression. A
   named declaration is now `Stmt.Function(name, Expr.Function)`, so both
   share the same parsing, resolving and `LoxFunction` code. The tricky
   case `fun () {};` is handled with one token of lookahead: `fun`
   followed by a name is a declaration, otherwise it is an expression
   statement. See `tests/ch10_lambda.lox`.
3. **Are parameters in the same scope as locals?** Written answer (see
   `answers.pdf`). `tests/ch10_param_scope.lox` shows that this
   interpreter reports `var a` redeclaring parameter `a` as an error.

## Chapter 11 — Resolving and Binding

1. **Why a function's name can be defined eagerly.** Written answer only.
2. **`var a = a;` in other languages.** Written answer only.
3. **Unused local variables are an error.** The resolver tracks whether
   each local is ever read and reports `Local variable 'x' is never
   used.` when its scope ends. Parameters and globals are not reported.
   See `tests/ch11_unused.lox`.
4. **Locals stored by index.** The resolver numbers each local in its
   scope (in declaration order) and gives the interpreter a
   `(depth, index)` pair. `Environment` stores locals in an
   `ArrayList`, so a lookup walks `depth` scopes and reads one array
   slot, with no hashing. Globals stay in a name → value map in the
   interpreter. See `tests/ch11_slots.lox`.

## Files changed from the book's chapter 11 code

| File | Change |
|---|---|
| `tool/GenerateAst.java` → `Expr.java`, `Stmt.java` | `Expr.Function` node; `Stmt.Function` wraps it (10.2) |
| `Parser.java` | `functionBody()`, lambda in `primary()`, `checkNext()` lookahead (10.2); `loopDepth` reset in function bodies |
| `LoxFunction.java` | Takes a name and an `Expr.Function`; defines params by slot (10.2, 11.4) |
| `Resolver.java` | `Variable` class with `index` and `used`; unused-local errors in `endScope()` (11.3); passes indexes to the interpreter (11.4) |
| `Environment.java` | Array of slots with `getAt` / `assignAt` by index (11.4) |
| `Interpreter.java` | `globals` map, `Slot` lookups, `visitFunctionExpr` (10.2, 11.4) |
| `AstPrinter.java` | Prints calls and anonymous functions |

## Tests

`./run_tests.sh` compiles the interpreter and runs every file in `tests/`,
comparing stdout and stderr with the `.expected` / `.expected_err` files.
`tests/ch8_shadow.lox` (`var a = a + 2;` in a block) printed `3` in the
chapter 8 interpreter; it is now the resolver error from chapter 11.

## Benchmark

`benchmark/locals.lox` runs a 5,000,000-iteration loop over block locals.
On my machine it took about 2.2 s with my chapter 8 & 9 interpreter (locals
in a `HashMap`) and about 1.4 s with this one (locals in array slots),
including JVM startup.
