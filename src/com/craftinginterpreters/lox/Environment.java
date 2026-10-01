package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;

// Chapter 11, challenge 4: a local scope stores its variables in an array.
// The resolver gives each local variable an index in its scope, so the
// interpreter can reach it with (distance, index) and never looks up a
// name. Global variables are not stored here; they live in a map in the
// Interpreter, because they can be referenced before they are declared.
class Environment {
  final Environment enclosing;
  private final List<Object> values = new ArrayList<>();

  Environment() {
    enclosing = null;
  }

  Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  // Locals are defined in the same order the resolver declared them, so
  // the new value always lands at the index the resolver assigned.
  void define(Object value) {
    values.add(value);
  }

  Object getAt(int distance, int index) {
    return ancestor(distance).values.get(index);
  }

  void assignAt(int distance, int index, Object value) {
    ancestor(distance).values.set(index, value);
  }

  Environment ancestor(int distance) {
    Environment environment = this;
    for (int i = 0; i < distance; i++) {
      environment = environment.enclosing;
    }

    return environment;
  }
}
