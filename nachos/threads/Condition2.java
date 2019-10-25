package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		this.alarm = new Alarm();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {

	
		boolean intStatus = Machine.interrupt().disable();
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		
		waitQueue.waitForAccess(KThread.currentThread());
		
		conditionLock.release();
		KThread.sleep();	
	
		
		conditionLock.acquire();
	
		Machine.interrupt().restore(intStatus);
		
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = waitQueue.nextThread();
		if(thread != null){
			if (!alarm.cancel(thread))
				thread.ready();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean intStatus = Machine.interrupt().disable();
		KThread thread = waitQueue.nextThread();
		while (thread !=null){
			if (!alarm.cancel(thread))
				thread.ready();
			thread = waitQueue.nextThread();
		}
		Machine.interrupt().restore(intStatus);

	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
        public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();

		waitQueue.waitForAccess(KThread.currentThread());
		
		conditionLock.release();
		alarm.waitUntil(timeout);	
	
		
		conditionLock.acquire();
	
		Machine.interrupt().restore(intStatus);



	}
	public int value;
	private Alarm alarm;
        private Lock conditionLock;
	private ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
	private static class InterlockTest {

		private static Lock lock;
		private static Condition2 cv;
	
		private static class Interlocker implements Runnable {
			public void run() {
				lock.acquire();
				for (int i = 0; i < 10; i++) {
					System.out.println(KThread.currentThread().getName());
					cv.wake();
					cv.sleep();
				}
				lock.release();
			}
		}

		public InterlockTest(){
			lock = new Lock();
			cv = new Condition2(lock);
			KThread ping = new KThread(new Interlocker());
			ping.setName("ping");
			KThread pong = new KThread(new Interlocker());
			pong.setName("pong");

			ping.fork();
			pong.fork();
			//for (int i = 0; i<50; i++) 
			//	KThread.currentThread().yield();
			ping.join();
			
		}
	}
	private static void sleepForTest1(){
		Lock lock =new Lock();
		Condition2 cv = new Condition2(lock);
		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName()+" sleeping");
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println(KThread.currentThread().getName()+" woke up, slept for " +(t1-t0) + "ticks");
		lock.release();
	}

	public static void selfTest(){
		new InterlockTest();
		sleepForTest1();
	}



}
