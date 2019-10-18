package nachos.threads;

import nachos.machine.*;
import java.util.*;
import java.lang.Long;


/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private Vector<KThread> thread_list;
	private ArrayList<Long> time_list;

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
		thread_list = new Vector<KThread>();
		time_list = new ArrayList<Long> ();
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		//KThread.currentThread().yield();
		for(int i =0; i < time_list.size();i++){
			//if(time_list.get(i).longValue() <=0 && time_list.get(i).longValue()>-500){
			//	time_list.set(i, new Long(-501));
			//	thread_list.get(i).ready();	
			//}
			//else time_list.set(i, new Long(time_list.get(i).longValue() -500));
			
			if(time_list.get(i).longValue() >=0 &&Machine.timer().getTime()>= time_list.get(i).longValue())
			{	thread_list.get(i).ready();
				time_list.set(i,new Long(-1));

			}
		}
		KThread.currentThread().yield();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		boolean intStatus =Machine.interrupt().disable();
 		thread_list.add(KThread.currentThread());
                time_list.add(new Long(Machine.timer().getTime()+x));	
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
	}

        /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
        public boolean cancel(KThread thread) {
		return false;
	}


    // Add Alarm testing code to the Alarm class
    
    public static void alarmTest1() {
	int durations[] = {501, 1501,3001, 499, 1499, 2999, 14440};
	long t0, t1;

	for (int d : durations) {
	    t0 = Machine.timer().getTime();
	    ThreadedKernel.alarm.waitUntil (d);
	    t1 = Machine.timer().getTime();
	    System.out.println("t0 is:"+ t0);
	    System.out.println("t1 is:"+ t1);
	    System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
	    System.out.println("duration should be :"+d);
	}
	
    }

    // Implement more test methods here ...

    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
	alarmTest1();

	// Invoke your other test methods here ...
    }


}
