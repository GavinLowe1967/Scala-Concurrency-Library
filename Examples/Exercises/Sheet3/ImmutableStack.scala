/** An immutable stack, with contents `stack`. */
class ImmutableStack[A](stack: List[A] = List()){
  /** Is the stack empty? */
  def isEmpty = stack.isEmpty

  /** Push x, returning the resulting stack. */
  def push(x: A) = new ImmutableStack(x::stack)

  /** Pop from this. */
  def pop: A = { require(stack.nonEmpty); stack.head }

  /** Pop from this, returning the value popped and the resulting stack. */
  def pop2: (A, ImmutableStack[A]) = {
    require(stack.nonEmpty); (stack.head, new ImmutableStack(stack.tail))
  }

}
