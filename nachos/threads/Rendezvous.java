package nachos.threads;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */


    private HashMap<Integer, LinkedList<Integer>> valMap; // hashmap of tag&values to exchange
    private HashMap<Integer, Lock> lockMap; // hashmap of tag&lock
    private HashMap<Integer, Condition> condMap; // hashmap of tag&condition
    public Rendezvous () {
        this.semaphore_map = new HashMap<Integer,ArrayList<Semaphore>>();
        this.val_map = new HashMap<Integer,LinkedList<Integer>> (); 
        valMap = new HashMap<Integer, LinkedList<Integer>>();
    	lockMap = new HashMap<Integer, Lock>();
        condMap = new HashMap<Integer, Condition>();
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */


    

    public int exchange (int tag, int value) {
	// create Lock and Condition variables for this tag if not yet created
	if (lockMap.get(tag) == null)
		lockMap.put(tag, new Lock());
	if (condMap.get(tag) == null)
		condMap.put(tag, new Condition(lockMap.get(tag)));

	// get the condition Lock
	lock =lockMap.get(tag);
	cond = condMap.get(tag);

	int exchangedVal;
	// check whether the thread has received a value to exchange	
	// if not, block the thread waiting for another thread to exchange value
	// if so, do not block the thread, exchange value 
	
	// when the tagMap does not contain any thread to exchange value
	if (valMap.get(tag) == null || valMap.get(tag).size() == 0){
	
		
		// acquire the condition Lock
		lock.acquire();
		

		// put a new linkedlist containing the int value to be exchanged linked with tag
		LinkedList<Integer> nLink = new LinkedList<Integer>();
		nLink.add(new Integer(value));
		//valMap.put(tag, new LinkedList<Integer>(new Integer(value)));
		valMap.put(tag, nLink);

		// sleep the current thread waiting for next thread that calls exchange
		cond.sleep();
		
		// get the exchanged value and remove it from valMap
		exchangedVal = valMap.get(tag).removeFirst().intValue();
	}

	// when the tagMap contains at least one value to exchange
	else {

		// acquire the condition Lock
		lock.acquire();

		// get the exchange value and remove it from valMap
		exchangedVal = valMap.get(tag).removeFirst().intValue();
		
		// add value to the valMap
		valMap.get(tag).add(new Integer(value));
		//addFirst or addLast?
		
		// wake the first thread that is asleep
		cond.wake();

		//if removed A's val and B put it's val in list,
		//when A woke up, it is on ready state, could be after C. Then C would acquire the lock  should acquire the lock and even though A
	}
		
	// release the lock before return
	lock.release();
	
	// return the value that gets exchanged to the thread
	return exchangedVal;
    }



    public int exchange2 (int tag, int value) {
        boolean intStatus = Machine.interrupt().disable();
        Semaphore first; 
        Semaphore second;
        boolean odd;
        if (semaphore_map.get(new Integer(tag)) ==null){
            //init a semaphore for that tag
            first = new Semaphore(0);
            second = new Semaphore(0);
            first.r_val =value;
            first.complete = false;
            odd = true;
            ArrayList<Semaphore> sem_li = new ArrayList<Semaphore>();
            sem_li.add(first);
            sem_li.add(second);
            semaphore_map.put(new Integer(tag),sem_li);
            first.V();
            second.P();
        }
        else {
            first=semaphore_map.get(new Integer(tag)).get(0);
            second=semaphore_map.get(new Integer(tag)).get(1);
            
            if (first.complete){
                first.r_val =value;
                first.complete = false;
                odd = true;
                first.V();
                second.P();
            }
            else{
                second.r_val =value;
                first.complete = true;
                odd = false;
                second.V();
                first.P();
            }
            
            
        }
        
        int result;
        if (odd) result = second.r_val;
        else result = first.r_val; //exchange value
        Machine.interrupt().restore(intStatus);
        return result;
   
    }
    private HashMap<Integer,ArrayList<Semaphore>> semaphore_map;
    private HashMap<Integer,LinkedList<Integer>> val_map;
    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();
    
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
               // Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t1.setName("t1_tag0");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t2.setName("t2_tag0");

	KThread t3 = new KThread( new Runnable(){
		public void run(){
			int tag = 3;
			int send = 5;
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	});
	t3.setName("t3_tag3");
    
        KThread t4 = new KThread( new Runnable(){
		public void run(){
			int tag = 0;
			int send = 10;
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 16, "Was expecting " + 16 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	});
	t4.setName("t4_tag0");
	
	KThread t5 = new KThread( new Runnable(){
		public void run(){
			int tag = 0;
			int send = 16;
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 10, "Was expecting " + 10 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	});
	t5.setName("t5_tag0");

		
	t1.fork(); t3.fork();t2.fork();t4.fork();//t5.fork();
        // assumes join is implemented correctly
        t1.join(); t3.join();t2.join();t4.join();//t5.join();
        }
    
        // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()
    
        public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        rendezTest1();
        }
    
}
