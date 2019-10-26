package nachos.threads;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayList;
import nachos.machine.*;
import java.util.Vector;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */

    private HashMap<Integer, Integer> flagMap;
    private HashMap<Integer, LinkedList<Vector<Integer>>>  valMap; // hashmap of tag&values to exchange
    private HashMap<Integer, Lock> lockMap; // hashmap of tag&lock
    private HashMap<Integer, Condition> condMap; // hashmap of tag&condition
    public Rendezvous () {
        valMap = new HashMap<Integer, LinkedList<Vector<Integer>>>();
    	lockMap = new HashMap<Integer, Lock>();
        condMap = new HashMap<Integer, Condition>();
    	flagMap = new HashMap<Integer, Integer>();
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

	Lock lock =lockMap.get(tag);
    	if (lock == null){
		lock = new Lock();
		lockMap.put(tag, lock);
	}
	
	lock.acquire();
	
	if (condMap.get(tag) == null){
		condMap.put(tag, new Condition(lock));
		flagMap.put(tag, new Integer(1));
	}
	// get the condition Lock
	Condition cond = condMap.get(tag);
	
	int exchangedVal;
	int count = flagMap.get(tag).intValue();
	
	flagMap.put(tag, new Integer(count+1));
	// check whether the thread has received a value to exchange	
	// if not, block the thread waiting for another thread to exchange value
	// if so, do not block the thread, exchange value 
	
	// when the tagMap does not contain any thread to exchange value
	if (valMap.get(tag) == null || valMap.get(tag).size() == 0){
	
		
		// acquire the condition Lock
		

		// put a new linkedlist containing the int value to be exchanged linked with tag
		LinkedList<Vector<Integer>> nLink = new LinkedList<Vector<Integer>>();
		Vector<Integer> v = new Vector<Integer>();
		v.add(new Integer(count));
		v.add(new Integer(value));
		nLink.add(v);
		//valMap.put(tag, new LinkedList<Integer>(new Integer(value)));
		valMap.put(tag, nLink);

		// sleep the current thread waiting for next thread that calls exchange
		cond.sleep();
		LinkedList<Vector<Integer>> li = valMap.get(tag);
		int r = -1;
		for(int i =0; i<li.size();i++){
			if(li.get(i).get(0).intValue() == count+1){
				r=i;
			}
		}
		if(r == -1) System.out.println("ERROR, WRONG INDEX");
		// get the exchanged value and remove it from valMap
		exchangedVal = li.remove(r).get(1).intValue();
	}

	// when the tagMap contains at least one value to exchange
	else {

		
			
		if(count %2 ==0){

		// get the exchange value and remove it from valMap
		exchangedVal = valMap.get(tag).removeLast().get(1).intValue();
		
		// add value to the valMap
		Vector<Integer> v = new Vector<Integer>();
		v.add(new Integer(count));
		v.add(new Integer(value));
		valMap.get(tag).addLast(v);
		//addFirst or addLast?
		
		// wake the first thread that is asleep
		cond.wake();

		}

		else{
		Vector<Integer> v = new Vector<Integer>();
		v.add(new Integer(count));
		v.add(new Integer(value));
		valMap.get(tag).addLast(v);
			
		cond.sleep();
		LinkedList<Vector<Integer>> li = valMap.get(tag);
		int r = -1;
		while(r==-1){
			for(int i =0; i<li.size();i++){
			if(li.get(i).get(0).intValue()==count+1){
				r=i;
			}
			}
			if(r==-1) cond.sleep();
		}
		 
		// get the exchanged value and remove it from valMap
		exchangedVal = li.remove(r).get(1).intValue();
		cond.wake();
		}
		//if removed A's val and B put it's val in list,
		//when A woke up, it is on ready state, could be after C. 
		//B would execute to the end and return A's value. val_list now has B's value. Then C would acquire the lock and enter the queue. Needs to do something so that C won't be able to run earlier than A / at least not get A's value.
	}
		
	// release the lock before return
	lock.release();
	
	// return the value that gets exchanged to the thread
	return exchangedVal;
    }



    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();
    
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;
    	        int tag2 = 3;
		int send2 = 33;
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
		
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging (second time)" + send2);
                int recv2 = r.exchange (tag2, send2);
		// Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                
                System.out.println ("Thread " + KThread.currentThread().getName() + " first received " + recv);
		System.out.println ("Thread " + KThread.currentThread().getName() + " second received " + recv2);
	    }
	});
        t1.setName("t1_tag_first_0_second_3");
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


	KThread t6 = new KThread( new Runnable(){
		public void run(){
			int tag = 0;
			int send = 44;
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 10, "Was expecting " + 10 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	});
	t6.setName("t6_tag0");

	KThread t7 = new KThread( new Runnable(){
		public void run(){
			int tag = 0;
			int send = 77;
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 10, "Was expecting " + 10 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	});
	t7.setName("t7_tag0");
	
	KThread t8 = new KThread( new Runnable(){
		public void run(){
			int tag = 4;
			int send = 141;
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 10, "Was expecting " + 10 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
		}
	});
	t8.setName("t8_tag4");



		
	t1.fork(); t3.fork();t2.fork();t4.fork();t5.fork();t6.fork();t8.fork();t7.fork();
        // assumes join is implemented correctly
        t1.join(); t3.join();t2.join();t4.join();t5.join();t6.join();t8.join();t7.join();
        
    	}
    
        // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()
    
        public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        rendezTest1();
        }
    
}
