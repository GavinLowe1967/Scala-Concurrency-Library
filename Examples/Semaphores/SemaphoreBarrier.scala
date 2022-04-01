import ox.scl._

class SemaphoreBarrier(n: Int){
  assert(n > 1)
  private var waiting = 0 // number of processes currently waiting
  private val waitSem = new SignallingSemaphore()
  private val mutex = new MutexSemaphore()

  def sync(me: Int) = {
    mutex.down
    if(waiting == n-1) waitSem.up
    else{ 
      waiting += 1; mutex.up; waitSem.down // Wait until woken
      waiting -= 1
      if(waiting==0) mutex.up; else waitSem.up // pass the baton
    }
  }
}
    
