package nachos.threads;

import java.util.HashMap;
import java.util.LinkedList;

import nachos.machine.*;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {
    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous () {
        this.tag_cvQuque_Map = new HashMap<Integer,LinkedList<Condition2>>(); 
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
        //Machine.interrupt().restore(intStatus);
        Condition2 con1 = new Condition2(new Lock());
        if(tag_cvQuque_Map.get(new Integer(tag))==null)
            tag_cvQuque_Map.put(new Integer(tag), new LinkedList<Condition2>());
        Condition2 other = tag_cvQuque_Map.get(new Integer(tag)).pollLast();

        if(other==null){
            con1.value=value;
            tag_cvQuque_Map.get(new Integer(tag)).add(con1);
            con1.sleep();
            //player1 = KThread.currentThread();
        }

        else{
            int result = other.value;
            other.value = value;
            other.wake();
            Machine.interrupt().restore(intStatus);
            return result;




        }

        int result = con1.value;
        Machine.interrupt().restore(intStatus);
        return result;

    }
    private HashMap<Integer,LinkedList<Condition2>> tag_cvQuque_Map;

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
