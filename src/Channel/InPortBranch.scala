package ox.scl.channel

class InPortBranch(val guard: () => Boolean, inPort: InPort[A], body: () => Unit)
