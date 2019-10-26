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
    public Rendezvous () {
        this.semaphore_map = new HashMap<Integer,ArrayList<Semaphore>>(); 
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

    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();
    
        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t1.setName("t1");
        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;
    
                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
            });
        t2.setName("t2");
    
        t1.fork(); t2.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join();
        }
    
        // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()
    
        public static void selfTest() {
        // place calls to your Rendezvous tests that you implement here
        rendezTest1();
        }
    
}
