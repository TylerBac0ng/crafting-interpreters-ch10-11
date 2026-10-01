package com.craftinginterpreters.lox;

import java.util.List;

class LoxFunction implements LoxCallable {
  // Chapter 10, challenge 2: null for an anonymous function.
  private final String name;
  private final Expr.Function declaration;
  private final Environment closure;

  LoxFunction(String name, Expr.Function declaration,
              Environment closure) {
    this.name = name;
    this.declaration = declaration;
    this.closure = closure;
  }

  @Override
  public int arity() {
    return declaration.params.size();
  }

  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    Environment environment = new Environment(closure);
    // Chapter 11, challenge 4: parameters fill slots 0..n-1, in the same
    // order the resolver declared them.
    for (Object argument : arguments) {
      environment.define(argument);
    }

    try {
      interpreter.executeBlock(declaration.body, environment);
    } catch (Return returnValue) {
      return returnValue.value;
    }
    return null;
  }

  @Override
  public String toString() {
    if (name == null) return "<fn>";
    return "<fn " + name + ">";
  }
}
