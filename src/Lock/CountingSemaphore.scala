package ox.scl.lock

/** A counting semaphore.
  * @param permits the initial number of permits available. */
class CountingSemaphore(private var permits: Int = 0){
  /** Create one extra permit.  Unblock a waiting down, if there is one. */
  def up = synchronized{
    permits += 1; notify()
  }

  /** Wait for a permit and then consume it. */
  def down = synchronized{
    while(permits == 0) wait()
    permits -= 1
  }
}
