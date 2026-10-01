package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>,
                             Stmt.Visitor<Void> {
  // Chapter 8, challenge 2: a sentinel stored in a variable declared
  // without an initializer, so we can tell "never assigned" apart from
  // "explicitly assigned nil".
  private static final Object uninitialized = new Object();

  // Chapter 9, challenge 3: thrown by a break statement and caught by
  // the nearest enclosing while loop. No stack trace is needed since it
  // is used for control flow, not error reporting.
  private static class BreakException extends RuntimeException {
    BreakException() {
      super(null, null, false, false);
    }
  }

  // Chapter 11, challenge 4: where the resolver found a local variable.
  // "depth" is how many scopes to walk out, "index" is its slot there.
  private static class Slot {
    final int depth;
    final int index;

    Slot(int depth, int index) {
      this.depth = depth;
      this.index = index;
    }
  }

  // Globals are still looked up by name, since a function body may refer
  // to a global that is declared later in the file.
  final Map<String, Object> globals = new HashMap<>();
  // The outermost environment. It holds no slots; it only marks "global
  // scope" and ends every chain of enclosing environments.
  private final Environment globalEnvironment = new Environment();
  private Environment environment = globalEnvironment;
  private final Map<Expr, Slot> locals = new HashMap<>();

  Interpreter() {
    globals.put("clock", new LoxCallable() {
      @Override
      public int arity() { return 0; }

      @Override
      public Object call(Interpreter interpreter,
                         List<Object> arguments) {
        return (double)System.currentTimeMillis() / 1000.0;
      }

      @Override
      public String toString() { return "<native fn>"; }
    });
  }

  void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  // Chapter 8, challenge 1: evaluates a bare REPL expression and returns
  // its value as a string, or null if a runtime error occurred.
  String interpret(Expr expression) {
    try {
      Object value = evaluate(expression);
      return stringify(value);
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
      return null;
    }
  }

  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }

  private void execute(Stmt stmt) {
    stmt.accept(this);
  }

  void resolve(Expr expr, int depth, int index) {
    locals.put(expr, new Slot(depth, index));
  }

  void executeBlock(List<Stmt> statements,
                    Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

      for (Stmt statement : statements) {
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitBreakStmt(Stmt.Break stmt) {
    throw new BreakException();
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    LoxFunction function =
        new LoxFunction(stmt.name.lexeme, stmt.function, environment);
    declare(stmt.name, function);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null) value = evaluate(stmt.value);

    throw new Return(value);
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = uninitialized;
    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
    }

    declare(stmt.name, value);
    return null;
  }

  // Chapter 11, challenge 4: a global goes in the map by name. A local is
  // appended to the current scope's array, which puts it at the index the
  // resolver gave it.
  private void declare(Token name, Object value) {
    if (environment == globalEnvironment) {
      globals.put(name.lexeme, value);
    } else {
      environment.define(value);
    }
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    try {
      while (isTruthy(evaluate(stmt.condition))) {
        execute(stmt.body);
      }
    } catch (BreakException ex) {
      // Exit the loop.
    }
    return null;
  }

  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);

    Slot slot = locals.get(expr);
    if (slot != null) {
      environment.assignAt(slot.depth, slot.index, value);
    } else if (globals.containsKey(expr.name.lexeme)) {
      globals.put(expr.name.lexeme, value);
    } else {
      throw new RuntimeError(expr.name,
          "Undefined variable '" + expr.name.lexeme + "'.");
    }

    return value;
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
    Object value = lookUpVariable(expr.name, expr);
    if (value == uninitialized) {
      throw new RuntimeError(expr.name,
          "Variable '" + expr.name.lexeme +
          "' must be initialized before use.");
    }
    return value;
  }

  private Object lookUpVariable(Token name, Expr expr) {
    Slot slot = locals.get(expr);
    if (slot != null) {
      return environment.getAt(slot.depth, slot.index);
    }

    if (globals.containsKey(name.lexeme)) {
      return globals.get(name.lexeme);
    }

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value;
  }

  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.OR) {
      if (isTruthy(left)) return left;
    } else {
      if (!isTruthy(left)) return left;
    }

    return evaluate(expr.right);
  }

  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = evaluate(expr.callee);

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : expr.arguments) {
      arguments.add(evaluate(argument));
    }

    if (!(callee instanceof LoxCallable)) {
      throw new RuntimeError(expr.paren,
          "Can only call functions and classes.");
    }

    LoxCallable function = (LoxCallable)callee;
    if (arguments.size() != function.arity()) {
      throw new RuntimeError(expr.paren, "Expected " +
          function.arity() + " arguments but got " +
          arguments.size() + ".");
    }

    return function.call(this, arguments);
  }

  // Chapter 10, challenge 2: an anonymous function closes over the
  // environment where the expression is evaluated.
  @Override
  public Object visitFunctionExpr(Expr.Function expr) {
    return new LoxFunction(null, expr, environment);
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression);
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG:
        return !isTruthy(right);
      case MINUS:
        checkNumberOperand(expr.operator, right);
        return -(double)right;
    }

    // Unreachable.
    return null;
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case GREATER:
        checkNumberOperands(expr.operator, left, right);
        return (double)left > (double)right;
      case GREATER_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left >= (double)right;
      case LESS:
        checkNumberOperands(expr.operator, left, right);
        return (double)left < (double)right;
      case LESS_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left <= (double)right;
      case BANG_EQUAL: return !isEqual(left, right);
      case EQUAL_EQUAL: return isEqual(left, right);
      case MINUS:
        checkNumberOperands(expr.operator, left, right);
        return (double)left - (double)right;
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double)left + (double)right;
        }

        if (left instanceof String && right instanceof String) {
          return (String)left + (String)right;
        }

        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case SLASH:
        checkNumberOperands(expr.operator, left, right);
        return (double)left / (double)right;
      case STAR:
        checkNumberOperands(expr.operator, left, right);
        return (double)left * (double)right;
    }

    // Unreachable.
    return null;
  }

  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  private void checkNumberOperands(Token operator,
                                   Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;

    throw new RuntimeError(operator, "Operands must be numbers.");
  }

  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    return true;
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  private String stringify(Object object) {
    if (object == null) return "nil";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return object.toString();
  }
}
